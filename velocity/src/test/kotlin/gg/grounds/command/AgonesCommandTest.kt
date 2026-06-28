package gg.grounds.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.ProxyServer
import java.lang.reflect.Proxy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgonesCommandTest {

    @Test
    fun `allows console to run agones command`() {
        val command = AgonesCommand(proxyServer(), getServerRole = { null })

        assertTrue(command.hasPermission(invocation(consoleSource())))
    }

    @Test
    fun `allows command source with grounds command namespace permission`() {
        val command = AgonesCommand(proxyServer(), getServerRole = { null })

        assertTrue(command.hasPermission(invocation(commandSource("grounds.command.agones"))))
    }

    @Test
    fun `rejects command source without agones command permission`() {
        val command = AgonesCommand(proxyServer(), getServerRole = { null })

        assertFalse(command.hasPermission(invocation(commandSource())))
    }

    private fun proxyServer(): ProxyServer = proxy()

    private fun invocation(source: CommandSource): SimpleCommand.Invocation =
        proxy(mapOf("source" to source, "arguments" to emptyArray<String>(), "alias" to "agones"))

    private fun commandSource(vararg permissions: String): CommandSource =
        proxy(
            mapOf(
                "hasPermission" to { args: Array<out Any?> -> args.single() in permissions },
                "getPermissionValue" to
                    { args: Array<out Any?> ->
                        Tristate.fromBoolean(args.single() in permissions)
                    },
            )
        )

    private fun consoleSource(): ConsoleCommandSource = proxy()

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
                method.returnType == Double::class.javaPrimitiveType -> 0.0
                method.returnType == Float::class.javaPrimitiveType -> 0.0f
                method.returnType == Void.TYPE -> null
                else -> null
            }
        } as T
}
