package gg.grounds.discovery

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DiscoveryConfigTest {

    @Test
    fun `empty env yields prod-compatible defaults`() {
        val cfg = DiscoveryConfig.fromEnv(env = emptyMap())

        assertEquals("games", cfg.namespace)
        assertEquals("grounds/server-type in (lobby,game,match)", cfg.labelSelector)
        assertEquals("grounds/server-type", cfg.lobbyLabel)
        assertEquals("lobby", cfg.lobbyValue)
        assertEquals(setOf("Ready", "Allocated", "Reserved"), cfg.runningStates)
        assertEquals(Duration.ofSeconds(2), cfg.pollInterval)
        assertEquals("PodIP", cfg.addressType)
        assertEquals(25565, cfg.port)
        assertEquals(emptyList<StaticServer>(), cfg.staticServers)
    }

    @Test
    fun `GROUNDS_AGONES_NAMESPACE wins over POD_NAMESPACE and default`() {
        val cfg =
            DiscoveryConfig.fromEnv(
                env = mapOf("GROUNDS_AGONES_NAMESPACE" to "explicit", "POD_NAMESPACE" to "downward")
            )
        assertEquals("explicit", cfg.namespace)
    }

    @Test
    fun `POD_NAMESPACE wins over default when explicit override is absent`() {
        val cfg = DiscoveryConfig.fromEnv(env = mapOf("POD_NAMESPACE" to "from-downward"))
        assertEquals("from-downward", cfg.namespace)
    }

    @Test
    fun `empty label selector disables k8s-side filtering`() {
        val cfg = DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_LABEL_SELECTOR" to ""))
        assertEquals("", cfg.labelSelector)
    }

    @Test
    fun `empty lobby label signals role-based filter is off`() {
        val cfg = DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_LOBBY_LABEL" to ""))
        assertEquals("", cfg.lobbyLabel)
    }

    @Test
    fun `running states parses csv with whitespace`() {
        val cfg =
            DiscoveryConfig.fromEnv(
                env = mapOf("GROUNDS_AGONES_RUNNING_STATES" to "Ready, Allocated ,Scheduled")
            )
        assertEquals(setOf("Ready", "Allocated", "Scheduled"), cfg.runningStates)
    }

    @Test
    fun `running states empty string falls back to defaults`() {
        val cfg = DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_RUNNING_STATES" to ""))
        assertEquals(DiscoveryConfig.DEFAULT_RUNNING_STATES, cfg.runningStates)
    }

    @Test
    fun `poll interval accepts seconds`() {
        val cfg = DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_POLL_INTERVAL" to "5s"))
        assertEquals(Duration.ofSeconds(5), cfg.pollInterval)
    }

    @Test
    fun `poll interval accepts minutes and hours`() {
        val m = DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_POLL_INTERVAL" to "5m"))
        val h = DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_POLL_INTERVAL" to "1h"))
        assertEquals(Duration.ofMinutes(5), m.pollInterval)
        assertEquals(Duration.ofHours(1), h.pollInterval)
    }

    @Test
    fun `poll interval rejects malformed values`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_POLL_INTERVAL" to "two seconds"))
        }
    }

    @Test
    fun `port falls back to default when value is non-numeric`() {
        val cfg = DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_PORT" to "abc"))
        assertEquals(25565, cfg.port)
    }

    @Test
    fun `port honours numeric value`() {
        val cfg = DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_PORT" to "25577"))
        assertEquals(25577, cfg.port)
    }

    @Test
    fun `address type override respected`() {
        val cfg =
            DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_AGONES_ADDRESS_TYPE" to "ExternalIP"))
        assertEquals("ExternalIP", cfg.addressType)
    }

    @Test
    fun `per-dev cluster preset — empty selectors and pod-namespace`() {
        val cfg =
            DiscoveryConfig.fromEnv(
                env =
                    mapOf(
                        "POD_NAMESPACE" to "default",
                        "GROUNDS_AGONES_LABEL_SELECTOR" to "",
                        "GROUNDS_AGONES_LOBBY_LABEL" to "",
                    )
            )
        assertEquals("default", cfg.namespace)
        assertEquals("", cfg.labelSelector)
        assertEquals("", cfg.lobbyLabel)
        // Defaults preserved for the rest
        assertEquals(25565, cfg.port)
        assertEquals(Duration.ofSeconds(2), cfg.pollInterval)
    }

    @Test
    fun `static servers parse comma separated name host and port entries`() {
        assertEquals(
            listOf(
                StaticServer("buildserver", "buildserver", 25565),
                StaticServer("metrics", "metrics.stage.svc.cluster.local", 25566),
            ),
            DiscoveryConfig.fromEnv(
                    env =
                        mapOf(
                            "GROUNDS_STATIC_SERVERS" to
                                " buildserver=buildserver:25565, metrics=metrics.stage.svc.cluster.local:25566 "
                        )
                )
                .staticServers,
        )
    }

    @Test
    fun `static servers reject entries without a name address separator`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_STATIC_SERVERS" to "buildserver:25565"))
        }
    }

    @Test
    fun `static servers reject entries with an empty name`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_STATIC_SERVERS" to "=buildserver:25565"))
        }
    }

    @Test
    fun `static servers reject entries with an empty host`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryConfig.fromEnv(env = mapOf("GROUNDS_STATIC_SERVERS" to "buildserver=:25565"))
        }
    }

    @Test
    fun `static servers reject entries with port zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryConfig.fromEnv(
                env = mapOf("GROUNDS_STATIC_SERVERS" to "buildserver=buildserver:0")
            )
        }
    }

    @Test
    fun `static servers reject entries with ports above 65535`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryConfig.fromEnv(
                env = mapOf("GROUNDS_STATIC_SERVERS" to "buildserver=buildserver:65536")
            )
        }
    }

    @Test
    fun `static servers reject entries with non numeric ports`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryConfig.fromEnv(
                env = mapOf("GROUNDS_STATIC_SERVERS" to "buildserver=buildserver:abc")
            )
        }
    }

    @Test
    fun `static servers reject duplicate names`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryConfig.fromEnv(
                env =
                    mapOf(
                        "GROUNDS_STATIC_SERVERS" to
                            "buildserver=buildserver:25565,buildserver=other:25566"
                    )
            )
        }
    }
}
