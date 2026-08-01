package gg.grounds.drain

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import org.slf4j.Logger

/**
 * The loopback endpoint the pod's preStop hook talks to. Loopback on purpose: the only caller is
 * `sh` inside the same container, and a drain trigger reachable from the cluster network would be a
 * kick-everyone button.
 * - `GET/POST /drain/start?deadlineSeconds=N` — begin draining; idempotent.
 * - `GET /drain/players` — plain-text player count; the hook polls this until it reads `0`.
 * - `GET /drain/status` — the same, for humans: `{"draining":bool,"players":N}`.
 */
class DrainHttpServer(
    private val drainManager: DrainManager,
    private val port: Int,
    private val logger: Logger,
) {
    private var server: HttpServer? = null

    fun start() {
        if (port == 0) {
            logger.info("Drain HTTP endpoint disabled (GROUNDS_DRAIN_HTTP_PORT=0)")
            return
        }
        val httpServer =
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
        httpServer.createContext("/drain/start") { exchange ->
            val deadline = deadlineSeconds(exchange.requestURI.query)
            val started = drainManager.start(deadline)
            respond(exchange, 200, if (started) "started" else "already-draining")
        }
        httpServer.createContext("/drain/players") { exchange ->
            respond(exchange, 200, drainManager.playersRemaining().toString())
        }
        httpServer.createContext("/drain/status") { exchange ->
            respond(
                exchange,
                200,
                """{"draining":${drainManager.isDraining},"players":${drainManager.playersRemaining()}}""",
            )
        }
        httpServer.start()
        server = httpServer
        logger.info("Drain HTTP endpoint listening (port={})", port)
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.use {
            it.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            it.sendResponseHeaders(status, bytes.size.toLong())
            it.responseBody.write(bytes)
        }
    }

    companion object {
        const val DEFAULT_DEADLINE_SECONDS = 600L

        /**
         * Clamped rather than rejected: the caller is a shell one-liner, not a client we argue
         * with.
         */
        internal fun deadlineSeconds(query: String?): Long {
            val raw =
                query
                    ?.split('&')
                    ?.firstOrNull { it.startsWith("deadlineSeconds=") }
                    ?.substringAfter('=')
                    ?.toLongOrNull() ?: return DEFAULT_DEADLINE_SECONDS
            return raw.coerceIn(10L, 86_400L)
        }
    }
}
