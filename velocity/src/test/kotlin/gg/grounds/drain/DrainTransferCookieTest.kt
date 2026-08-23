package gg.grounds.drain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DrainTransferCookieTest {

    private val now = Instant.parse("2026-08-23T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `round trips a static server name before its expiry`() {
        val cookie = DrainTransferCookie("secret", clock)

        val payload = cookie.encode("buildserver")

        assertEquals("buildserver", cookie.decode(payload))
    }

    @Test
    fun `rejects a cookie at its expiry`() {
        val cookie = DrainTransferCookie("secret", clock)
        val payload = cookie.encode("buildserver", now.toEpochMilli())

        assertNull(cookie.decode(payload))
    }

    @Test
    fun `rejects malformed client payloads`() {
        val cookie = DrainTransferCookie("secret", clock)

        assertNull(cookie.decode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `rejects a cookie signed with another secret`() {
        val payload = DrainTransferCookie("source-secret", clock).encode("buildserver")

        assertNull(DrainTransferCookie("target-secret", clock).decode(payload))
    }

    @Test
    fun `rejects a forged cookie signature`() {
        val cookie = DrainTransferCookie("secret", clock)
        val payload = cookie.encode("buildserver")!!
        payload[10] = (payload[10].toInt() xor 1).toByte()

        assertNull(cookie.decode(payload))
    }

    @Test
    fun `rejects an expiry beyond the allowed lifetime`() {
        val cookie = DrainTransferCookie("secret", clock)
        val payload = cookie.encode("buildserver", now.plusSeconds(600).toEpochMilli())

        assertNull(cookie.decode(payload))
    }
}
