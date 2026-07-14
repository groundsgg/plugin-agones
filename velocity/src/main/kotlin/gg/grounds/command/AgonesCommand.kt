package gg.grounds.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.api.ProxyServiceRegistry
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class AgonesCommand(
    private val proxyServer: ProxyServer,
    private val getServerRole: (String) -> String?,
    /**
     * Looked up per invocation, not held: plugin-proxy registers the service during its own
     * startup, which may be after ours, and a reference captured at construction would be null
     * forever.
     */
    private val proxyService: () -> ProxyService? = {
        ProxyServiceRegistry.get(ProxyService::class.java)
    },
) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val servers = proxyServer.allServers

        if (servers.isEmpty()) {
            source.sendMessage(Component.text("No servers registered.", NamedTextColor.RED))
            return
        }

        // Velocity only knows the players connected to *this* proxy. With two proxies in front of
        // one lobby, each of them sees half of it, and /agones confidently printed the half as the
        // whole. Ask the network instead; null means we could not, and we say so rather than
        // printing this proxy's numbers as if they were everyone's.
        val counts = proxyService()?.getNetworkPlayerCounts()

        source.sendMessage(Component.text("--- Agones Servers ---", NamedTextColor.GOLD))

        for (server in servers) {
            val name = server.serverInfo.name
            val address = server.serverInfo.address
            val playerCount = counts?.on(name) ?: server.playersConnected.size
            val type = getServerRole(name) ?: "unknown"
            val typeColor =
                when (type) {
                    "lobby" -> NamedTextColor.GREEN
                    "game" -> NamedTextColor.AQUA
                    "match" -> NamedTextColor.LIGHT_PURPLE
                    else -> NamedTextColor.GRAY
                }

            val line =
                Component.text()
                    .append(Component.text(" $name", NamedTextColor.WHITE))
                    .append(Component.text(" [$type]", typeColor))
                    .append(Component.text(" $address", NamedTextColor.GRAY))
                    .append(Component.text(" ($playerCount players)", NamedTextColor.YELLOW))
                    .build()

            source.sendMessage(line)
        }

        val total = counts?.total ?: proxyServer.playerCount
        source.sendMessage(
            Component.text("Total: ${servers.size} servers, $total players", NamedTextColor.GOLD)
        )

        if (counts == null) {
            source.sendMessage(
                Component.text(
                    "Player counts are for this proxy only — the presence service is unreachable.",
                    NamedTextColor.RED,
                )
            )
        }
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        val source = invocation.source()
        return source is ConsoleCommandSource || source.hasPermission(PERMISSION)
    }

    companion object {
        private const val PERMISSION = "grounds.command.agones"
    }
}
