package gg.grounds.discovery

import gg.grounds.discovery.LobbySelection.Candidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LobbySelectionTest {

    @Test
    fun `joins go to the least occupied lobby`() {
        val chosen =
            LobbySelection.pick(
                listOf(Candidate("lobby-a", 12), Candidate("lobby-b", 391), Candidate("lobby-c", 3))
            )
        assertEquals("lobby-c", chosen)
    }

    @Test
    fun `a freshly autoscaled lobby is filled first because it is empty`() {
        val chosen =
            LobbySelection.pick(
                listOf(
                    Candidate("lobby-a", 150),
                    Candidate("lobby-b", 148),
                    Candidate("lobby-new", 0),
                )
            )
        assertEquals("lobby-new", chosen)
    }

    @Test
    fun `repeated picks even the lobbies out rather than filling one`() {
        val counts = mutableMapOf("lobby-a" to 4, "lobby-b" to 0, "lobby-c" to 2)
        repeat(6) {
            val chosen = LobbySelection.pick(counts.map { Candidate(it.key, it.value) })!!
            counts[chosen] = counts.getValue(chosen) + 1
        }
        assertEquals(listOf(4, 4, 4), counts.values.sorted())
    }

    @Test
    fun `ties break on the name so every proxy makes the same choice`() {
        val chosen = LobbySelection.pick(listOf(Candidate("lobby-b", 50), Candidate("lobby-a", 50)))
        assertEquals("lobby-a", chosen)
    }

    @Test
    fun `no candidates means no lobby`() {
        assertNull(LobbySelection.pick(emptyList()))
    }
}
