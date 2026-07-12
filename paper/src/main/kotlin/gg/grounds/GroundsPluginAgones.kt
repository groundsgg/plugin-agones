package gg.grounds

import gg.grounds.agones.AgonesHelper
import gg.grounds.agones.AgonesLogger
import gg.grounds.agones.AgonesRestClient
import gg.grounds.agones.GameServerOwnership
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
        // The Agones SDK sidecar is only injected by the cluster's
        // mutating webhook into pods that the controller has wrapped
        // in a GameServer / Fleet. A plain plugin-paper Deployment
        // (the default for non-gamemode workloads on Grounds) has no
        // sidecar — the plugin's calls to localhost:9358 then loop on
        // SocketException + log SEVERE every 10s, which is loud noise
        // for an inert feature.
        //
        // The Agones webhook always sets `AGONES_SDK_HTTP_PORT` (and
        // its grpc twin) on the GameServer container's env. Use that
        // as the activation gate: present → real init; absent → log
        // once and stay quiet.
        val sidecarPort = System.getenv("AGONES_SDK_HTTP_PORT")
        if (sidecarPort.isNullOrBlank()) {
            logger.info(
                "Skipped Agones plugin (reason=no_sidecar, " +
                    "AGONES_SDK_HTTP_PORT_unset); pod is not a GameServer"
            )
            return
        }

        val agonesHelper = createAgonesHelper()

        val ownership = GameServerOwnership.fromEnvironment()
        if (ownership.isMatchmakerManaged) {
            // A matchmaker owns this server's Agones state. We must not touch
            // it: the readiness loop would call ready() on an allocated server
            // that is merely still empty (its players are seconds away), and
            // the player listeners would do the same on the last disconnect.
            // Either one hands the server back to the fleet and lets a second
            // match land on it. Ending the match is the gamemode's job
            // (SDK.Shutdown). See GameServerOwnership.
            logger.info(
                "Started Agones plugin successfully (platform=paper, " +
                    "ownership=matchmaker-managed; readiness loop and player listeners disabled)"
            )
            return
        }

        setGameServerState(agonesHelper)

        registerListeners(agonesHelper)
        scheduleFallback(agonesHelper)

        logger.info("Started Agones plugin successfully (platform=paper, ownership=self-managed)")
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
        return AgonesHelper(AgonesRestClient.forSidecar(), agonesLogger, coroutineScope)
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
