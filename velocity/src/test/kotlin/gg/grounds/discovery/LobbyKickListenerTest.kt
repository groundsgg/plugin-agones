package gg.grounds.discovery

import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LobbyKickListenerTest {
    private val oldLobby = server("lobby-old")
    private val newLobby = server("lobby-new")
    private val playerId = UUID.randomUUID()
    private val currentPlayer = player()
    private val excluded = mutableListOf<Set<String>>()
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
        assertEquals(listOf(setOf("lobby-old")), excluded)
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

    @Test
    fun `rejected lobbies do not redirect a player back into a cycle`() {
        val cycling =
            LobbyKickListener(
                { true },
                { exclude, _ -> if ("lobby-new" !in exclude) newLobby else oldLobby },
            )
        val first = kick(oldLobby)
        cycling.onKickedFromServer(first)
        assertSame(newLobby, (first.result as KickedFromServerEvent.RedirectPlayer).server)

        val second = kick(newLobby)
        cycling.onKickedFromServer(second)
        assertTrue(second.result is KickedFromServerEvent.DisconnectPlayer)
        assertEquals(
            Component.text("Server closed"),
            (second.result as KickedFromServerEvent.DisconnectPlayer).reasonComponent,
        )
    }

    @Test
    fun `exhaustion preserves the first backend rejection reason`() {
        val fallback =
            LobbyKickListener(
                { true },
                { excluded, _ -> if ("lobby-new" !in excluded) newLobby else oldLobby },
            )
        fallback.onKickedFromServer(kick(oldLobby, reason = Component.text("Invalid Version")))
        val last = kick(newLobby, reason = Component.text("Other failure"))
        fallback.onKickedFromServer(last)
        assertEquals(
            Component.text("Invalid Version"),
            (last.result as KickedFromServerEvent.DisconnectPlayer).reasonComponent,
        )
    }

    @Test
    fun `three failed lobbies are excluded while a healthy fourth remains selectable`() {
        val lobbies = listOf(oldLobby, newLobby, server("lobby-third"), server("lobby-healthy"))
        val seen = mutableListOf<Set<String>>()
        val fallback =
            LobbyKickListener(
                { true },
                { excluded, _ ->
                    seen.add(excluded)
                    lobbies.firstOrNull { it.serverInfo.name !in excluded }
                },
            )
        for (i in 0..2) {
            val event = kick(lobbies[i])
            fallback.onKickedFromServer(event)
            assertSame(
                lobbies[i + 1],
                (event.result as KickedFromServerEvent.RedirectPlayer).server,
            )
        }
        assertEquals(setOf("lobby-old", "lobby-new", "lobby-third"), seen.last())
    }

    @Test
    fun `successful connection clears failed lobby history`() {
        val fallback =
            LobbyKickListener(
                { true },
                { excluded, _ -> if ("lobby-new" !in excluded) newLobby else oldLobby },
            )
        fallback.onKickedFromServer(kick(oldLobby))
        fallback.onServerPostConnect(ServerPostConnectEvent(currentPlayer, oldLobby))
        val later = kick(newLobby)
        fallback.onKickedFromServer(later)
        assertSame(oldLobby, (later.result as KickedFromServerEvent.RedirectPlayer).server)
    }

    @Test
    fun `disconnect clears failed lobby history`() {
        val fallback =
            LobbyKickListener(
                { true },
                { excluded, _ -> if ("lobby-new" !in excluded) newLobby else oldLobby },
            )
        fallback.onKickedFromServer(kick(oldLobby))
        fallback.onDisconnect(
            DisconnectEvent(currentPlayer, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN)
        )
        val later = kick(newLobby)
        fallback.onKickedFromServer(later)
        assertSame(oldLobby, (later.result as KickedFromServerEvent.RedirectPlayer).server)
    }

    @Test
    fun `disconnect from a rejected duplicate connection does not reset the active fallback`() {
        val duplicate = player()
        val fallback =
            LobbyKickListener(
                { true },
                { excluded, _ -> if ("lobby-new" !in excluded) newLobby else oldLobby },
            )
        fallback.onKickedFromServer(kick(oldLobby))
        fallback.onDisconnect(
            DisconnectEvent(duplicate, DisconnectEvent.LoginStatus.CONFLICTING_LOGIN)
        )
        val second = kick(newLobby)
        fallback.onKickedFromServer(second)
        assertTrue(second.result is KickedFromServerEvent.DisconnectPlayer)
    }

    @Test
    fun `late old disconnect does not reset a new connection fallback`() {
        val nextConnection = player()
        val fallback =
            LobbyKickListener(
                { true },
                { excluded, _ -> if ("lobby-new" !in excluded) newLobby else oldLobby },
            )
        fallback.onKickedFromServer(kick(oldLobby))
        val fresh = kick(oldLobby, connection = nextConnection)
        fallback.onKickedFromServer(fresh)
        assertTrue(fresh.result is KickedFromServerEvent.RedirectPlayer)
        fallback.onDisconnect(
            DisconnectEvent(currentPlayer, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN)
        )
        val next = kick(newLobby, connection = nextConnection)
        fallback.onKickedFromServer(next)
        assertTrue(next.result is KickedFromServerEvent.DisconnectPlayer)
    }

    @Test
    fun `a blocked picker does not block cleanup or apply a stale redirect`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val fallback =
            LobbyKickListener(
                { true },
                { _, _ ->
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    newLobby
                },
            )
        val event = kick(oldLobby)
        val unrelated = proxy<Player>(mapOf("getUniqueId" to UUID.randomUUID()))
        try {
            val picking = pool.submit { fallback.onKickedFromServer(event) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val unrelatedCleanup =
                pool.submit {
                    fallback.onDisconnect(
                        DisconnectEvent(unrelated, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN)
                    )
                }
            unrelatedCleanup.get(2, TimeUnit.SECONDS)
            val cleanup =
                pool.submit {
                    fallback.onDisconnect(
                        DisconnectEvent(currentPlayer, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN)
                    )
                }
            cleanup.get(2, TimeUnit.SECONDS)
            release.countDown()
            picking.get(5, TimeUnit.SECONDS)
            assertTrue(event.result is KickedFromServerEvent.DisconnectPlayer)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `a notice another plugin chose is kept`() {
        val notice = KickedFromServerEvent.Notify.create(Component.text("notice"))
        val event = kick(oldLobby, result = notice)
        listener.onKickedFromServer(event)
        assertSame(notice, event.result)
    }

    @Test
    fun `redirects stop after a bounded number of different failed lobbies`() {
        val lobbies = (0..10).map { server("lobby-$it") }
        val fallback =
            LobbyKickListener(
                { true },
                { excluded, _ -> lobbies.firstOrNull { it.serverInfo.name !in excluded } },
            )
        for (i in 0..6) {
            val event = kick(lobbies[i])
            fallback.onKickedFromServer(event)
            assertTrue(event.result is KickedFromServerEvent.RedirectPlayer)
        }
        val last = kick(lobbies[7])
        fallback.onKickedFromServer(last)
        assertTrue(last.result is KickedFromServerEvent.DisconnectPlayer)
    }

    private fun player(active: Boolean = true): Player =
        proxy(mapOf("isActive" to active, "getUniqueId" to playerId))

    private fun kick(
        from: RegisteredServer,
        active: Boolean = true,
        reason: Component = Component.text("Server closed"),
        connection: Player = if (active) currentPlayer else player(false),
        result: KickedFromServerEvent.ServerKickResult =
            KickedFromServerEvent.DisconnectPlayer.create(Component.text("Server closed")),
    ) = KickedFromServerEvent(connection, from, reason, false, result)

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
