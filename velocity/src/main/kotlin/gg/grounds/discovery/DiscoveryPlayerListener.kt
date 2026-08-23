package gg.grounds.discovery

import com.velocitypowered.api.event.Continuation
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.CookieReceiveEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.drain.DrainTransferCookie
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import net.kyori.adventure.text.Component

internal fun selectDrainStaticServer(
    serverName: String,
    servers: Collection<RegisteredServer>,
    serverRole: (String) -> String?,
): RegisteredServer? =
    servers.firstOrNull { server ->
        canonicalServerName(server.serverInfo.name) == canonicalServerName(serverName) &&
            serverRole(server.serverInfo.name) == STATIC_SERVER_ROLE
    }

private const val STATIC_SERVER_ROLE = "static"

internal fun consumeDrainTransferCookie(clearCookie: () -> Unit) {
    try {
        clearCookie()
    } catch (_: Exception) {
        // The destination choice and its continuation must not depend on client cookie storage.
    }
}

class DiscoveryPlayerListener(
    private val plugin: Any,
    private val proxyServer: ProxyServer,
    private val lobbyServers: Set<String>,
    private val serverRole: (String) -> String?,
    /**
     * Network-wide players per backend server, or null when the network cannot be asked. Null falls
     * back to this proxy's own view — on a single proxy that is the same number, and with several
     * it still spreads, just per proxy rather than per network.
     */
    private val networkCounts: () -> Map<String, Int>?,
    private val drainTransferCookie: DrainTransferCookie = DrainTransferCookie(),
) {
    private val pendingCookies = ConcurrentHashMap<UUID, PendingCookieRequest>()

    @Subscribe
    fun onLogin(event: LoginEvent) {
        if (findLobbyServer() != null) return

        event.result =
            ResultedEvent.ComponentResult.denied(
                Component.text(
                    "No lobby servers are currently available. Please try again in a moment."
                )
            )
    }

    @Subscribe
    fun onPlayerChooseInitialServer(event: PlayerChooseInitialServerEvent): EventTask =
        EventTask.withContinuation { continuation ->
            if (event.initialServer.isPresent) {
                continuation.resume()
                return@withContinuation
            }
            val player = event.player
            if (player.protocolVersion < ProtocolVersion.MINECRAFT_1_20_5) {
                chooseServer(event, null)
                continuation.resume()
                return@withContinuation
            }

            val pending = PendingCookieRequest(event, continuation)
            pendingCookies.put(player.uniqueId, pending)?.complete(null)
            try {
                player.requestCookie(DrainTransferCookie.KEY)
                pending.timeoutTask =
                    proxyServer.scheduler
                        .buildTask(
                            plugin,
                            Runnable {
                                if (pendingCookies.remove(player.uniqueId, pending))
                                    pending.complete(null)
                            },
                        )
                        .delay(COOKIE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        .schedule()
            } catch (error: IllegalArgumentException) {
                if (pendingCookies.remove(player.uniqueId, pending)) pending.complete(null)
            }
        }

    @Subscribe
    fun onCookieReceive(event: CookieReceiveEvent) {
        if (event.originalKey != DrainTransferCookie.KEY) return
        event.result = CookieReceiveEvent.ForwardResult.handled()
        val payload = event.originalData
        consumeDrainTransferCookie {
            event.player.storeCookie(DrainTransferCookie.KEY, byteArrayOf())
        }
        pendingCookies.remove(event.player.uniqueId)?.complete(payload)
    }

    private fun chooseServer(event: PlayerChooseInitialServerEvent, payload: ByteArray?) {
        if (event.initialServer.isPresent) return
        val preferred =
            drainTransferCookie.decode(payload)?.let { serverName ->
                selectDrainStaticServer(serverName, proxyServer.allServers, serverRole)
            }
        if (preferred != null) {
            event.setInitialServer(preferred)
            return
        }
        findLobbyServer()?.let(event::setInitialServer)
    }

    private fun findLobbyServer(): RegisteredServer? {
        val lobbies = proxyServer.allServers.filter { it.serverInfo.name in lobbyServers }
        if (lobbies.isEmpty()) return null

        val counts = networkCounts()
        val candidates =
            lobbies.map { server ->
                val name = server.serverInfo.name
                LobbySelection.Candidate(
                    name,
                    if (counts != null) counts[name] ?: 0 else server.playersConnected.size,
                )
            }
        val chosen = LobbySelection.pick(candidates) ?: return null
        return lobbies.firstOrNull { it.serverInfo.name == chosen }
    }

    private inner class PendingCookieRequest(
        private val event: PlayerChooseInitialServerEvent,
        private val continuation: Continuation,
    ) {
        var timeoutTask: ScheduledTask? = null

        fun complete(payload: ByteArray?) {
            timeoutTask?.cancel()
            try {
                chooseServer(event, payload)
            } finally {
                continuation.resume()
            }
        }
    }

    private companion object {
        private const val COOKIE_TIMEOUT_MILLIS = 1_000L
    }
}
