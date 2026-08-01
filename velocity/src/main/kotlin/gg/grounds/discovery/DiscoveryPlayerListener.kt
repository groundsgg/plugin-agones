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
    private val lobbySoftCap: Int,
    /**
     * Network-wide players per backend server, or null when the network cannot be asked. Null falls
     * back to this proxy's own view — enough to keep packing roughly right on a single proxy, and
     * strictly better than picking blind.
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
                LobbyPacking.Candidate(
                    name,
                    if (counts != null) counts[name] ?: 0 else server.playersConnected.size,
                )
            }
        val chosen = LobbyPacking.pick(candidates, lobbySoftCap) ?: return null
        return lobbies.firstOrNull { it.serverInfo.name == chosen }
    }
}
