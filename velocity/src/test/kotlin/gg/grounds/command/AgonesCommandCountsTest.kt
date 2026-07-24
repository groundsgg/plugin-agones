package gg.grounds.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import gg.grounds.proxy.api.NetworkPlayerCounts
import gg.grounds.proxy.api.ProxyService
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The reported bug: two players stand in the same lobby, each connected through a different proxy.
 * Velocity's `playersConnected` only ever counts the players on the proxy that is asking, so
 * /agones printed "1 players" — the half it could see, presented as the whole.
 */
class AgonesCommandCountsTest {

    @Test
    fun `reports the whole network, not just the players on this proxy`() {
        val sent = mutableListOf<Component>()
        val source = recordingSource(sent)
        val command =
            AgonesCommand(
                proxyServer = proxyServer(localPlayersOnLobby = 1, localPlayerCount = 1),
                getServerRole = { "lobby" },
                proxyService = {
                    proxyServiceReturning(NetworkPlayerCounts(mapOf("lobby" to 2), total = 2))
                },
            )

        command.execute(invocation(source))

        assertTrue(sent.any { "(2 players)" in it.plain() }, "server line: ${sent.plain()}")
        assertTrue(sent.any { "2 players" in it.plain() && "Total" in it.plain() })
        assertFalse(sent.any { "unreachable" in it.plain() })
    }

    // Falling back to this proxy's own numbers is exactly the bug. If we have to do it, say so —
    // an unlabelled proxy-local count is indistinguishable from a network one.
    @Test
    fun `says so when it could only count this proxy`() {
        val sent = mutableListOf<Component>()
        val source = recordingSource(sent)
        val command =
            AgonesCommand(
                proxyServer = proxyServer(localPlayersOnLobby = 1, localPlayerCount = 1),
                getServerRole = { "lobby" },
                proxyService = { null },
            )

        command.execute(invocation(source))

        assertTrue(sent.any { "(1 players)" in it.plain() })
        assertTrue(
            sent.any { "this proxy only" in it.plain() },
            "a proxy-local count must be labelled: ${sent.plain()}",
        )
    }

    @Test
    fun `shows a server nobody is on as empty rather than dropping it`() {
        val sent = mutableListOf<Component>()
        val source = recordingSource(sent)
        val command =
            AgonesCommand(
                proxyServer = proxyServer(localPlayersOnLobby = 0, localPlayerCount = 0),
                getServerRole = { "lobby" },
                // The network reply omits unoccupied servers entirely.
                proxyService = { proxyServiceReturning(NetworkPlayerCounts(emptyMap(), total = 0)) },
            )

        command.execute(invocation(source))

        assertTrue(sent.any { "lobby" in it.plain() && "(0 players)" in it.plain() })
    }

    private fun Component.plain(): String = GsonComponentSerializer.gson().serialize(this)

    private fun List<Component>.plain(): String = joinToString("\n") { it.plain() }

    private fun proxyServiceReturning(counts: NetworkPlayerCounts): ProxyService =
        proxy(mapOf("getNetworkPlayerCounts" to counts))

    private fun proxyServer(localPlayersOnLobby: Int, localPlayerCount: Int): ProxyServer {
        val server: RegisteredServer =
            proxy(
                mapOf(
                    "getServerInfo" to
                        ServerInfo("lobby", InetSocketAddress.createUnresolved("lobby", 25565)),
                    "getPlayersConnected" to (1..localPlayersOnLobby).map { Any() }.toSet(),
                )
            )
        return proxy(mapOf("getAllServers" to listOf(server), "getPlayerCount" to localPlayerCount))
    }

    private fun recordingSource(sink: MutableList<Component>): CommandSource =
        proxy(
            mapOf(
                "sendMessage" to
                    { args: Array<out Any?> ->
                        sink.add(args.first() as Component)
                        Unit
                    }
            )
        )

    private fun invocation(source: CommandSource): SimpleCommand.Invocation =
        proxy(mapOf("source" to source, "arguments" to emptyArray<String>(), "alias" to "agones"))

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> proxy(responses: Map<String, Any> = emptyMap()): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args
            ->
            val response = responses[method.name]
            when {
                response is Function1<*, *> ->
                    (response as (Array<out Any?>) -> Any?)(args.orEmpty())
                response != null -> response
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Void.TYPE -> null
                else -> null
            }
        } as T
}
