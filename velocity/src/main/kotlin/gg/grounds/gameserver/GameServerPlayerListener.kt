package gg.grounds.gameserver

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.agones.AgonesHelper

class GameServerPlayerListener(
    private val proxyServer: ProxyServer,
    private val agonesHelper: AgonesHelper,
) {

    @Subscribe
    fun onPlayerJoin(event: PostLoginEvent) {
        if (proxyServer.allPlayers.size == 1) {
            agonesHelper.allocate()
        }
    }

    @Subscribe
    fun onPlayerDisconnect(event: DisconnectEvent) {
        if (proxyServer.allPlayers.isEmpty()) {
            agonesHelper.ready()
        }
    }
}
