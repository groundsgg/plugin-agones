package gg.grounds.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class AgonesCommand(private val proxyServer: ProxyServer, private val lobbyServers: Set<String>) :
    SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val servers = proxyServer.allServers

        if (servers.isEmpty()) {
            source.sendMessage(Component.text("No servers registered.", NamedTextColor.RED))
            return
        }

        source.sendMessage(Component.text("--- Agones Servers ---", NamedTextColor.GOLD))

        for (server in servers) {
            val name = server.serverInfo.name
            val address = server.serverInfo.address
            val playerCount = server.playersConnected.size
            val type = if (name in lobbyServers) "lobby" else "game"

            val line =
                Component.text()
                    .append(Component.text(" $name", NamedTextColor.WHITE))
                    .append(
                        Component.text(
                            " [$type]",
                            if (type == "lobby") NamedTextColor.GREEN else NamedTextColor.AQUA,
                        )
                    )
                    .append(Component.text(" $address", NamedTextColor.GRAY))
                    .append(Component.text(" ($playerCount players)", NamedTextColor.YELLOW))
                    .build()

            source.sendMessage(line)
        }

        source.sendMessage(
            Component.text(
                "Total: ${servers.size} servers, ${proxyServer.playerCount} players",
                NamedTextColor.GOLD,
            )
        )
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean = true
}
