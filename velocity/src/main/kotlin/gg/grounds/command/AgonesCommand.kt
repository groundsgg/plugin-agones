package gg.grounds.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.AgonesMessage
import gg.grounds.i18n.Palette
import gg.grounds.i18n.Translations
import gg.grounds.presentation.VelocitySystemMessages
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.api.ProxyServiceRegistry
import net.kyori.adventure.text.Component

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
    private val messages: Translations =
        Translations.forBundleWithSystemRenderer(
            "gg.grounds.agones.messages",
            AgonesCommand::class.java.classLoader,
            systemRenderer = VelocitySystemMessages.renderer,
        ),
) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val servers = proxyServer.allServers

        if (servers.isEmpty()) {
            messages.send(source, AgonesMessage.NO_SERVERS)
            return
        }

        // Velocity only knows the players connected to *this* proxy. With two proxies in front of
        // one lobby, each of them sees half of it, and /agones confidently printed the half as the
        // whole. Ask the network instead; null means we could not, and we say so rather than
        // printing this proxy's numbers as if they were everyone's.
        val counts = proxyService()?.getNetworkPlayerCounts()

        messages.send(source, AgonesMessage.SERVER_HEADER)

        for (server in servers) {
            val name = server.serverInfo.name
            val address = server.serverInfo.address
            val playerCount = counts?.on(name) ?: server.playersConnected.size
            val type = getServerRole(name) ?: "unknown"
            val typeColor =
                when (type) {
                    "lobby" -> Palette.SUCCESS
                    "game" -> Palette.ACCENT
                    "match" -> Palette.INFO
                    else -> Palette.TEXT_MUTED
                }
            messages.send(
                source,
                AgonesMessage.SERVER_LINE,
                "name" to Component.text(name, Palette.TEXT),
                "type" to Component.text(type, typeColor),
                "address" to Component.text(address.toString(), Palette.TEXT_MUTED),
                "players" to playerCount,
            )
        }

        val total = counts?.total ?: proxyServer.playerCount
        messages.send(
            source,
            AgonesMessage.SERVER_TOTAL,
            "servers" to servers.size,
            "players" to total,
        )

        if (counts == null) {
            messages.send(source, AgonesMessage.LOCAL_PLAYER_COUNTS)
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
