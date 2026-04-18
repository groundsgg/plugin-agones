package gg.grounds.discovery

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.proxy.ProxyServer

class DiscoveryPlayerListener(
    private val proxyServer: ProxyServer,
    private val lobbyServers: Set<String>,
) {

    @Subscribe
    fun onPlayerChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        if (event.initialServer.isPresent) return

        val lobby = proxyServer.allServers.firstOrNull { it.serverInfo.name in lobbyServers }
        if (lobby != null) {
            event.setInitialServer(lobby)
        }
    }
}
