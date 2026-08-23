package gg.grounds.drain

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import net.kyori.adventure.key.Key

/** Signed, short-lived destination hint used only by an automatic proxy drain transfer. */
class DrainTransferCookie(secret: String? = null, private val clock: Clock = Clock.systemUTC()) {
    private val secret = secret?.takeIf { it.isNotBlank() }?.toByteArray(StandardCharsets.UTF_8)

    fun encode(
        serverName: String,
        expiresAtMillis: Long = clock.millis() + LIFETIME_MILLIS,
    ): ByteArray? {
        val key = secret ?: return null
        val name = serverName.toByteArray(StandardCharsets.UTF_8)
        if (name.isEmpty() || name.size > MAX_SERVER_NAME_BYTES) return null
        val issuedAtMillis = clock.millis()
        val body =
            ByteBuffer.allocate(HEADER_BYTES + name.size)
                .put(VERSION)
                .putLong(issuedAtMillis)
                .putLong(expiresAtMillis)
                .put(name)
                .array()
        return body + sign(body, key)
    }

    /** Returns null for expired, malformed, or unsupported client-controlled payloads. */
    fun decode(payload: ByteArray?): String? {
        val key = secret ?: return null
        if (payload == null || payload.size !in (HEADER_BYTES + MAC_BYTES + 1)..MAX_PAYLOAD_BYTES) {
            return null
        }
        val body = payload.copyOfRange(0, payload.size - MAC_BYTES)
        val signature = payload.copyOfRange(payload.size - MAC_BYTES, payload.size)
        if (!MessageDigest.isEqual(sign(body, key), signature)) return null

        val bytes = ByteBuffer.wrap(body)
        if (bytes.get() != VERSION) return null
        val issuedAtMillis = bytes.long
        val expiresAtMillis = bytes.long
        val now = clock.millis()
        if (
            issuedAtMillis > now + CLOCK_SKEW_MILLIS ||
                expiresAtMillis <= now ||
                expiresAtMillis > now + LIFETIME_MILLIS + CLOCK_SKEW_MILLIS ||
                expiresAtMillis <= issuedAtMillis ||
                expiresAtMillis > issuedAtMillis + LIFETIME_MILLIS + CLOCK_SKEW_MILLIS
        ) {
            return null
        }

        val name = ByteArray(bytes.remaining())
        bytes.get(name)
        val serverName = name.toString(StandardCharsets.UTF_8)
        return serverName.takeIf {
            it.isNotBlank() && it.toByteArray(StandardCharsets.UTF_8).size == name.size
        }
    }

    private fun sign(body: ByteArray, key: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, algorithm))
            doFinal(body)
        }

    companion object {
        val KEY: Key = Key.key("grounds", "drain-static-server")
        const val SECRET_ENV = "VELOCITY_FORWARDING_SECRET"
        private const val VERSION: Byte = 1
        private const val HEADER_BYTES = 1 + Long.SIZE_BYTES + Long.SIZE_BYTES
        private const val MAC_BYTES = 32
        private const val MAX_SERVER_NAME_BYTES = 64
        private const val MAX_PAYLOAD_BYTES = HEADER_BYTES + MAX_SERVER_NAME_BYTES + MAC_BYTES
        private const val LIFETIME_MILLIS = 30_000L
        private const val CLOCK_SKEW_MILLIS = 5_000L
    }
}
