package gg.grounds.agones

/**
 * Who decides this GameServer's Agones state.
 *
 * By default a server owns its own state: the first player to join flips it to Allocated, and when
 * the last one leaves it goes back to Ready so the fleet can hand it out again. That is right for a
 * lobby or a standalone gamemode, where "empty" genuinely means "free".
 *
 * It is *wrong* the moment an external matchmaker allocates the server. The matchmaker allocates
 * first and the players arrive seconds later (assign → proxy → client connect), so during that
 * window the server is Allocated but empty — and a self-managing server would immediately call
 * `ready()` and put itself back in the pool. The next allocation then hands the same server to a
 * second match. Two matches, one server, and the reaper cannot see the orphan because it is Ready
 * rather than Allocated.
 *
 * So when a matchmaker owns the lifecycle, this plugin must keep its hands off the state: no
 * readiness loop, no `ready()` on empty. The server leaves the pool for good and shuts down when
 * its match is over; the fleet replaces it with a fresh one.
 *
 * The switch is an environment variable rather than a lookup of our own GameServer labels, because
 * the label is patched onto us *at allocation time* — a server that polled for it would race the
 * very window this is meant to close. The env var is set when the pod is created, so we know from
 * birth which mode we are in.
 */
enum class GameServerOwnership {
    /** This server manages its own Ready/Allocated state from player count. */
    SELF_MANAGED,

    /** An external matchmaker owns the state. Never call `ready()`. */
    MATCHMAKER_MANAGED;

    val isMatchmakerManaged: Boolean
        get() = this == MATCHMAKER_MANAGED

    companion object {
        /**
         * forge sets this on the Fleet template when the gamemode declares a `matchmaking:` block.
         */
        const val ENV_VAR = "GROUNDS_MATCHMAKING"

        fun fromEnvironment(getenv: (String) -> String? = System::getenv): GameServerOwnership {
            val raw = getenv(ENV_VAR)?.trim()
            val enabled =
                raw.equals("1", ignoreCase = true) || raw.equals("true", ignoreCase = true)
            return if (enabled) MATCHMAKER_MANAGED else SELF_MANAGED
        }
    }
}
