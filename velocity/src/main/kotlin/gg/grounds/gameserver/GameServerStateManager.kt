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
) {
    private lateinit var agonesHelper: AgonesHelper
    private var fallbackTask: ScheduledTask? = null

    fun start() {
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
