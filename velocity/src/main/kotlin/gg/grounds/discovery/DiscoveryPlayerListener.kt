package gg.grounds.discovery

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.proxy.ProxyServer

class DiscoveryPlayerListener(private val proxyServer: ProxyServer) {

    @Subscribe
    fun onPlayerChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        if (event.initialServer.isPresent || proxyServer.allServers.isEmpty()) {
            return
        }

        event.setInitialServer(proxyServer.allServers.first())
    }
}
