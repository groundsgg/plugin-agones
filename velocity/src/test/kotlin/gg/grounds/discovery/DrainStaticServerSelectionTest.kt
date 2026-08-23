package gg.grounds.discovery

import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DrainStaticServerSelectionTest {

    @Test
    fun `selects a registered static server named by the drain cookie`() {
        val buildserver = registeredServer("buildserver")

        val selected =
            selectDrainStaticServer("buildserver", listOf(buildserver)) { name ->
                if (name == "buildserver") "static" else null
            }

        assertSame(buildserver, selected)
    }

    @Test
    fun `matches a static server name from the drain cookie case insensitively`() {
        val buildserver = registeredServer("BuildServer")

        val selected = selectDrainStaticServer("buildserver", listOf(buildserver)) { "static" }

        assertSame(buildserver, selected)
    }

    @Test
    fun `does not select an Agones round server named by the drain cookie`() {
        val game = registeredServer("game-7")

        val selected = selectDrainStaticServer("game-7", listOf(game)) { "game" }

        assertNull(selected)
    }

    @Test
    fun `allows a cookie-capable login to reach static selection without a lobby`() {
        assertFalse(
            shouldDenyInitialLogin(
                hasLobby = false,
                hasStatic = true,
                protocolVersion = ProtocolVersion.MINECRAFT_1_20_5,
            )
        )
    }

    private fun registeredServer(name: String): RegisteredServer =
        Proxy.newProxyInstance(
            RegisteredServer::class.java.classLoader,
            arrayOf(RegisteredServer::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getServerInfo" ->
                    ServerInfo(name, InetSocketAddress.createUnresolved("$name.internal", 25565))
                else -> null
            }
        } as RegisteredServer
}
