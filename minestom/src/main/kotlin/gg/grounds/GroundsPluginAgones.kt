package gg.grounds

import gg.grounds.agones.AgonesHelper
import gg.grounds.agones.AgonesLogger
import gg.grounds.agones.AgonesRestClient
import gg.grounds.listener.PlayerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GroundsPluginAgones {
    private val logger: Logger = LoggerFactory.getLogger(GroundsPluginAgones::class.java)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fallbackTask: Task? = null

    fun enable() {
        logger.info("Minestom Agones plugin initialized")

        val agonesHelper = createAgonesHelper()
        setGameServerState(agonesHelper)

        registerListeners(agonesHelper)
        scheduleFallback(agonesHelper)
    }

    fun disable() {
        fallbackTask?.cancel()
        coroutineScope.cancel()
    }

    private fun setGameServerState(agonesHelper: AgonesHelper) {
        if (MinecraftServer.getConnectionManager().onlinePlayers.isNotEmpty()) {
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

    private fun registerListeners(agonesHelper: AgonesHelper) {
        PlayerListener(agonesHelper).register(MinecraftServer.getGlobalEventHandler())
    }

    private fun scheduleFallback(agonesHelper: AgonesHelper) {
        fallbackTask =
            MinecraftServer.getSchedulerManager()
                .scheduleTask(
                    { setGameServerState(agonesHelper) },
                    TaskSchedule.seconds(1),
                    TaskSchedule.seconds(10),
                )
    }
}
