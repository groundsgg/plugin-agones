package gg.grounds.drain

import gg.grounds.proxy.api.PlayerSessionTransfer
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DrainHandoffTest {
    private val player = UUID.randomUUID()
    private val calls = mutableListOf<String>()

    private fun drain(session: PlayerSessionTransfer?) =
        transferWithHandoff(
            session,
            player,
            "eu.geo.grnds.io",
            25565,
            direct = { calls += "direct" },
            refuse = { calls += "refuse" },
        )

    @Test
    fun `the session handoff carries the player when plugin-player is installed`() {
        drain { id, host, port ->
            calls += "handoff $id $host:$port"
            true
        }
        assertEquals(listOf("handoff $player eu.geo.grnds.io:25565"), calls)
    }

    @Test
    fun `a handoff that cannot be issued refuses instead of sending a bare transfer`() {
        drain { _, _, _ -> false }
        assertEquals(listOf("refuse"), calls)
    }

    @Test
    fun `without plugin-player the bare transfer is used`() {
        drain(null)
        assertEquals(listOf("direct"), calls)
    }
}
