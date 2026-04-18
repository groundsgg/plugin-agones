package gg.grounds.listener

import gg.grounds.agones.AgonesHelper
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent

class PlayerListener(private val agonesHelper: AgonesHelper) {
    fun register(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { _ ->
            if (MinecraftServer.getConnectionManager().onlinePlayers.size == 1) {
                agonesHelper.allocate()
            }
        }

        eventNode.addListener(PlayerDisconnectEvent::class.java) { _ ->
            // Delay the check by 1 tick to allow the server to update its player list
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                if (MinecraftServer.getConnectionManager().onlinePlayers.isEmpty()) {
                    agonesHelper.ready()
                }
            }
        }
    }
}
