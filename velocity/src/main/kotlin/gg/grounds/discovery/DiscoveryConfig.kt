package gg.grounds.discovery

import java.time.Duration
import java.util.Locale

internal fun canonicalServerName(name: String): String = name.lowercase(Locale.ROOT)

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
 * - `GROUNDS_STATIC_SERVERS` — Comma-separated `name=host:port` Velocity backends that are
 *   registered without Agones discovery.
 */
data class StaticServer(val name: String, val host: String, val port: Int)

data class DiscoveryConfig(
    val namespace: String,
    val labelSelector: String,
    val lobbyLabel: String,
    val lobbyValue: String,
    val runningStates: Set<String>,
    val pollInterval: Duration,
    val addressType: String,
    val port: Int,
    val staticServers: List<StaticServer>,
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
                staticServers = parseStaticServers(env["GROUNDS_STATIC_SERVERS"]),
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

        private fun parseStaticServers(raw: String?): List<StaticServer> {
            if (raw.isNullOrBlank()) return emptyList()

            val names = mutableSetOf<String>()
            return raw.split(",").map { entry ->
                val trimmedEntry = entry.trim()
                val separator = trimmedEntry.indexOf('=')
                require(separator >= 0) { "Invalid GROUNDS_STATIC_SERVERS entry '$trimmedEntry'" }

                val name = trimmedEntry.substring(0, separator).trim()
                val address = trimmedEntry.substring(separator + 1).trim()
                val portSeparator = address.lastIndexOf(':')
                require(name.isNotEmpty() && portSeparator >= 0) {
                    "Invalid GROUNDS_STATIC_SERVERS entry '$trimmedEntry'"
                }

                val host = address.substring(0, portSeparator).trim()
                val port = address.substring(portSeparator + 1).trim().toIntOrNull()
                require(host.isNotEmpty() && port != null && port in 1..65535) {
                    "Invalid GROUNDS_STATIC_SERVERS entry '$trimmedEntry'"
                }
                require(names.add(canonicalServerName(name))) {
                    "Invalid GROUNDS_STATIC_SERVERS entry '$trimmedEntry': duplicate name '$name'"
                }

                StaticServer(name, host, port)
            }
        }
    }
}
