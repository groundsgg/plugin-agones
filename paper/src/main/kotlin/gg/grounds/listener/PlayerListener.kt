package gg.grounds.listener

import gg.grounds.agones.AgonesHelper
import org.bukkit.Server
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

class PlayerListener(
    private val plugin: Plugin,
    private val server: Server,
    private val agonesHelper: AgonesHelper,
) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (server.onlinePlayers.size == 1) {
            agonesHelper.allocate()
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        ensureReadyIfWillBeEmpty()
    }

    @EventHandler
    fun onPlayerKick(event: PlayerKickEvent) {
        ensureReadyIfWillBeEmpty()
    }

    private fun ensureReadyIfWillBeEmpty() {
        // Delay the check by 1 tick to allow the server to update its player list
        server.scheduler.runTaskLater(
            plugin,
            Runnable {
                if (server.onlinePlayers.isEmpty()) {
                    agonesHelper.ready()
                }
            },
            1L,
        )
    }
}
