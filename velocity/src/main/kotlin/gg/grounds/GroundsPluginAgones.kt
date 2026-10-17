package gg.grounds

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.command.AgonesCommand
import gg.grounds.discovery.DiscoveryConfig
import gg.grounds.discovery.DiscoveryServerDisplayQuery
import gg.grounds.discovery.DiscoveryService
import gg.grounds.discovery.LobbyKickListener
import gg.grounds.discovery.OutdatedLobbyMover
import gg.grounds.drain.DrainConfig
import gg.grounds.drain.DrainHttpServer
import gg.grounds.drain.DrainListener
import gg.grounds.drain.DrainManager
import gg.grounds.drain.DrainTransferCookie
import gg.grounds.gameserver.GameServerStateManager
import gg.grounds.i18n.Translations
import gg.grounds.presentation.VelocitySystemMessages
import gg.grounds.proxy.api.ProxyServiceRegistry
import gg.grounds.proxy.api.ServerDisplayQuery
import java.util.UUID
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
    private lateinit var drainHttpServer: DrainHttpServer
    private lateinit var outdatedLobbyMover: OutdatedLobbyMover

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        val messages =
            Translations.forBundleWithSystemRenderer(
                "gg.grounds.agones.messages",
                javaClass.classLoader,
                systemRenderer = VelocitySystemMessages.renderer,
            )
        val discoveryConfig = DiscoveryConfig.fromEnv()
        logger.info(
            "Loaded Agones discovery config (namespace={}, labelSelector={}, lobbyLabel={}, lobbyValue={}, runningStates={}, pollInterval={}s, addressType={}, port={}, lobbyPlayerCap={})",
            discoveryConfig.namespace,
            discoveryConfig.labelSelector.ifEmpty { "<none>" },
            discoveryConfig.lobbyLabel.ifEmpty { "<none>" },
            discoveryConfig.lobbyValue,
            discoveryConfig.runningStates,
            discoveryConfig.pollInterval.toSeconds(),
            discoveryConfig.addressType,
            discoveryConfig.port,
            discoveryConfig.lobbyPlayerCap,
        )

        stateManager =
            GameServerStateManager(this, proxyServer, logger, coroutineScope).also { it.start() }
        val drainCookie = DrainTransferCookie(System.getenv(DrainTransferCookie.SECRET_ENV))
        lateinit var drainManager: DrainManager
        discoveryService =
            DiscoveryService(
                    this,
                    proxyServer,
                    logger,
                    discoveryConfig,
                    drainTransferCookie = drainCookie,
                    sourceCookiePending = { playerId -> drainManager.isCookiePending(playerId) },
                    messages = messages,
                )
                .also { it.start() }

        withProxyApi("register") {
            ProxyServiceRegistry.register(
                ServerDisplayQuery::class.java,
                DiscoveryServerDisplayQuery { name -> discoveryService.getServerRole(name) },
            )
        }

        proxyServer.commandManager.register(
            proxyServer.commandManager.metaBuilder("agones").build(),
            AgonesCommand(
                proxyServer,
                { serverName -> discoveryService.getServerRole(serverName) },
                messages = messages,
            ),
        )

        val drainConfig = DrainConfig.fromEnv()
        drainManager =
            DrainManager(
                this,
                proxyServer,
                logger,
                drainConfig,
                { serverName -> discoveryService.getServerRole(serverName) },
                discoveryConfig.lobbyValue,
                drainCookie,
                messages = messages,
            )
        proxyServer.eventManager.register(this, DrainListener(drainManager))
        proxyServer.eventManager.register(
            this,
            LobbyKickListener(
                { serverName ->
                    discoveryService.getServerRole(serverName) == discoveryConfig.lobbyValue
                },
                { exclude, playerId -> discoveryService.pickLobby(exclude, playerId) },
            ),
        )
        outdatedLobbyMover =
            OutdatedLobbyMover(
                    this,
                    proxyServer,
                    logger,
                    discoveryService::lobbyCandidates,
                    { drainManager.isDraining },
                    lobbyPlayerCap = discoveryConfig.lobbyPlayerCap,
                    messages = messages,
                )
                .also { it.start() }
        drainHttpServer =
            DrainHttpServer(drainManager, drainConfig.httpPort, logger).also { it.start() }

        logger.info("Initialized Agones plugin (platform=velocity)")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        withProxyApi("unregister") {
            ProxyServiceRegistry.unregister(ServerDisplayQuery::class.java)
        }
        if (this::drainHttpServer.isInitialized) {
            drainHttpServer.stop()
        }
        if (this::outdatedLobbyMover.isInitialized) {
            outdatedLobbyMover.stop()
        }
        if (this::discoveryService.isInitialized) {
            discoveryService.stop()
        }
        if (this::stateManager.isInitialized) {
            stateManager.stop()
        }
        coroutineScope.cancel()
    }

    /**
     * The lobby [playerId] should go to, never [exclude], with their place reserved; null when
     * there is none or discovery has not started.
     *
     * For plugin-match, which sends players back to a lobby after a match. It calls this by
     * reflection — JDK types only — so the two plugins share one placement rule and one set of
     * reservations without a compile-time dependency on each other.
     */
    fun pickLobby(playerId: UUID, exclude: String?): String? =
        if (this::discoveryService.isInitialized) {
            discoveryService.pickLobby(exclude, playerId)?.serverInfo?.name
        } else {
            null
        }

    /**
     * Publishes the server display query, if there is a proxy API to publish it into.
     *
     * `plugin-proxy-api` is compileOnly, so on a proxy without plugin-proxy the registry class
     * simply is not there. That must cost this plugin its server display publishing and nothing
     * else -- discovery and drain are the job, and letting a NoClassDefFoundError out of here would
     * take the command registration below it down as well.
     */
    private fun withProxyApi(action: String, block: () -> Unit) {
        try {
            block()
        } catch (error: NoClassDefFoundError) {
            logger.info(
                "Server display not published ({}): plugin-proxy is not loaded (missing={})",
                action,
                error.message,
            )
        }
    }
}
