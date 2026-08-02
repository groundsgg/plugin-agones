package gg.grounds.drain

import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import net.kyori.adventure.text.Component
import org.slf4j.Logger

/**
 * Moves players off this proxy before it shuts down, instead of letting Velocity kick them.
 *
 * Started by the pod's preStop hook via [DrainHttpServer]. From that moment on:
 * - new logins are denied (they bounce back through the public name to a live proxy),
 * - players sitting in a lobby are sent a Minecraft transfer packet right away,
 * - players inside a round stay untouched — Agones keeps their GameServer alive, and the moment the
 *   round sends them back towards a lobby the transfer happens *instead of* that connect,
 * - at the deadline, whoever is *not* inside a round is transferred; players inside a round are
 *   never transferred — the round either ends within the grace period (and the lobby connect
 *   becomes the transfer), or the pod's termination ends the session. A transfer would end the
 *   round just as surely, only earlier.
 *
 * "Inside a round" is decided by the server's `grounds/server-type` role: anything that is not the
 * lobby role defers the transfer. A server that discovery has no role for cannot be a protected
 * round.
 */
class DrainManager(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val config: DrainConfig,
    private val serverRole: (String) -> String?,
    private val lobbyValue: String,
) {
    @Volatile
    var isDraining: Boolean = false
        private set

    fun playersRemaining(): Int = proxy.allPlayers.size

    /** Starts the drain. Idempotent — a second call reports `false` and changes nothing. */
    @Synchronized
    fun start(deadlineSeconds: Long): Boolean {
        if (isDraining) return false
        isDraining = true
        logger.info(
            "Drain started (players={}, deadline={}s, transferTarget={})",
            proxy.allPlayers.size,
            deadlineSeconds,
            config.transferHost?.let { "$it:${config.transferPort}" } ?: "<none — wait only>",
        )

        proxy.allPlayers.forEach { player ->
            if (!shouldDefer(roleOf(player), lobbyValue)) {
                transferOut(player, force = false)
            }
        }

        proxy.scheduler
            .buildTask(plugin, Runnable { onDeadline() })
            .delay(deadlineSeconds, TimeUnit.SECONDS)
            .schedule()
        return true
    }

    /**
     * A player is heading to [targetServer] — when draining and the target is not a round, send
     * them to another proxy instead. Returns true when the connect should be cancelled.
     */
    fun interceptConnect(player: Player, targetServer: String): Boolean {
        if (!isDraining) return false
        if (shouldDefer(serverRole(targetServer), lobbyValue)) return false
        return transferOut(player, force = false)
    }

    private fun onDeadline() {
        val remaining = proxy.allPlayers
        if (remaining.isEmpty()) return
        val (inRound, drainable) = remaining.partition { shouldDefer(roleOf(it), lobbyValue) }
        if (drainable.isNotEmpty()) {
            logger.warn(
                "Drain deadline reached; transferring {} players not inside a round",
                drainable.size,
            )
            drainable.forEach { transferOut(it, force = true) }
        }
        if (inRound.isNotEmpty()) {
            logger.warn(
                "Drain deadline reached with {} players still inside a round; leaving them " +
                    "until the round ends or the pod is terminated",
                inRound.size,
            )
        }
    }

    /**
     * True when the transfer (or, under force, the disconnect) was issued. Without force, a player
     * we cannot transfer — no target configured, client older than 1.20.5 — is left alone: they
     * keep playing until the deadline, and the deadline path disconnects them with an honest
     * message rather than the raw proxy-shutdown kick.
     */
    private fun transferOut(player: Player, force: Boolean): Boolean {
        val host = config.transferHost
        val transferable =
            host != null && player.protocolVersion >= ProtocolVersion.MINECRAFT_1_20_5
        if (transferable) {
            logger.info(
                "Draining player via transfer (player={}, target={}:{})",
                player.username,
                host,
                config.transferPort,
            )
            player.transferToHost(InetSocketAddress.createUnresolved(host!!, config.transferPort))
            return true
        }
        if (force) {
            player.disconnect(RESTART_MESSAGE)
            return true
        }
        return false
    }

    private fun roleOf(player: Player): String? =
        player.currentServer.map { it.serverInfo.name }.orElse(null)?.let(serverRole)

    companion object {
        val RESTART_MESSAGE: Component =
            Component.text("This proxy is restarting — please reconnect.")

        /**
         * A transfer is deferred only for players on a server whose role is a real, non-lobby role:
         * that is where a round can be running. No server or no role means nothing to protect.
         */
        @JvmStatic
        fun shouldDefer(role: String?, lobbyValue: String): Boolean =
            role != null && role != lobbyValue
    }
}
