package gg.grounds.drain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DrainTransferSafetyTest {
    @Test
    fun `a failed transfer does not stop later drain transfers`() {
        val attempted = mutableListOf<String>()
        val failures = mutableListOf<String>()

        transferAllSafely(
            listOf("first", "second"),
            { player ->
                attempted += player
                if (player == "first") error("connection closed")
            },
        ) { player, _ ->
            failures += player
        }

        assertEquals(listOf("first", "second"), attempted)
        assertEquals(listOf("first"), failures)
    }
}
