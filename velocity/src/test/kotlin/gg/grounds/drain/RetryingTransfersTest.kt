package gg.grounds.drain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetryingTransfersTest {
    @Test
    fun `full shared budget queues once and retries without minting another ticket`() {
        var available = false
        val sent = mutableListOf<Int>()
        val queue =
            RetryingTransfers<Int, Int>(
                2,
                { it },
                { true },
                {
                    if (available) {
                        sent += it
                        true
                    } else false
                },
            )
        queue.offer(1)
        queue.offer(1)
        queue.offer(2)
        assertFalse(queue.tick())
        assertEquals(2, queue.remaining)
        assertTrue(sent.isEmpty())
        available = true
        assertTrue(queue.tick())
        assertEquals(listOf(1, 2), sent)
    }

    @Test
    fun `one tick never spins indefinitely on a refused transfer`() {
        var attempts = 0
        val queue =
            RetryingTransfers<Int, Int>(
                20,
                { it },
                { true },
                {
                    attempts++
                    false
                },
            )
        queue.offer(1)
        assertFalse(queue.tick())
        assertEquals(1, attempts)
    }

    @Test
    fun `left players and newly protected rounds are removed without sending`() {
        val queue = RetryingTransfers<Int, Int>(2, { it }, { false }, { error("not due") })
        queue.offer(1)
        assertTrue(queue.tick())
    }
}
