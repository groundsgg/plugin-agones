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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.slf4j.Logger

class DiscoveryService(
    private val plugin: Any,
    private val proxyServer: ProxyServer,
    private val logger: Logger,
    private val config: DiscoveryConfig = DiscoveryConfig.fromEnv(),
) {
    private val gson = Gson()
    private lateinit var customObjectsApi: CustomObjectsApi
    private lateinit var pollTask: ScheduledTask
    private val lobbyServers: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val serverRoles: MutableMap<String, String> = ConcurrentHashMap()

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

    fun getServerRole(serverName: String): String? = serverRoles[serverName]

    private fun createCustomObjectsApi(): CustomObjectsApi? {
        return try {
            val client = Config.defaultClient()
            Configuration.setDefaultApiClient(client)
            CustomObjectsApi(client)
        } catch (error: Throwable) {
            logger.warn(
                "Failed to initialize Agones discovery client (namespace={}, labelSelector={})",
                config.namespace,
                config.labelSelector,
                error,
            )
            null
        }
    }

    private fun unregisterPreconfiguredServers() {
        val configuredServers = proxyServer.allServers.toList()
        for (server in configuredServers) {
            proxyServer.unregisterServer(server.serverInfo)
            logger.info(
                "Removed pre-configured server successfully (serverName={})",
                server.serverInfo.name,
            )
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
                .repeat(config.pollInterval.toSeconds(), TimeUnit.SECONDS)
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
            val request =
                customObjectsApi.listNamespacedCustomObject(
                    GROUP,
                    VERSION,
                    config.namespace,
                    PLURAL,
                )
            if (config.labelSelector.isNotEmpty()) {
                request.labelSelector(config.labelSelector)
            }
            val raw = request.execute()

            val list = gson.fromJson(gson.toJson(raw), GameServerList::class.java)

            return list.items.filter { gameServer ->
                val state = gameServer.status?.state
                state != null && state in config.runningStates
            }
        } catch (error: Throwable) {
            logger.warn(
                "Failed to fetch running Agones GameServers (namespace={}, labelSelector={})",
                config.namespace,
                config.labelSelector,
                error,
            )
            return emptyList()
        }
    }

    private fun registerRunningServers(
        runningGameServers: List<GameServer>,
        currentServers: Map<String, RegisteredServer>,
    ) {
        for (gameServer in runningGameServers) {
            val metadata = gameServer.metadata
            val serverName = metadata?.name
            if (serverName == null) {
                logger.error(
                    "Failed to register Agones GameServer (namespace={}, reason=missing_server_name, labels={}, state={})",
                    config.namespace,
                    metadata?.labels,
                    gameServer.status?.state,
                )
                continue
            }

            val serverType = resolveServerType(metadata.labels) ?: continue
            serverRoles[serverName] = serverType

            if (serverType == config.lobbyValue) {
                lobbyServers.add(serverName)
            } else {
                lobbyServers.remove(serverName)
            }

            if (serverName in currentServers) continue

            val address =
                gameServer.status?.addresses?.firstOrNull { it.type == config.addressType }?.address
            if (address == null) {
                logger.error(
                    "Failed to register Agones GameServer (serverName={}, reason=missing_address, addressType={})",
                    serverName,
                    config.addressType,
                )
                continue
            }

            val serverInfo = ServerInfo(serverName, InetSocketAddress(address, config.port))
            proxyServer.registerServer(serverInfo)
            logger.info(
                "Registered proxy server successfully (serverName={}, serverType={})",
                serverName,
                serverType,
            )
        }
    }

    /**
     * Returns the server's role label, or [DiscoveryConfig.lobbyValue] when role-based filtering is
     * disabled (`GROUNDS_AGONES_LOBBY_LABEL=""`). When filtering is enabled and the label is
     * missing, the GameServer is skipped (returns null).
     */
    private fun resolveServerType(labels: Map<String, String>): String? =
        when {
            config.lobbyLabel.isEmpty() -> config.lobbyValue
            else -> labels[config.lobbyLabel]
        }

    private fun unregisterServersThatAreNoLongerRunning(
        runningGameServers: List<GameServer>,
        currentServers: Map<String, RegisteredServer>,
    ) {
        val runningServerNames = runningGameServers.mapNotNull { it.metadata?.name }.toSet()

        for (server in currentServers.values) {
            if (server.serverInfo.name !in runningServerNames) {
                proxyServer.unregisterServer(server.serverInfo)
                lobbyServers.remove(server.serverInfo.name)
                serverRoles.remove(server.serverInfo.name)
                logger.info(
                    "Unregistered proxy server successfully (serverName={})",
                    server.serverInfo.name,
                )
            }
        }
    }

    companion object {
        private const val GROUP = "agones.dev"
        private const val VERSION = "v1"
        private const val PLURAL = "gameservers"
    }
}
