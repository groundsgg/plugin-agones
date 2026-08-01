package gg.grounds.discovery

import gg.grounds.discovery.LobbyPacking.Candidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LobbyPackingTest {

    @Test
    fun `packs the fullest lobby below the cap`() {
        val chosen =
            LobbyPacking.pick(
                listOf(
                    Candidate("lobby-a", 12),
                    Candidate("lobby-b", 391),
                    Candidate("lobby-c", 0),
                ),
                softCap = 400,
            )
        assertEquals("lobby-b", chosen)
    }

    @Test
    fun `a lobby at the cap stops taking joins`() {
        val chosen =
            LobbyPacking.pick(
                listOf(Candidate("lobby-a", 400), Candidate("lobby-b", 17)),
                softCap = 400,
            )
        assertEquals("lobby-b", chosen)
    }

    @Test
    fun `overflow spreads to the least occupied when everything is full`() {
        val chosen =
            LobbyPacking.pick(
                listOf(Candidate("lobby-a", 431), Candidate("lobby-b", 405)),
                softCap = 400,
            )
        assertEquals("lobby-b", chosen)
    }

    @Test
    fun `ties break on the name so every proxy packs the same lobby`() {
        val chosen =
            LobbyPacking.pick(
                listOf(Candidate("lobby-b", 50), Candidate("lobby-a", 50)),
                softCap = 400,
            )
        assertEquals("lobby-a", chosen)
    }

    @Test
    fun `no candidates means no lobby`() {
        assertNull(LobbyPacking.pick(emptyList(), softCap = 400))
    }
}
