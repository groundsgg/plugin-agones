package gg.grounds.gameserver

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.agones.AgonesHelper
import gg.grounds.agones.AgonesLogger
import gg.grounds.agones.AgonesRestClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import org.slf4j.Logger

class GameServerStateManager(
    private val plugin: Any,
    private val proxyServer: ProxyServer,
    private val logger: Logger,
    private val coroutineScope: CoroutineScope,
    /**
     * Indirection for tests. The real value reads the Agones SDK env vars Agones injects into
     * containers it manages.
     */
    private val sidecarDetector: () -> Boolean = ::detectAgonesSidecar,
) {
    private lateinit var agonesHelper: AgonesHelper
    private var fallbackTask: ScheduledTask? = null

    fun start() {
        // Only Agones-managed pods (i.e. proxies deployed as part of a
        // Fleet) have the SDK sidecar listening on localhost:9358. A
        // proxy deployed as a plain Kubernetes Deployment — the most
        // common shape for small dev clusters — has no sidecar, and
        // calling `allocate()` / `ready()` against `localhost:9358`
        // throws ConnectException on a 10s loop. Skip the self-state
        // path when no sidecar is detected; DiscoveryService and the
        // backend-server registration keep working regardless.
        if (!sidecarDetector()) {
            logger.info(
                "Agones SDK sidecar not detected (AGONES_SDK_HTTP_PORT unset). " +
                    "Skipping proxy self-state management; discovery + routing keep running."
            )
            return
        }
        agonesHelper = createAgonesHelper()
        setGameServerState()
        registerListeners()
        scheduleFallback()
    }

    fun stop() {
        fallbackTask?.cancel()
    }

    private fun setGameServerState() {
        if (proxyServer.allPlayers.isNotEmpty()) {
            agonesHelper.allocate()
        } else {
            agonesHelper.ready()
        }
    }

    private fun createAgonesHelper(): AgonesHelper {
        val agonesLogger =
            object : AgonesLogger {
                override fun info(message: String) {
                    logger.info(message)
                }

                override fun error(message: String, error: Throwable) {
                    logger.error(message, error)
                }
            }
        return AgonesHelper(AgonesRestClient.forSidecar(), agonesLogger, coroutineScope)
    }

    private fun registerListeners() {
        proxyServer.eventManager.register(
            plugin,
            GameServerPlayerListener(proxyServer, agonesHelper),
        )
    }

    private fun scheduleFallback() {
        fallbackTask =
            proxyServer.scheduler
                .buildTask(plugin, Runnable { setGameServerState() })
                .delay(1L, TimeUnit.SECONDS)
                .repeat(10L, TimeUnit.SECONDS)
                .schedule()
    }
}

/**
 * Heuristic: Agones injects `AGONES_SDK_HTTP_PORT` (default 9358) and `AGONES_SDK_GRPC_PORT` into
 * the GameServer container's environment. Presence of either is a sufficient signal that the SDK
 * sidecar is running alongside this pod and the REST endpoint is reachable on localhost. Absence =>
 * plain Deployment, no sidecar.
 */
private fun detectAgonesSidecar(): Boolean =
    System.getenv("AGONES_SDK_HTTP_PORT") != null || System.getenv("AGONES_SDK_GRPC_PORT") != null
