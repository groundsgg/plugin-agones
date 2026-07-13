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
            // Ready ONCE, and then never again.
            //
            // Agones only moves a GameServer out of Scheduled when it calls
            // ready(). A server that never does is never in the pool, so the
            // matchmaker's allocation matches nothing and no match can ever be
            // placed on it — the fleet sits there looking healthy and is, to an
            // allocator, invisible. Keeping our hands off the state entirely was
            // too broad: what must not happen is re-readying a server that is
            // ALREADY allocated (its players are seconds away, and handing it
            // back would let a second match land on it).
            //
            // So: enter the pool, then stop. No readiness loop, no player
            // listeners, no ready() on empty. Ending the match is the gamemode's
            // job (SDK.Shutdown). See GameServerOwnership.
            agonesHelper.ready()
            logger.info(
                "Started Agones plugin successfully (platform=paper, " +
                    "ownership=matchmaker-managed; readied once, then hands off — " +
                    "readiness loop and player listeners disabled)"
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
