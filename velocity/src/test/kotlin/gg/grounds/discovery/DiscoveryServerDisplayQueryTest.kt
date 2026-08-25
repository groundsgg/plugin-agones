package gg.grounds.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DiscoveryServerDisplayQueryTest {
    @Test
    fun `a known lobby becomes kind plus replica id`() {
        val query = DiscoveryServerDisplayQuery { name ->
            if (name == "lobby-nl-ams1-tr9pf-s9fwt") "lobby" else null
        }
        val display = query.displayOf("lobby-nl-ams1-tr9pf-s9fwt")!!
        assertEquals("lobby", display.kind)
        assertEquals("s9fwt", display.id)
    }

    @Test
    fun `an unknown server is omitted`() {
        val query = DiscoveryServerDisplayQuery { null }
        assertNull(query.displayOf("missing"))
    }
}
