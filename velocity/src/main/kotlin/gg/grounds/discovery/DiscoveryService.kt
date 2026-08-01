package gg.grounds.discovery

import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.api.ProxyServiceRegistry
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.CoreV1Api
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
    private lateinit var coreApi: CoreV1Api
    private lateinit var pollTask: ScheduledTask
    private val lobbyServers: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val serverRoles: MutableMap<String, String> = ConcurrentHashMap()
    @Volatile private var countsSnapshot: Pair<Long, Map<String, Int>?>? = null

    fun start() {
        customObjectsApi = createCustomObjectsApi() ?: return
        // Same client, already configured as the default above.
        coreApi = CoreV1Api()

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
            DiscoveryPlayerListener(
                proxyServer,
                lobbyServers,
                config.lobbySoftCap,
                this::networkCountsCached,
            ),
        )
    }

    /**
     * Network-wide players per server, briefly cached: the lookup behind it is a gRPC call and the
     * caller runs on every join. Null (plugin-proxy absent, presence service unreachable) is cached
     * too — a join storm must not hammer a service that is already answering badly.
     */
    private fun networkCountsCached(): Map<String, Int>? {
        val now = System.nanoTime()
        countsSnapshot?.let { (takenAt, value) ->
            if (now - takenAt < COUNTS_TTL_NANOS) return value
        }
        val fresh =
            ProxyServiceRegistry.get(ProxyService::class.java)?.getNetworkPlayerCounts()?.byServer
        countsSnapshot = now to fresh
        return fresh
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

    /** The GameServer's pod carries the same name, so this is a direct lookup. */
    private fun podIp(serverName: String): String? =
        try {
            coreApi.readNamespacedPod(serverName, config.namespace).execute().status?.podIP
        } catch (error: Throwable) {
            logger.warn("Could not read the pod for GameServer {}: {}", serverName, error.message)
            null
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

            // Agones does not always publish the pod's address in the GameServer's
            // status. The bundle's lobby fleets carry Hostname, InternalIP AND
            // PodIP; the fleets forge renders for a pushed gamemode carry only the
            // first two. A proxy that insists on PodIP therefore throws away every
            // pushed gamemode — the server runs, is Ready, and no player can ever
            // reach it, which looks exactly like a broken game.
            //
            // The pod is the source of that address anyway, and Agones names it
            // after the GameServer, so fall back to reading it directly. Never the
            // node's InternalIP: that would route players to a machine instead of
            // to their server.
            val address =
                gameServer.status?.addresses?.firstOrNull { it.type == config.addressType }?.address
                    ?: podIp(serverName)
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
        private val COUNTS_TTL_NANOS = TimeUnit.SECONDS.toNanos(2)
    }
}
