package gg.grounds.discovery

import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
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
    fun `does not select an Agones round server named by the drain cookie`() {
        val game = registeredServer("game-7")

        val selected = selectDrainStaticServer("game-7", listOf(game)) { "game" }

        assertNull(selected)
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
