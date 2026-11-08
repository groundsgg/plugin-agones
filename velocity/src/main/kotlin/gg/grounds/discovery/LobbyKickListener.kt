package gg.grounds.discovery

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import java.util.UUID
import net.kyori.adventure.text.Component

/**
 * A lobby that goes away takes its players to another lobby instead of off the network.
 *
 * When a lobby shuts down — a rollout, a crash, a node drained under it — Velocity would disconnect
 * everyone on it: the `try` list holds no real server to fall back to. This steps in only where
 * Velocity would disconnect; a redirect or a notice another plugin (or Velocity itself) already
 * chose is left alone, and so is a player who is leaving the proxy anyway.
 */
class LobbyKickListener(
    private val isLobby: (String) -> Boolean,
    /**
     * Another lobby to send the player to, excluding every failed lobby; null when there is none.
     * Picked like a join — the fullest lobby with room — and reserved for the player.
     */
    private val pickLobby: (exclude: Set<String>, playerId: UUID) -> RegisteredServer?,
) {

    private data class Attempt(
        val owner: Player,
        val failed: MutableSet<String>,
        val reason: Component?,
        var version: Int = 0,
    )

    private val attempts = HashMap<UUID, Attempt>()

    @Subscribe
    fun onKickedFromServer(event: KickedFromServerEvent) {
        if (!event.player.isActive) return
        if (event.result !is KickedFromServerEvent.DisconnectPlayer) return
        val kickedFrom = canonicalServerName(event.server.serverInfo.name)
        if (!isLobby(event.server.serverInfo.name)) return
        val playerId = event.player.uniqueId
        val (attempt, version, excluded) =
            synchronized(this) {
                val existing = attempts[playerId]
                val current =
                    existing?.takeIf { it.owner === event.player }
                        ?: run {
                            // Do not evict active attempts: that could restart a cycle under load.
                            if (existing == null && attempts.size >= MAX_PLAYERS) return
                            Attempt(
                                    event.player,
                                    mutableSetOf(),
                                    event.serverKickReason.orElse(null),
                                )
                                .also { attempts[playerId] = it }
                        }
                current.version++
                if (!current.failed.add(kickedFrom) || current.failed.size >= MAX_ATTEMPTS) {
                    disconnectWithOriginalReason(event, current)
                    return
                }
                Triple(current, current.version, current.failed.toSet())
            }
        // Presence lookup in the picker may block; never hold the shared attempt lock here.
        val target = pickLobby(excluded, playerId)
        val targetName = target?.serverInfo?.name?.let(::canonicalServerName)
        synchronized(this) {
            if (
                attempts[playerId] !== attempt ||
                    attempt.version != version ||
                    event.result !is KickedFromServerEvent.DisconnectPlayer
            )
                return
            if (target == null || targetName in excluded) {
                disconnectWithOriginalReason(event, attempt)
                return
            }
            event.result =
                KickedFromServerEvent.RedirectPlayer.create(
                    target,
                    event.serverKickReason.orElse(null),
                )
        }
    }

    private fun disconnectWithOriginalReason(event: KickedFromServerEvent, attempt: Attempt) {
        attempt.reason?.let { event.result = KickedFromServerEvent.DisconnectPlayer.create(it) }
    }

    @Subscribe
    @Synchronized
    fun onServerPostConnect(event: ServerPostConnectEvent) {
        clearAttempt(event.player)
    }

    @Subscribe
    @Synchronized
    fun onDisconnect(event: DisconnectEvent) {
        clearAttempt(event.player)
    }

    private fun clearAttempt(player: Player) {
        val id = player.uniqueId
        if (attempts[id]?.owner === player) attempts.remove(id)
    }

    companion object {
        private const val MAX_ATTEMPTS = 8
        private const val MAX_PLAYERS = 10_000
    }
}
