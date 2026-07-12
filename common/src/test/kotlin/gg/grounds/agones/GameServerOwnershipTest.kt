package gg.grounds.agones

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameServerOwnershipTest {

    private fun env(value: String?) =
        GameServerOwnership.fromEnvironment { name ->
            if (name == GameServerOwnership.ENV_VAR) value else null
        }

    @Test
    fun `unset means the server manages itself`() {
        // The overwhelmingly common case: lobbies and standalone gamemodes,
        // where "no players" really does mean "free to hand out".
        assertEquals(GameServerOwnership.SELF_MANAGED, env(null))
        assertFalse(env(null).isMatchmakerManaged)
    }

    @Test
    fun `1 and true both hand ownership to the matchmaker`() {
        assertTrue(env("1").isMatchmakerManaged)
        assertTrue(env("true").isMatchmakerManaged)
        assertTrue(env("TRUE").isMatchmakerManaged)
        assertTrue(env(" 1 ").isMatchmakerManaged)
    }

    @Test
    fun `anything else stays self-managed`() {
        // Fail safe, not open: an unparseable value must not silently disable
        // the readiness loop on an ordinary lobby, which would leave it stuck
        // outside the Ready pool and unjoinable.
        assertFalse(env("0").isMatchmakerManaged)
        assertFalse(env("false").isMatchmakerManaged)
        assertFalse(env("").isMatchmakerManaged)
        assertFalse(env("yes").isMatchmakerManaged)
    }
}
