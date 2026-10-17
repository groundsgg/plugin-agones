package gg.grounds.discovery

import gg.grounds.discovery.LobbySelection.Candidate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LobbyPlacementTest {

    @Test
    fun `concurrent joins never put more than the cap on one lobby`() {
        val placement = LobbyPlacement(cap = 100)
        // The observed counts do not move: none of these players has arrived yet, so only the
        // reservations stand between the burst and one lobby of 150.
        val observed = listOf(Candidate("lobby-a", 0), Candidate("lobby-b", 0))
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        try {
            val picks =
                (1..150).map {
                    pool.submit(
                        Callable {
                            start.await()
                            placement.place(observed, UUID.randomUUID())
                        }
                    )
                }
            start.countDown()
            val counts = picks.map { it.get(10, TimeUnit.SECONDS) }.groupingBy { it }.eachCount()

            assertEquals(mapOf("lobby-a" to 100, "lobby-b" to 50), counts)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `reservations top up a lobby that already has players`() {
        val placement = LobbyPlacement(cap = 100)
        val observed = listOf(Candidate("lobby-a", 97), Candidate("lobby-b", 0))

        val picks = (1..5).map { placement.place(observed, UUID.randomUUID()) }

        assertEquals(List(3) { "lobby-a" } + List(2) { "lobby-b" }, picks)
    }

    @Test
    fun `a reservation counts until the player arrives`() {
        val placement = LobbyPlacement(cap = 100)
        val player = UUID.randomUUID()
        val observed = listOf(Candidate("lobby-a", 10))

        placement.place(observed, player)
        assertEquals(11, placement.withReservations(observed).single().players)

        // Arrived: Velocity now counts them, so the reservation must not count them twice.
        placement.release(player)
        assertEquals(10, placement.withReservations(observed).single().players)
    }

    @Test
    fun `a reservation whose join never completes expires`() {
        var now = 0L
        val placement = LobbyPlacement(cap = 100, ttl = Duration.ofSeconds(15), clock = { now })
        val observed = listOf(Candidate("lobby-a", 10))

        placement.place(observed, UUID.randomUUID())
        now += Duration.ofSeconds(16).toNanos()

        assertEquals(10, placement.withReservations(observed).single().players)
    }

    @Test
    fun `the excluded lobby is never chosen even when it is the fullest`() {
        val placement = LobbyPlacement(cap = 100)
        val observed = listOf(Candidate("lobby-a", 50), Candidate("lobby-b", 10))

        assertEquals("lobby-b", placement.place(observed, UUID.randomUUID(), exclude = "LOBBY-A"))
    }

    @Test
    fun `a failed join placed again moves its reservation instead of keeping both`() {
        val placement = LobbyPlacement(cap = 100)
        val player = UUID.randomUUID()
        val observed = listOf(Candidate("lobby-a", 50), Candidate("lobby-b", 10))

        assertEquals("lobby-a", placement.place(observed, player))
        // lobby-a refused the connection; the kick listener places the player elsewhere.
        assertEquals("lobby-b", placement.place(observed, player, exclude = "lobby-a"))

        assertEquals(
            mapOf("lobby-a" to 50, "lobby-b" to 11),
            placement.withReservations(observed).associate { it.name to it.players },
        )
    }

    @Test
    fun `asking without a player reserves nothing`() {
        val placement = LobbyPlacement(cap = 100)
        val observed = listOf(Candidate("lobby-a", 10))

        assertEquals("lobby-a", placement.place(observed))
        assertEquals(10, placement.withReservations(observed).single().players)
    }

    @Test
    fun `the preferred lobby wins while it has room`() {
        val placement = LobbyPlacement(cap = 100)
        val observed = listOf(Candidate("lobby-home", 10), Candidate("lobby-full", 90))

        assertEquals(
            "lobby-home",
            placement.place(observed, UUID.randomUUID(), preferred = "lobby-home"),
        )
        assertEquals(11, placement.withReservations(observed).first().players)
    }

    @Test
    fun `a preferred lobby at the cap counting reservations falls back to the fill rule`() {
        val placement = LobbyPlacement(cap = 100)
        val observed = listOf(Candidate("lobby-home", 99), Candidate("lobby-b", 40))

        assertEquals(
            "lobby-home",
            placement.place(observed, UUID.randomUUID(), preferred = "lobby-home"),
        )
        // The reservation above fills it: the next returning player goes to the fullest with room.
        assertEquals(
            "lobby-b",
            placement.place(observed, UUID.randomUUID(), preferred = "lobby-home"),
        )
    }

    @Test
    fun `a preferred lobby that is gone, outdated or excluded is ignored`() {
        val placement = LobbyPlacement(cap = 100)
        val observed = listOf(Candidate("lobby-old", 5, outdated = true), Candidate("lobby-b", 40))

        assertEquals("lobby-b", placement.place(observed, preferred = "lobby-gone"))
        assertEquals("lobby-b", placement.place(observed, preferred = "lobby-old"))
        assertEquals(
            "lobby-b",
            placement.place(
                listOf(Candidate("lobby-home", 5), Candidate("lobby-b", 40)),
                exclude = "lobby-home",
                preferred = "lobby-home",
            ),
        )
    }
}
