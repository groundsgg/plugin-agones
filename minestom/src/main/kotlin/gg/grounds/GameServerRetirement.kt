package gg.grounds

/**
 * Lets a self-managed server ask to be replaced, for example a lobby whose map was re-pinned.
 *
 * Registered by [GroundsPluginAgones] for self-managed servers only; a matchmade server ends with
 * its match instead. After [retire] proxies move its players to current servers and send it no new
 * ones, and once it is empty it shuts down so the Fleet starts a fresh one in its place.
 */
fun interface GameServerRetirement {
    fun retire()
}
