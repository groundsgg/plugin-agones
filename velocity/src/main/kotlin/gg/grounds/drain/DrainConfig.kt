package gg.grounds.drain

/**
 * Drain configuration sourced from environment variables. All keys are optional; with none set the
 * HTTP endpoint still comes up (loopback only) and a drain degrades to "wait for players to leave",
 * which is still strictly better than the kick it replaces.
 *
 * Environment keys:
 * - `GROUNDS_DRAIN_TRANSFER_HOST` — `host[:port]` the Minecraft transfer packet sends players to.
 *   This is the *public* name the client reconnects through (mc-router resolves it to whichever
 *   proxy is alive), not a backend address. Optional: when unset, the target is this proxy's own
 *   entry in `REGIONS` (the `code=host[:port]` catalogue `/region` already transfers players with)
 *   — the same values file then works in every region. With neither set, players are never
 *   transferred, only waited for.
 *
 * The catalogue is keyed by continent (`eu=eu.geo.grnds.io:…`), not by datacentre, so the own entry
 * is looked up by `CONTINENT` first and by `REGION` (`de-nbg1`) only as a fallback for a catalogue
 * keyed by datacentre.
 * - `GROUNDS_DRAIN_TRANSFERS` — `false` makes the drain wait-only whatever the target settings say.
 *   For the Bedrock proxy: there is no other proxy a Geyser session could be transferred to, and a
 *   Java transfer target in `REGIONS` would send it somewhere it cannot connect.
 * - `GROUNDS_DRAIN_HTTP_PORT` — loopback port the preStop hook calls. `0` disables the endpoint.
 * - `GROUNDS_DRAIN_TRANSFERS_PER_SECOND` — how many players a drain transfers per second (default
 *   20). A full proxy then reconnects over seconds instead of in one burst of logins.
 */
data class DrainConfig(
    val transferHost: String?,
    val transferPort: Int,
    val httpPort: Int,
    val transfersPerSecond: Int = DEFAULT_TRANSFERS_PER_SECOND,
) {

    companion object {
        const val DEFAULT_TRANSFER_PORT = 25565
        const val DEFAULT_HTTP_PORT = 8085
        const val DEFAULT_TRANSFERS_PER_SECOND = 20

        fun fromEnv(env: Map<String, String> = System.getenv()): DrainConfig {
            val transfers =
                env["GROUNDS_DRAIN_TRANSFERS"]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        it.toBooleanStrictOrNull()
                            ?: throw IllegalArgumentException(
                                "GROUNDS_DRAIN_TRANSFERS '$it' must be true or false"
                            )
                    } ?: true
            val rawTarget =
                if (!transfers) null
                else
                    env["GROUNDS_DRAIN_TRANSFER_HOST"]?.trim()?.takeIf { it.isNotEmpty() }
                        ?: ownRegionTarget(env["REGIONS"], env["CONTINENT"])
                        ?: ownRegionTarget(env["REGIONS"], env["REGION"])
            // rsplit, because a host can contain no colon but a port always follows the last one.
            val host =
                rawTarget?.substringBeforeLast(':', rawTarget)?.trim()?.takeIf { it.isNotEmpty() }
            val portText = rawTarget?.substringAfterLast(':', "")?.trim().orEmpty()
            val port =
                when {
                    portText.isEmpty() -> DEFAULT_TRANSFER_PORT
                    else ->
                        portText.toIntOrNull()?.takeIf { it in 1..65535 }
                            ?: throw IllegalArgumentException(
                                "drain transfer target '$rawTarget' has a bad port"
                            )
                }
            val httpPort =
                env["GROUNDS_DRAIN_HTTP_PORT"]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        it.toIntOrNull()?.takeIf { parsed -> parsed in 0..65535 }
                            ?: throw IllegalArgumentException(
                                "GROUNDS_DRAIN_HTTP_PORT '$it' must be a port number or 0"
                            )
                    } ?: DEFAULT_HTTP_PORT

            val transfersPerSecond =
                env["GROUNDS_DRAIN_TRANSFERS_PER_SECOND"]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        it.toIntOrNull()?.takeIf { parsed -> parsed >= 1 }
                            ?: throw IllegalArgumentException(
                                "GROUNDS_DRAIN_TRANSFERS_PER_SECOND '$it' must be a whole number ≥ 1"
                            )
                    } ?: DEFAULT_TRANSFERS_PER_SECOND

            return DrainConfig(host, port, httpPort, transfersPerSecond)
        }

        /**
         * The `host[:port]` filed under [region] in the `REGIONS` catalogue. Choosing the *own*
         * entry on purpose: a drained player should land where they already are, latency-wise — the
         * replacement proxy close by — not wherever another region's name would steer them. Null
         * (no catalogue, no code, code not listed) leaves the caller to its fallback.
         */
        internal fun ownRegionTarget(regions: String?, region: String?): String? {
            if (regions.isNullOrBlank() || region.isNullOrBlank()) return null
            val code = region.trim()
            return regions
                .split(',')
                .map { it.trim() }
                .firstNotNullOfOrNull { entry ->
                    val entryCode = entry.substringBefore('=', "").trim()
                    val target = entry.substringAfter('=', "").trim()
                    target.takeIf { entryCode.equals(code, ignoreCase = true) && it.isNotEmpty() }
                }
        }
    }
}
