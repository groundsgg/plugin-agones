package gg.grounds.discovery

import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.Component

class DiscoveryPlayerListener(
    private val proxyServer: ProxyServer,
    private val lobbyServers: Set<String>,
    /**
     * Network-wide players per backend server, or null when the network cannot be asked. Null falls
     * back to this proxy's own view — on a single proxy that is the same number, and with several
     * it still spreads, just per proxy rather than per network.
     */
    private val networkCounts: () -> Map<String, Int>?,
) {

    @Subscribe
    fun onLogin(event: LoginEvent) {
        if (findLobbyServer() != null) return

        event.result =
            ResultedEvent.ComponentResult.denied(
                Component.text(
                    "No lobby servers are currently available. Please try again in a moment."
                )
            )
    }

    @Subscribe
    fun onPlayerChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        if (event.initialServer.isPresent) return

        val lobby = findLobbyServer()
        if (lobby != null) {
            event.setInitialServer(lobby)
        }
    }

    private fun findLobbyServer(): RegisteredServer? {
        val lobbies = proxyServer.allServers.filter { it.serverInfo.name in lobbyServers }
        if (lobbies.isEmpty()) return null

        val counts = networkCounts()
        val candidates =
            lobbies.map { server ->
                val name = server.serverInfo.name
                LobbySelection.Candidate(
                    name,
                    if (counts != null) counts[name] ?: 0 else server.playersConnected.size,
                )
            }
        val chosen = LobbySelection.pick(candidates) ?: return null
        return lobbies.firstOrNull { it.serverInfo.name == chosen }
    }
}
