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

internal fun shouldDenyInitialLogin(
    hasLobby: Boolean,
    hasStatic: Boolean,
    protocolVersion: ProtocolVersion,
): Boolean = !hasLobby && !(hasStatic && protocolVersion >= ProtocolVersion.MINECRAFT_1_20_5)

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
    /**
     * The lobby a joining player lands on, or null when there is none — see
     * [DiscoveryService.pickLobby]. Spreads by network-wide counts when the network can be asked,
     * by this proxy's own view otherwise, and avoids outdated lobbies.
     */
    private val pickLobby: () -> RegisteredServer?,
    private val serverRole: (String) -> String?,
    private val drainTransferCookie: DrainTransferCookie = DrainTransferCookie(),
    private val sourceCookiePending: (String) -> Boolean = { false },
) {
    private val pendingCookies = ConcurrentHashMap<UUID, PendingCookieRequest>()

    @Subscribe
    fun onLogin(event: LoginEvent) {
        if (
            !shouldDenyInitialLogin(
                pickLobby() != null,
                proxyServer.allServers.any { server ->
                    serverRole(server.serverInfo.name) == STATIC_SERVER_ROLE
                },
                event.player.protocolVersion,
            )
        ) {
            return
        }

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

    // This must run before DrainListener: source-proxy cookie echoes belong to its stager, not to
    // initial-server selection. Velocity invokes higher priorities first.
    @Subscribe(priority = DRAIN_COOKIE_SOURCE_SUPPRESSION_PRIORITY)
    fun onCookieReceive(event: CookieReceiveEvent) {
        if (event.originalKey != DrainTransferCookie.KEY) return
        if (sourceCookiePending(event.player.uniqueId.toString())) return
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
        pickLobby()?.let(event::setInitialServer)
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
        private const val DRAIN_COOKIE_SOURCE_SUPPRESSION_PRIORITY: Short = 100
    }
}
