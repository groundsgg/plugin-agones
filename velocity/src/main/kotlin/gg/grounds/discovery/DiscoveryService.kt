package gg.grounds.discovery

import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.api.scheduler.ScheduledTask
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.CustomObjectsApi
import io.kubernetes.client.util.Config
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import org.slf4j.Logger

class DiscoveryService(
    private val plugin: Any,
    private val proxyServer: ProxyServer,
    private val logger: Logger,
) {
    private val gson = Gson()
    private lateinit var customObjectsApi: CustomObjectsApi
    private lateinit var pollTask: ScheduledTask
    val lobbyServers: MutableSet<String> = mutableSetOf()

    fun start() {
        customObjectsApi = createCustomObjectsApi() ?: return

        unregisterPreconfiguredServers()
        registerListeners()
        schedulePolling()
    }

    fun stop() {
        if (this::pollTask.isInitialized) {
            pollTask.cancel()
        }
    }

    private fun createCustomObjectsApi(): CustomObjectsApi? {
        return try {
            val client = Config.defaultClient()
            Configuration.setDefaultApiClient(client)
            CustomObjectsApi(client)
        } catch (error: Throwable) {
            logger.warn("Agones discovery init failed: ${error.message}", error)
            null
        }
    }

    private fun unregisterPreconfiguredServers() {
        val configuredServers = proxyServer.allServers.toList()
        for (server in configuredServers) {
            logger.info("Removing pre-configured server: {}", server.serverInfo.name)
            proxyServer.unregisterServer(server.serverInfo)
        }
    }

    private fun registerListeners() {
        proxyServer.eventManager.register(
            plugin,
            DiscoveryPlayerListener(proxyServer, lobbyServers),
        )
    }

    private fun schedulePolling() {
        pollTask =
            proxyServer.scheduler
                .buildTask(plugin, this::updateRegisteredGameServers)
                .repeat(2, TimeUnit.SECONDS)
                .schedule()
    }

    private fun updateRegisteredGameServers() {
        val runningGameServers = fetchRunningGameServers()

        val currentServers = proxyServer.allServers.associateBy { it.serverInfo.name }

        registerRunningServers(runningGameServers, currentServers)
        unregisterServersThatAreNoLongerRunning(runningGameServers, currentServers)
    }

    private fun fetchRunningGameServers(): List<GameServer> {
        if (!this::customObjectsApi.isInitialized) return emptyList()
        try {
            val raw =
                customObjectsApi
                    .listNamespacedCustomObject(GROUP, VERSION, NAMESPACE, PLURAL)
                    .labelSelector(LABEL_SELECTOR)
                    .execute()

            val list = gson.fromJson(gson.toJson(raw), GameServerList::class.java)

            return list.items.filter { gameServer ->
                val state = gameServer.status?.state
                state != null && state in RUNNING_STATES
            }
        } catch (error: Throwable) {
            logger.warn("Agones poll failed: ${error.message}", error)
            return emptyList()
        }
    }

    private fun registerRunningServers(
        runningGameServers: List<GameServer>,
        currentServers: Map<String, RegisteredServer>,
    ) {
        for (gameServer in runningGameServers) {
            val serverName = gameServer.metadata?.name
            if (serverName == null) {
                logger.error("Game server $gameServer is missing a name in metadata")
                continue
            }

            if (serverName in currentServers) continue

            val address = gameServer.status?.addresses?.firstOrNull { it.type == "PodIP" }?.address
            if (address == null) {
                logger.error("Game server $serverName is missing an address in status")
                continue
            }

            val serverType = gameServer.metadata.labels[SERVER_TYPE_LABEL]
            val serverInfo = ServerInfo(serverName, InetSocketAddress(address, 25565))
            logger.info("Registering server: {} (type={})", serverName, serverType)
            proxyServer.registerServer(serverInfo)

            if (serverType == "lobby") {
                lobbyServers.add(serverName)
            }
        }
    }

    private fun unregisterServersThatAreNoLongerRunning(
        runningGameServers: List<GameServer>,
        currentServers: Map<String, RegisteredServer>,
    ) {
        val runningServerNames = runningGameServers.mapNotNull { it.metadata?.name }.toSet()

        for (server in currentServers.values) {
            if (server.serverInfo.name !in runningServerNames) {
                logger.info("Unregistering server: {}", server.serverInfo.name)
                proxyServer.unregisterServer(server.serverInfo)
                lobbyServers.remove(server.serverInfo.name)
            }
        }
    }

    companion object {
        private const val GROUP = "agones.dev"
        private const val VERSION = "v1"
        private const val PLURAL = "gameservers"
        private const val NAMESPACE = "games"
        private const val SERVER_TYPE_LABEL = "grounds/server-type"
        private const val LABEL_SELECTOR = "$SERVER_TYPE_LABEL in (lobby,game,match)"
        private val RUNNING_STATES = setOf("Ready", "Allocated", "Reserved")
    }
}
