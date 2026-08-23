package gg.grounds.discovery

import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class DiscoveryServiceTest {

    @Test
    fun `startup removes only the exact baked image placeholders`() {
        val removed = mutableListOf<ServerInfo>()
        val placeholder = registeredServer("lobby", "127.0.0.1", 30066)
        val sameNameDifferentAddress = registeredServer("lobby", "10.0.0.10", 25565)
        val external = registeredServer("external", "10.0.0.11", 25565)
        val service =
            DiscoveryService(
                plugin = Any(),
                proxyServer =
                    proxyServer(listOf(placeholder, sameNameDifferentAddress, external), removed),
                logger = LoggerFactory.getLogger(javaClass),
                config = DiscoveryConfig.fromEnv(emptyMap()),
                kubernetesClientFactory = { null },
            )

        service.start()

        assertEquals(listOf(placeholder.serverInfo), removed)
    }

    @Test
    fun `static servers register when Kubernetes initialization fails`() {
        val registrations = mutableListOf<ServerInfo>()
        val service =
            DiscoveryService(
                plugin = Any(),
                proxyServer = proxyServer(emptyList(), registrations = registrations),
                logger = LoggerFactory.getLogger(javaClass),
                config =
                    DiscoveryConfig.fromEnv(
                        mapOf("GROUNDS_STATIC_SERVERS" to "buildserver=buildserver:25565")
                    ),
                kubernetesClientFactory = { null },
            )

        service.start()

        assertEquals(
            listOf(
                ServerInfo("buildserver", InetSocketAddress.createUnresolved("buildserver", 25565))
            ),
            registrations,
        )
        assertEquals("static", service.getServerRole("buildserver"))
    }

    @Test
    fun `replacement of an Agones registration is never unregistered as owned`() {
        val agonesRegistration = registeredServer("game", "10.0.0.1", 25565)
        val externalReplacement = registeredServer("game", "10.0.0.2", 25565)
        val removed = mutableListOf<ServerInfo>()
        val service =
            DiscoveryService(
                plugin = Any(),
                proxyServer =
                    proxyServer(
                        emptyList(),
                        removed,
                        registrations = mutableListOf(),
                        registered = agonesRegistration,
                    ),
                logger = LoggerFactory.getLogger(javaClass),
                config = DiscoveryConfig.fromEnv(emptyMap()),
                kubernetesClientFactory = { null },
            )
        val gameServer =
            GameServer(
                metadata =
                    Metadata(name = "game", labels = mapOf("grounds/server-type" to "lobby")),
                status =
                    Status(
                        state = "Ready",
                        addresses = listOf(GameServerAddress("10.0.0.1", "PodIP")),
                    ),
            )

        service.registerRunningServers(listOf(gameServer), emptyMap())
        service.registerRunningServers(
            listOf(
                gameServer.copy(
                    metadata =
                        Metadata(name = "game", labels = mapOf("grounds/server-type" to "game"))
                )
            ),
            mapOf("game" to externalReplacement),
        )
        assertEquals(null, service.getServerRole("game"))
        service.unregisterServersThatAreNoLongerRunning(
            emptyList(),
            mapOf("game" to externalReplacement),
        )

        assertTrue(removed.isEmpty())
    }

    private fun proxyServer(
        servers: List<RegisteredServer>,
        removed: MutableList<ServerInfo> = mutableListOf(),
        registrations: MutableList<ServerInfo> = mutableListOf(),
        registered: RegisteredServer? = null,
    ): ProxyServer =
        proxy(
            mapOf(
                "getAllServers" to servers,
                "getEventManager" to proxy<EventManager>(),
                "unregisterServer" to
                    { args: Array<out Any?> ->
                        removed.add(args.single() as ServerInfo)
                    },
                "registerServer" to
                    { args: Array<out Any?> ->
                        val serverInfo = args.single() as ServerInfo
                        registrations.add(serverInfo)
                        registered
                            ?: registeredServer(
                                serverInfo.name,
                                serverInfo.address.hostString,
                                serverInfo.address.port,
                            )
                    },
            )
        )

    private fun registeredServer(name: String, host: String, port: Int): RegisteredServer =
        proxy(
            mapOf(
                "getServerInfo" to ServerInfo(name, InetSocketAddress.createUnresolved(host, port))
            )
        )

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
                method.returnType == Long::class.javaPrimitiveType -> 0L
                method.returnType == Void.TYPE -> null
                else -> null
            }
        } as T
}
