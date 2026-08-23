package gg.grounds.drain

import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.CookieReceiveEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent

class DrainListener(private val drainManager: DrainManager) {

    /**
     * A draining proxy takes no new players. The deny message is what a client sees in the rare
     * window where a connection still reaches this pod after it left the Service's endpoints —
     * reconnecting through the public name lands them on a live proxy.
     */
    @Subscribe
    fun onLogin(event: LoginEvent) {
        if (!drainManager.isDraining) return
        event.result = ResultedEvent.ComponentResult.denied(DrainManager.RESTART_MESSAGE)
    }

    /**
     * The moment a round is over, its players head back towards a lobby — on a draining proxy that
     * connect becomes the transfer to another proxy instead. Every path to a lobby runs through
     * this event: an explicit connection request, a kick-redirect, a plugin's fireAndForget.
     */
    @Subscribe
    fun onServerPreConnect(event: ServerPreConnectEvent) {
        val target = event.result.server.orElse(null) ?: return
        if (drainManager.interceptConnect(event.player, target.serverInfo.name)) {
            event.result = ServerPreConnectEvent.ServerResult.denied()
        }
    }

    @Subscribe
    fun onCookieReceive(event: CookieReceiveEvent) {
        if (event.originalKey != DrainTransferCookie.KEY) return
        if (drainManager.handleCookie(event.player, event.originalData)) {
            event.result = CookieReceiveEvent.ForwardResult.handled()
        }
    }
}
