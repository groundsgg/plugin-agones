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
        val cookie = DrainTransferCookie(clock)

        val payload = cookie.encode("buildserver")

        assertEquals("buildserver", cookie.decode(payload))
    }

    @Test
    fun `rejects a cookie at its expiry`() {
        val cookie = DrainTransferCookie(clock)
        val payload = cookie.encode("buildserver", now.toEpochMilli())

        assertNull(cookie.decode(payload))
    }

    @Test
    fun `rejects malformed client payloads`() {
        val cookie = DrainTransferCookie(clock)

        assertNull(cookie.decode(byteArrayOf(1, 2, 3)))
    }
}
