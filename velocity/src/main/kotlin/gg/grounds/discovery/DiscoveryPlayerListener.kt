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

    private fun findLobbyServer(): RegisteredServer? =
        proxyServer.allServers.firstOrNull { it.serverInfo.name in lobbyServers }
}
