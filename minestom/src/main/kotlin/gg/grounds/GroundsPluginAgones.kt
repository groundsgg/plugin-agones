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
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GroundsPluginAgones {
    private val logger: Logger = LoggerFactory.getLogger(GroundsPluginAgones::class.java)
    private var coroutineScope: CoroutineScope? = null
    private var fallbackTask: Task? = null
    private var playerEventNode: EventNode<Event>? = null

    fun enable() {
        disable()

        logger.info("Minestom Agones plugin initialized")

        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        this.coroutineScope = coroutineScope

        val agonesHelper = createAgonesHelper(coroutineScope)
        setGameServerState(agonesHelper)

        playerEventNode = registerListeners(agonesHelper)
        scheduleFallback(agonesHelper)
    }

    fun disable() {
        fallbackTask?.cancel()
        fallbackTask = null

        playerEventNode?.let(MinecraftServer.getGlobalEventHandler()::removeChild)
        playerEventNode = null

        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun setGameServerState(agonesHelper: AgonesHelper) {
        if (MinecraftServer.getConnectionManager().onlinePlayers.isNotEmpty()) {
            agonesHelper.allocate()
        } else {
            agonesHelper.ready()
        }
    }

    private fun createAgonesHelper(coroutineScope: CoroutineScope): AgonesHelper {
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

    private fun registerListeners(agonesHelper: AgonesHelper): EventNode<Event> {
        val eventNode = EventNode.all("grounds-plugin-agones")
        PlayerListener(agonesHelper).register(eventNode)
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        return eventNode
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
