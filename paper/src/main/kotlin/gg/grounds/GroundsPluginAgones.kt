package gg.grounds

import gg.grounds.agones.AgonesHelper
import gg.grounds.agones.AgonesLogger
import gg.grounds.agones.AgonesRestClient
import gg.grounds.listener.PlayerListener
import java.util.logging.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class GroundsPluginAgones : JavaPlugin() {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fallbackTask: BukkitTask

    override fun onEnable() {
        logger.info("Paper Agones plugin initialized")

        val agonesHelper = createAgonesHelper()
        setGameServerState(agonesHelper)

        registerListeners(agonesHelper)
        scheduleFallback(agonesHelper)
    }

    override fun onDisable() {
        if (this::fallbackTask.isInitialized) {
            fallbackTask.cancel()
        }
        coroutineScope.cancel()
    }

    private fun setGameServerState(agonesHelper: AgonesHelper) {
        if (server.onlinePlayers.isNotEmpty()) {
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
                    logger.log(Level.SEVERE, message, error)
                }
            }
        return AgonesHelper(AgonesRestClient.fromEnvironment(), agonesLogger, coroutineScope)
    }

    private fun registerListeners(agonesHelper: AgonesHelper) {
        server.pluginManager.registerEvents(PlayerListener(this, server, agonesHelper), this)
    }

    private fun scheduleFallback(agonesHelper: AgonesHelper) {
        fallbackTask =
            server.scheduler.runTaskTimer(
                this,
                Runnable { setGameServerState(agonesHelper) },
                20L,
                20L * 10,
            )
    }
}
