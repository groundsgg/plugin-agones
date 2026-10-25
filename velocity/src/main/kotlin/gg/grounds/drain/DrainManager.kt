package gg.grounds.drain

import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.AgonesMessage
import gg.grounds.i18n.Translations
import gg.grounds.presentation.VelocitySystemMessages
import gg.grounds.proxy.api.PlayerSessionTransfer
import gg.grounds.proxy.api.ProxyServiceRegistry
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.slf4j.Logger

/**
 * Moves players off this proxy before it shuts down, instead of letting Velocity kick them.
 *
 * Started by the pod's preStop hook via [DrainHttpServer]. From that moment on:
 * - new logins are denied (they bounce back through the public name to a live proxy),
 * - players sitting in a lobby are sent a Minecraft transfer packet, paced at
 *   [DrainConfig.transfersPerSecond] (see [PacedTransfers]) so a full proxy does not reconnect all
 *   at once,
 * - players inside a round stay untouched — Agones keeps their GameServer alive, and the moment the
 *   round sends them back towards a lobby the transfer happens *instead of* that connect,
 * - at the deadline, whoever is *not* inside a round is transferred (paced the same way); players
 *   inside a round are never transferred — the round either ends within the grace period (and the
 *   lobby connect becomes the transfer), or the pod's termination ends the session. A transfer
 *   would end the round just as surely, only earlier.
 *
 * "Inside a round" is decided by the server's `grounds/server-type` role: only the `game` and
 * `match` roles defer the transfer. A server that discovery has no role for cannot be a protected
 * round.
 */
class DrainManager(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val config: DrainConfig,
    private val serverRole: (String) -> String?,
    private val lobbyValue: String,
    private val drainTransferCookie: DrainTransferCookie = DrainTransferCookie(),
    /** Looked up per transfer: plugin-player may register after this plugin starts. */
    private val sessionTransfer: () -> PlayerSessionTransfer? = {
        ProxyServiceRegistry.get(PlayerSessionTransfer::class.java)
    },
    private val transferStager: DrainTransferStager = DrainTransferStager { action ->
        proxy.scheduler.buildTask(plugin, Runnable(action)).delay(1, TimeUnit.SECONDS).schedule()
    },
    private val ticker: DrainTicker = DrainTicker { tick ->
        var task: ScheduledTask? = null
        task =
            proxy.scheduler
                .buildTask(plugin, Runnable { if (tick()) task?.cancel() })
                .delay(1, TimeUnit.SECONDS)
                .repeat(1, TimeUnit.SECONDS)
                .schedule()
    },
    private val messages: Translations =
        Translations.forBundleWithSystemRenderer(
            "gg.grounds.agones.messages",
            DrainManager::class.java.classLoader,
            systemRenderer = VelocitySystemMessages.renderer,
        ),
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

        proxy.scheduler
            .buildTask(plugin, Runnable { onDeadline() })
            .delay(deadlineSeconds, TimeUnit.SECONDS)
            .schedule()
        pace(proxy.allPlayers.filter { !shouldDefer(roleOf(it), lobbyValue) }, force = false)
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
            pace(drainable, force = true)
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
     * Transfers [players] at [DrainConfig.transfersPerSecond]: the first batch now, the rest one
     * batch a second. Whoever left or walked into a round while queued is skipped.
     */
    private fun pace(players: List<Player>, force: Boolean) {
        val paced =
            PacedTransfers(
                players,
                config.transfersPerSecond,
                stillDue = { it.isActive && !shouldDefer(roleOf(it), lobbyValue) },
                transfer = { transferOut(it, force) },
                onFailure = { player, error ->
                    logger.warn("Failed to transfer draining player {}", player.username, error)
                },
            )
        if (paced.tick()) return
        logger.info(
            "Transferring the remaining {} players at {}/s",
            paced.remaining,
            config.transfersPerSecond,
        )
        ticker.everySecondUntilDone(paced::tick)
    }

    /**
     * True when the transfer (or, under force, the disconnect) was issued. Without force, a player
     * we cannot transfer — no target configured, client older than 1.20.5 — is left alone: they
     * keep playing until the deadline, and the deadline path disconnects them with an honest
     * message rather than the raw proxy-shutdown kick.
     */
    private fun transferOut(player: Player, force: Boolean): Boolean {
        val host = config.transferHost
        if (host != null && player.protocolVersion >= ProtocolVersion.MINECRAFT_1_20_5) {
            val transfer = {
                logger.info(
                    "Draining player via transfer (player={}, target={}:{})",
                    player.username,
                    host,
                    config.transferPort,
                )
                transferWithHandoff(
                    sessionTransfer(),
                    player.uniqueId,
                    host,
                    config.transferPort,
                    direct = {
                        player.transferToHost(
                            InetSocketAddress.createUnresolved(host, config.transferPort)
                        )
                    },
                    refuse = {
                        logger.warn(
                            "Session handoff unavailable for draining player {}; disconnecting",
                            player.username,
                        )
                        player.disconnect(restartMessage(player))
                    },
                )
            }
            val payload = currentStaticServerName(player)?.let(drainTransferCookie::encode)
            if (payload != null) {
                transferStager.stage(
                    player.uniqueId.toString(),
                    payload,
                    { player.storeCookie(DrainTransferCookie.KEY, payload) },
                    { player.requestCookie(DrainTransferCookie.KEY) },
                    transfer,
                )
            } else {
                transfer()
            }
            return true
        }
        if (force) {
            player.disconnect(restartMessage(player))
            return true
        }
        return false
    }

    private fun roleOf(player: Player): String? =
        player.currentServer.map { it.serverInfo.name }.orElse(null)?.let(serverRole)

    private fun currentStaticServerName(player: Player): String? =
        player.currentServer
            .map { it.serverInfo.name }
            .orElse(null)
            ?.takeIf { serverName -> shouldPreserveStaticBackend(serverRole(serverName)) }

    fun handleCookie(player: Player, payload: ByteArray?): Boolean {
        val playerId = player.uniqueId.toString()
        if (!transferStager.isPending(playerId)) return false
        transferStager.onCookie(playerId, payload)
        return true
    }

    fun isCookiePending(playerId: String): Boolean = transferStager.isPending(playerId)

    fun restartMessage(player: Player) = messages.render(AgonesMessage.RESTART_DISCONNECT, player)

    companion object {
        /**
         * A transfer is deferred only for players on a real round server. No server, an unknown
         * role, a lobby, or a static server means nothing to protect.
         */
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun shouldDefer(role: String?, lobbyValue: String): Boolean = role in ROUND_ROLES

        @JvmStatic
        fun shouldPreserveStaticBackend(role: String?): Boolean = role == STATIC_SERVER_ROLE

        private val ROUND_ROLES = setOf("game", "match")
        private const val STATIC_SERVER_ROLE = "static"
    }
}

/**
 * Sends a draining player on through plugin-player's session handoff when it is installed: the
 * destination proxy admits a transfer only with the single-use token that handoff stores in a
 * cookie, so a bare transfer packet ends in "This transfer could not be verified". Without
 * plugin-player the bare transfer is all there is. When the handoff cannot be issued, the player is
 * [refuse]d with the restart message rather than sent to a proxy that will turn them away.
 */
internal fun transferWithHandoff(
    session: PlayerSessionTransfer?,
    playerId: UUID,
    host: String,
    port: Int,
    direct: () -> Unit,
    refuse: () -> Unit,
) {
    if (session == null) return direct()
    if (!session.transferToHost(playerId, host, port)) refuse()
}
