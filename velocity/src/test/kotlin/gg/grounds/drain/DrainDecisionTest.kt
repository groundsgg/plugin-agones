package gg.grounds.drain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrainDecisionTest {

    @Test
    fun `lobby players are transferred right away`() {
        assertFalse(DrainManager.shouldDefer(role = "lobby", lobbyValue = "lobby"))
    }

    @Test
    fun `players inside a round are deferred`() {
        assertTrue(DrainManager.shouldDefer(role = "game", lobbyValue = "lobby"))
        assertTrue(DrainManager.shouldDefer(role = "match", lobbyValue = "lobby"))
    }

    @Test
    fun `no server or unknown role is nothing to protect`() {
        assertFalse(DrainManager.shouldDefer(role = null, lobbyValue = "lobby"))
    }

    @Test
    fun `deadline defaults when the query is absent or unreadable`() {
        assertEquals(
            DrainHttpServer.DEFAULT_DEADLINE_SECONDS,
            DrainHttpServer.deadlineSeconds(null),
        )
        assertEquals(DrainHttpServer.DEFAULT_DEADLINE_SECONDS, DrainHttpServer.deadlineSeconds(""))
        assertEquals(
            DrainHttpServer.DEFAULT_DEADLINE_SECONDS,
            DrainHttpServer.deadlineSeconds("deadlineSeconds=soon"),
        )
    }

    @Test
    fun `deadline reads and clamps the query parameter`() {
        assertEquals(840L, DrainHttpServer.deadlineSeconds("deadlineSeconds=840"))
        assertEquals(10L, DrainHttpServer.deadlineSeconds("deadlineSeconds=1"))
        assertEquals(86_400L, DrainHttpServer.deadlineSeconds("deadlineSeconds=999999999"))
        assertEquals(120L, DrainHttpServer.deadlineSeconds("foo=bar&deadlineSeconds=120"))
    }
}
