package gg.grounds

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.agones.AgonesHelper
import gg.grounds.agones.AgonesLogger
import gg.grounds.agones.AgonesRestClient
import gg.grounds.listener.PlayerListener
import java.util.concurrent.TimeUnit
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
    private lateinit var fallbackTask: ScheduledTask

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info("Velocity Agones plugin initialized")

        val agonesHelper = createAgonesHelper()
        setGameServerState(agonesHelper)

        registerListeners(agonesHelper)
        scheduleFallback(agonesHelper)
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        if (this::fallbackTask.isInitialized) {
            fallbackTask.cancel()
        }
        coroutineScope.cancel()
    }

    private fun setGameServerState(agonesHelper: AgonesHelper) {
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
        return AgonesHelper(AgonesRestClient.fromEnvironment(), agonesLogger, coroutineScope)
    }

    private fun registerListeners(agonesHelper: AgonesHelper) {
        proxyServer.eventManager.register(this, PlayerListener(proxyServer, agonesHelper))
    }

    private fun scheduleFallback(agonesHelper: AgonesHelper) {
        fallbackTask =
            proxyServer.scheduler
                .buildTask(this, Runnable { setGameServerState(agonesHelper) })
                .delay(1L, TimeUnit.SECONDS)
                .repeat(10L, TimeUnit.SECONDS)
                .schedule()
    }
}
