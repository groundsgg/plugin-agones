package gg.grounds.drain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DrainConfigTest {

    @Test
    fun `empty env yields wait-only drain with the default endpoint port`() {
        val cfg = DrainConfig.fromEnv(env = emptyMap())

        assertNull(cfg.transferHost)
        assertEquals(DrainConfig.DEFAULT_TRANSFER_PORT, cfg.transferPort)
        assertEquals(DrainConfig.DEFAULT_HTTP_PORT, cfg.httpPort)
    }

    @Test
    fun `transfer host without port gets the minecraft default`() {
        val cfg =
            DrainConfig.fromEnv(env = mapOf("GROUNDS_DRAIN_TRANSFER_HOST" to "eu.geo.grnds.io"))

        assertEquals("eu.geo.grnds.io", cfg.transferHost)
        assertEquals(25565, cfg.transferPort)
    }

    @Test
    fun `transfer host with port keeps it`() {
        val cfg =
            DrainConfig.fromEnv(
                env = mapOf("GROUNDS_DRAIN_TRANSFER_HOST" to "eu.geo.grnds.io:25566")
            )

        assertEquals("eu.geo.grnds.io", cfg.transferHost)
        assertEquals(25566, cfg.transferPort)
    }

    @Test
    fun `blank transfer host means wait-only`() {
        val cfg = DrainConfig.fromEnv(env = mapOf("GROUNDS_DRAIN_TRANSFER_HOST" to "  "))
        assertNull(cfg.transferHost)
    }

    @Test
    fun `bad transfer port fails loud instead of draining into nowhere`() {
        assertThrows(IllegalArgumentException::class.java) {
            DrainConfig.fromEnv(env = mapOf("GROUNDS_DRAIN_TRANSFER_HOST" to "host:notaport"))
        }
    }

    @Test
    fun `http port zero disables the endpoint`() {
        val cfg = DrainConfig.fromEnv(env = mapOf("GROUNDS_DRAIN_HTTP_PORT" to "0"))
        assertEquals(0, cfg.httpPort)
    }

    @Test
    fun `bad http port fails loud`() {
        assertThrows(IllegalArgumentException::class.java) {
            DrainConfig.fromEnv(env = mapOf("GROUNDS_DRAIN_HTTP_PORT" to "eighty"))
        }
    }
}
