package gg.grounds

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.command.AgonesCommand
import gg.grounds.discovery.DiscoveryConfig
import gg.grounds.discovery.DiscoveryService
import gg.grounds.gameserver.GameServerStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.Logger

@Plugin(
    id = "plugin-agones",
    name = "Grounds Agones Plugin",
    version = BuildInfo.VERSION,
    description = "Grounds agones plugin for Velocity",
    authors = ["Grounds Development Team and contributors"],
    url = "https://github.com/groundsgg/plugin-agones",
)
class GroundsPluginAgones
@Inject
constructor(private val proxyServer: ProxyServer, private val logger: Logger) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var stateManager: GameServerStateManager
    private lateinit var discoveryService: DiscoveryService

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        val discoveryConfig = DiscoveryConfig.fromEnv()
        logger.info(
            "Loaded Agones discovery config (namespace={}, labelSelector={}, lobbyLabel={}, lobbyValue={}, runningStates={}, pollInterval={}s, addressType={}, port={})",
            discoveryConfig.namespace,
            discoveryConfig.labelSelector.ifEmpty { "<none>" },
            discoveryConfig.lobbyLabel.ifEmpty { "<none>" },
            discoveryConfig.lobbyValue,
            discoveryConfig.runningStates,
            discoveryConfig.pollInterval.toSeconds(),
            discoveryConfig.addressType,
            discoveryConfig.port,
        )

        stateManager =
            GameServerStateManager(this, proxyServer, logger, coroutineScope).also { it.start() }
        discoveryService =
            DiscoveryService(this, proxyServer, logger, discoveryConfig).also { it.start() }

        proxyServer.commandManager.register(
            proxyServer.commandManager.metaBuilder("agones").build(),
            AgonesCommand(proxyServer, { serverName -> discoveryService.getServerRole(serverName) }),
        )

        logger.info("Initialized Agones plugin (platform=velocity)")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        if (this::discoveryService.isInitialized) {
            discoveryService.stop()
        }
        if (this::stateManager.isInitialized) {
            stateManager.stop()
        }
        coroutineScope.cancel()
    }
}
