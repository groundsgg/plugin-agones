package gg.grounds.discovery

import java.time.Duration

/**
 * Discovery configuration sourced from environment variables. All keys are optional; the defaults
 * preserve the historic prod behaviour, so existing deployments are unaffected.
 *
 * Environment keys:
 * - `GROUNDS_AGONES_NAMESPACE` — Agones namespace to watch (falls back to `POD_NAMESPACE` set via
 *   the Downward API, then to `games`).
 * - `GROUNDS_AGONES_LABEL_SELECTOR` — Kubernetes label selector applied to the GameServer list.
 *   Empty string disables filtering (useful in per-dev / staging clusters).
 * - `GROUNDS_AGONES_LOBBY_LABEL` — Metadata label key whose value identifies a server's role. Empty
 *   string disables role-based filtering and treats every running GameServer as a lobby.
 * - `GROUNDS_AGONES_LOBBY_VALUE` — Value of [lobbyLabel] that marks a GameServer as a lobby.
 * - `GROUNDS_AGONES_RUNNING_STATES` — Comma-separated Agones states considered "running".
 * - `GROUNDS_AGONES_POLL_INTERVAL` — How often to re-list GameServers (`2s`, `5m`, `1h`).
 * - `GROUNDS_AGONES_ADDRESS_TYPE` — Which `status.addresses` entry to use (`PodIP`, `ExternalIP`,
 *   `InternalIP`, `Hostname`).
 * - `GROUNDS_AGONES_PORT` — TCP port to dial on the discovered GameServer.
 * - `GROUNDS_AGONES_LOBBY_SOFT_CAP` — Players a lobby is packed up to before joins go to the next
 *   one. Soft: a snapshot-raced join over the cap is fine.
 */
data class DiscoveryConfig(
    val namespace: String,
    val labelSelector: String,
    val lobbyLabel: String,
    val lobbyValue: String,
    val runningStates: Set<String>,
    val pollInterval: Duration,
    val addressType: String,
    val port: Int,
    val lobbySoftCap: Int,
) {
    companion object {
        const val DEFAULT_NAMESPACE = "games"
        const val DEFAULT_LABEL_SELECTOR = "grounds/server-type in (lobby,game,match)"
        const val DEFAULT_LOBBY_LABEL = "grounds/server-type"
        const val DEFAULT_LOBBY_VALUE = "lobby"
        val DEFAULT_RUNNING_STATES = setOf("Ready", "Allocated", "Reserved")
        val DEFAULT_POLL_INTERVAL: Duration = Duration.ofSeconds(2)
        const val DEFAULT_ADDRESS_TYPE = "PodIP"
        const val DEFAULT_PORT = 25565
        const val DEFAULT_LOBBY_SOFT_CAP = 400

        fun fromEnv(env: Map<String, String> = System.getenv()): DiscoveryConfig =
            DiscoveryConfig(
                namespace =
                    env["GROUNDS_AGONES_NAMESPACE"] ?: env["POD_NAMESPACE"] ?: DEFAULT_NAMESPACE,
                labelSelector = env["GROUNDS_AGONES_LABEL_SELECTOR"] ?: DEFAULT_LABEL_SELECTOR,
                lobbyLabel = env["GROUNDS_AGONES_LOBBY_LABEL"] ?: DEFAULT_LOBBY_LABEL,
                lobbyValue = env["GROUNDS_AGONES_LOBBY_VALUE"] ?: DEFAULT_LOBBY_VALUE,
                runningStates =
                    env["GROUNDS_AGONES_RUNNING_STATES"]
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.toSet()
                        ?.takeIf { it.isNotEmpty() } ?: DEFAULT_RUNNING_STATES,
                pollInterval =
                    env["GROUNDS_AGONES_POLL_INTERVAL"]?.let(::parseDuration)
                        ?: DEFAULT_POLL_INTERVAL,
                addressType = env["GROUNDS_AGONES_ADDRESS_TYPE"] ?: DEFAULT_ADDRESS_TYPE,
                port = env["GROUNDS_AGONES_PORT"]?.toIntOrNull() ?: DEFAULT_PORT,
                lobbySoftCap =
                    env["GROUNDS_AGONES_LOBBY_SOFT_CAP"]?.toIntOrNull()?.takeIf { it > 0 }
                        ?: DEFAULT_LOBBY_SOFT_CAP,
            )

        private val DURATION_PATTERN = Regex("""^(\d+)\s*(s|m|h)$""")

        private fun parseDuration(raw: String): Duration {
            val match =
                DURATION_PATTERN.matchEntire(raw.trim())
                    ?: throw IllegalArgumentException(
                        "GROUNDS_AGONES_POLL_INTERVAL '$raw' must look like '2s', '5m', or '1h'"
                    )
            val n = match.groupValues[1].toLong()
            return when (match.groupValues[2]) {
                "s" -> Duration.ofSeconds(n)
                "m" -> Duration.ofMinutes(n)
                "h" -> Duration.ofHours(n)
                else -> error("unreachable")
            }
        }
    }
}
