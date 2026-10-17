package gg.grounds.discovery

import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.util.UUID
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LobbyKickListenerTest {
    private val oldLobby = server("lobby-old")
    private val newLobby = server("lobby-new")
    private val playerId = UUID.randomUUID()
    private val excluded = mutableListOf<String>()
    private val placedFor = mutableListOf<UUID>()
    private val listener =
        LobbyKickListener(
            isLobby = { it.startsWith("lobby-") },
            pickLobby = { exclude, player ->
                excluded.add(exclude)
                placedFor.add(player)
                newLobby
            },
        )

    @Test
    fun `a player dropped by a lobby is redirected to another lobby`() {
        val event = kick(from = oldLobby)

        listener.onKickedFromServer(event)

        val result = event.result as KickedFromServerEvent.RedirectPlayer
        assertSame(newLobby, result.server)
        assertEquals(listOf("lobby-old"), excluded)
        assertEquals(listOf(playerId), placedFor)
    }

    @Test
    fun `no other lobby leaves Velocity's disconnect in place`() {
        val event = kick(from = oldLobby)

        LobbyKickListener({ true }, { _, _ -> null }).onKickedFromServer(event)

        assertTrue(event.result is KickedFromServerEvent.DisconnectPlayer)
    }

    @Test
    fun `a kick from a non-lobby server is not touched`() {
        val event = kick(from = server("duel-abc"))

        listener.onKickedFromServer(event)

        assertTrue(event.result is KickedFromServerEvent.DisconnectPlayer)
    }

    @Test
    fun `a redirect another plugin already chose is kept`() {
        val elsewhere = server("lobby-home")
        val event =
            kick(from = oldLobby, result = KickedFromServerEvent.RedirectPlayer.create(elsewhere))

        listener.onKickedFromServer(event)

        assertSame(elsewhere, (event.result as KickedFromServerEvent.RedirectPlayer).server)
    }

    @Test
    fun `a player leaving the proxy is not redirected`() {
        val event = kick(from = oldLobby, active = false)

        listener.onKickedFromServer(event)

        assertTrue(event.result is KickedFromServerEvent.DisconnectPlayer)
    }

    private fun kick(
        from: RegisteredServer,
        active: Boolean = true,
        result: KickedFromServerEvent.ServerKickResult =
            KickedFromServerEvent.DisconnectPlayer.create(Component.text("Server closed")),
    ) =
        KickedFromServerEvent(
            proxy<Player>(mapOf("isActive" to active, "getUniqueId" to playerId)),
            from,
            Component.text("Server closed"),
            false,
            result,
        )

    private fun server(name: String): RegisteredServer =
        proxy(
            mapOf(
                "getServerInfo" to
                    ServerInfo(name, InetSocketAddress.createUnresolved("10.0.0.1", 25565))
            )
        )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> proxy(responses: Map<String, Any> = emptyMap()): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            responses[method.name]
                ?: when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    else -> null
                }
        } as T
}
