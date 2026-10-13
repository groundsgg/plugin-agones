package gg.grounds.drain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PacedTransfersTest {
    @Test
    fun `a full proxy is transferred in batches, not in one burst`() {
        val sent = mutableListOf<Int>()
        val paced = PacedTransfers((1..45).toList(), 20, { true }, { sent += it }, { _, _ -> })

        assertFalse(paced.tick())
        assertEquals(20, sent.size)
        assertFalse(paced.tick())
        assertEquals(40, sent.size)
        assertTrue(paced.tick())
        assertEquals((1..45).toList(), sent)
    }

    @Test
    fun `a few players go out at once`() {
        val sent = mutableListOf<String>()
        val paced = PacedTransfers(listOf("a", "b"), 20, { true }, { sent += it }, { _, _ -> })

        assertTrue(paced.tick())
        assertEquals(listOf("a", "b"), sent)
    }

    @Test
    fun `players who left or entered a round while queued are skipped without using a slot`() {
        val gone = setOf(2, 3)
        val sent = mutableListOf<Int>()
        val paced = PacedTransfers((1..6).toList(), 2, { it !in gone }, { sent += it }, { _, _ -> })

        paced.tick()
        assertEquals(listOf(1, 4), sent)
        assertTrue(paced.tick())
        assertEquals(listOf(1, 4, 5, 6), sent)
    }

    @Test
    fun `a failed transfer does not stop later drain transfers`() {
        val attempted = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val paced =
            PacedTransfers(
                listOf("first", "second"),
                20,
                { true },
                { player ->
                    attempted += player
                    if (player == "first") error("connection closed")
                },
            ) { player, _ ->
                failures += player
            }

        assertTrue(paced.tick())
        assertEquals(listOf("first", "second"), attempted)
        assertEquals(listOf("first"), failures)
    }

    @Test
    fun `the pace defaults to 20 per second and can be set`() {
        assertEquals(20, DrainConfig.fromEnv(emptyMap()).transfersPerSecond)
        assertEquals(
            50,
            DrainConfig.fromEnv(mapOf("GROUNDS_DRAIN_TRANSFERS_PER_SECOND" to "50"))
                .transfersPerSecond,
        )
        assertThrows<IllegalArgumentException> {
            DrainConfig.fromEnv(mapOf("GROUNDS_DRAIN_TRANSFERS_PER_SECOND" to "0"))
        }
    }
}
