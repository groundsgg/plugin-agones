package gg.grounds.drain

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Clock
import net.kyori.adventure.key.Key

/** A short-lived destination hint written only before an automatic proxy drain transfer. */
class DrainTransferCookie(private val clock: Clock = Clock.systemUTC()) {

    fun encode(
        serverName: String,
        expiresAtMillis: Long = clock.millis() + LIFETIME_MILLIS,
    ): ByteArray {
        val name = serverName.toByteArray(StandardCharsets.UTF_8)
        require(name.isNotEmpty() && name.size <= MAX_SERVER_NAME_BYTES) {
            "Static server name must be between 1 and $MAX_SERVER_NAME_BYTES UTF-8 bytes"
        }
        return ByteBuffer.allocate(HEADER_BYTES + name.size)
            .put(VERSION)
            .putLong(expiresAtMillis)
            .put(name)
            .array()
    }

    /** Returns null for expired, malformed, or unsupported client-controlled payloads. */
    fun decode(payload: ByteArray?): String? {
        if (payload == null || payload.size !in (HEADER_BYTES + 1)..MAX_PAYLOAD_BYTES) return null

        val bytes = ByteBuffer.wrap(payload)
        if (bytes.get() != VERSION) return null
        if (bytes.long <= clock.millis()) return null

        val name = ByteArray(bytes.remaining())
        bytes.get(name)
        val serverName = name.toString(StandardCharsets.UTF_8)
        if (
            serverName.isBlank() || serverName.toByteArray(StandardCharsets.UTF_8).size != name.size
        ) {
            return null
        }
        return serverName
    }

    companion object {
        val KEY: Key = Key.key("grounds", "drain-static-server")
        private const val VERSION: Byte = 1
        private const val HEADER_BYTES = 1 + Long.SIZE_BYTES
        private const val MAX_SERVER_NAME_BYTES = 64
        private const val MAX_PAYLOAD_BYTES = HEADER_BYTES + MAX_SERVER_NAME_BYTES
        private const val LIFETIME_MILLIS = 30_000L
    }
}
