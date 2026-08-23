package gg.grounds.drain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DrainTransferStagerTest {
    @Test
    fun `transfers only after the stored cookie echo is confirmed`() {
        val actions = mutableListOf<String>()
        val stager = DrainTransferStager { actions += "retry" }
        stager.stage("player", byteArrayOf(1), { actions += "store" }, { actions += "request" }) {
            actions += "transfer"
        }
        stager.onCookie("player", byteArrayOf(1))
        assertEquals(listOf("store", "request", "retry", "transfer"), actions)
    }

    @Test
    fun `mismatch retries and timeout transfers once`() {
        val actions = mutableListOf<String>()
        lateinit var timeout: () -> Unit
        val stager = DrainTransferStager { timeout = it }
        stager.stage("player", byteArrayOf(1), { actions += "store" }, { actions += "request" }) {
            actions += "transfer"
        }
        stager.onCookie("player", byteArrayOf(2))
        timeout()
        timeout()
        assertEquals(listOf("store", "request", "request", "transfer"), actions)
    }
}
