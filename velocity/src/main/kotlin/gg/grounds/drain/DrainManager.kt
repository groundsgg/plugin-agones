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
 * - after the deadline, a player whose round ends is transferred on the way back to a lobby, or,
 *   with no transfer target (wait-only, the Bedrock proxy), disconnected with the restart message,
 *   so the proxy empties as rounds end and the grace period never has to cut a round short.
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

    /**
     * Past the deadline, a player whose round just ended is moved on like everyone else was, or
     * told the proxy is restarting when there is nowhere to move them. Left alone they would sit in
     * a lobby on this proxy until the pod is killed.
     */
    @Volatile private var deadlinePassed = false

    private val retryQueue =
        RetryingTransfers<Player, UUID>(
            config.transfersPerSecond,
            { it.uniqueId },
            { it.isActive && !shouldDefer(roleOf(it), lobbyValue) },
            { player ->
                runCatching { transferOut(player, deadlinePassed) }
                    .getOrElse {
                        logger.warn("Failed to transfer draining player {}", player.username, it)
                        if (deadlinePassed) player.disconnect(restartMessage(player))
                        deadlinePassed
                    }
            },
        )
    private var tickerRunning = false
    private val sentTransfers = java.util.concurrent.ConcurrentHashMap.newKeySet<UUID>()

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
        return transferOut(player, force = deadlinePassed)
    }

    private fun onDeadline() {
        deadlinePassed = true
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
    @Synchronized
    private fun pace(players: List<Player>, force: Boolean) {
        players.forEach(retryQueue::offer)
        if (tickerRunning) return
        tickerRunning = true
        ticker.everySecondUntilDone {
            synchronized(this) {
                val done = retryQueue.tick()
                if (done) tickerRunning = false
                done
            }
        }
    }

    /**
     * True when the transfer (or, under force, the disconnect) was issued. Without force, a player
     * we cannot transfer — no target configured, client older than 1.20.5 — is left alone: they
     * keep playing until the deadline, and the deadline path disconnects them with an honest
     * message rather than the raw proxy-shutdown kick.
     */
    private fun transferOut(player: Player, force: Boolean): Boolean {
        if (sentTransfers.contains(player.uniqueId)) return true
        if (transferStager.isPending(player.uniqueId.toString())) return true
        val host = config.transferHost
        if (host != null && player.protocolVersion >= ProtocolVersion.MINECRAFT_1_20_5) {
            var issued = true
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
                    refuse = {
                        if (deadlinePassed) {
                            logger.warn(
                                "Drain deadline reached without transfer capacity (player={})",
                                player.username,
                            )
                            player.disconnect(restartMessage(player))
                        } else {
                            issued = false
                            // Global issuance budget is shared by all draining sources. Retry
                            // later; do not mint a ticket until this actual send attempt.
                            pace(listOf(player), force = false)
                        }
                    },
                )
                if (issued) sentTransfers.add(player.uniqueId)
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
            return issued
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
 * cookie. Missing plugin-player or capacity calls [refuse]; no bare transfer can bypass admission.
 */
internal fun transferWithHandoff(
    session: PlayerSessionTransfer?,
    playerId: UUID,
    host: String,
    port: Int,
    refuse: () -> Unit,
) {
    if (session == null) return refuse()
    if (!session.transferToHost(playerId, host, port)) refuse()
}
