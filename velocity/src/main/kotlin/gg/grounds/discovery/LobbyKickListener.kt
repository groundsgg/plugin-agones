package gg.grounds.discovery

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.proxy.server.RegisteredServer
import java.util.UUID

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
     * Another lobby to send the player to, never the one named; null when there is none. Picked
     * like a join — the fullest lobby with room — and reserved for the player.
     */
    private val pickLobby: (exclude: String, playerId: UUID) -> RegisteredServer?,
) {

    @Subscribe
    fun onKickedFromServer(event: KickedFromServerEvent) {
        if (!event.player.isActive) return
        if (event.result !is KickedFromServerEvent.DisconnectPlayer) return
        val kickedFrom = event.server.serverInfo.name
        if (!isLobby(kickedFrom)) return
        val target = pickLobby(kickedFrom, event.player.uniqueId) ?: return
        event.result =
            KickedFromServerEvent.RedirectPlayer.create(target, event.serverKickReason.orElse(null))
    }
}
