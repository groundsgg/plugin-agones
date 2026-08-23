package gg.grounds.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerOwnershipTest {

    @Test
    fun `only missing Agones owned servers become stale`() {
        assertEquals(
            setOf("old-game"),
            staleManagedServerNames(setOf("live-game"), setOf("live-game", "old-game")),
        )
    }

    @Test
    fun `an unowned static server cannot enter the removal result`() {
        assertEquals(emptySet<String>(), staleManagedServerNames(emptySet(), emptySet()))
    }
}
