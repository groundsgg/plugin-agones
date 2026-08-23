package gg.grounds.discovery

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DrainTransferCookieConsumptionTest {

    @Test
    fun `attempts to clear a received drain cookie even when the client clear fails`() {
        var attempts = 0

        assertDoesNotThrow {
            consumeDrainTransferCookie {
                attempts++
                throw IllegalStateException("client disconnected")
            }
        }

        assertEquals(1, attempts)
    }
}
