package gg.grounds.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `an unowned current Velocity registration does not receive an Agones role`() {
        assertFalse(
            shouldApplyAgonesState(
                serverName = "external-server",
                currentServerNames = setOf("external-server"),
                agonesManagedServerNames = emptySet(),
            )
        )
        assertTrue(
            shouldApplyAgonesState(
                serverName = "agones-server",
                currentServerNames = setOf("agones-server"),
                agonesManagedServerNames = setOf("agones-server"),
            )
        )
    }
}
