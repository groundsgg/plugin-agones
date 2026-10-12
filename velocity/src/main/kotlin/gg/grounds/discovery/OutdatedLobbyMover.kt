package gg.grounds.discovery

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import net.kyori.adventure.text.Component
import org.slf4j.Logger

/**
 * Which players to move off outdated lobbies this tick, and where to.
 *
 * Only players standing on an outdated lobby move, at most [limit] of them, and only when a current
 * lobby exists to take them. Each move counts towards its target, so one tick spreads its players
 * the way [LobbySelection.pick] spreads joins instead of sending all of them to the same lobby.
 */
internal fun <P> planOutdatedLobbyMoves(
    players: List<Pair<P, String>>,
    lobbies: List<LobbySelection.Candidate>,
    limit: Int,
): List<Pair<P, String>> {
    val outdated = lobbies.filter { it.outdated }.mapTo(mutableSetOf()) { it.name }
    val counts =
        lobbies.filter { !it.outdated }.associateTo(mutableMapOf()) { it.name to it.players }
    if (outdated.isEmpty() || counts.isEmpty()) return emptyList()
    return players
        .filter { (_, server) -> server in outdated }
        .take(limit)
        .map { (player, _) ->
            val target =
                LobbySelection.pick(counts.map { LobbySelection.Candidate(it.key, it.value) })!!
            counts[target] = counts.getValue(target) + 1
            player to target
        }
}

/**
 * Moves this proxy's players off lobbies a Fleet rollout has replaced.
 *
 * A rollout deletes only Ready GameServers, and a lobby with players in it is Allocated — so
 * without this an old lobby lives as long as anyone stands in it, on the old build. Once it is
 * empty the lobby readies itself and Agones deletes it, because its GameServerSet is at 0.
 *
 * Every proxy moves only its own players: each one can only connect the players it holds, and the
 * per-tick limit keeps a full lobby from landing on its successor all at once.
 */
class OutdatedLobbyMover(
    private val plugin: Any,
    private val proxyServer: ProxyServer,
    private val logger: Logger,
    private val lobbies: () -> List<LobbySelection.Candidate>,
    /** A draining proxy sends its lobby players to another proxy instead; moving them is moot. */
    private val isDraining: () -> Boolean,
    private val maxMovesPerTick: Int = DEFAULT_MAX_MOVES_PER_TICK,
) {
    private val inFlight: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var task: ScheduledTask? = null

    fun start() {
        task =
            proxyServer.scheduler
                .buildTask(plugin, Runnable { tick() })
                .delay(TICK_SECONDS, TimeUnit.SECONDS)
                .repeat(TICK_SECONDS, TimeUnit.SECONDS)
                .schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    internal fun tick() {
        if (isDraining()) return
        val lobbies = lobbies()
        if (lobbies.none { it.outdated }) return

        // A player without a current server is still joining, and one we already sent is on the
        // way; Velocity would refuse a second request for either, so neither is asked twice.
        val players =
            proxyServer.allPlayers
                .filter { it.uniqueId !in inFlight }
                .mapNotNull { player ->
                    player.currentServer.orElse(null)?.let { player to it.serverInfo.name }
                }
        for ((player, target) in planOutdatedLobbyMoves(players, lobbies, maxMovesPerTick)) {
            move(player, target)
        }
    }

    private fun move(player: Player, targetName: String) {
        val target = proxyServer.getServer(targetName).orElse(null) ?: return
        val from = player.currentServer.map { it.serverInfo.name }.orElse(null)
        inFlight.add(player.uniqueId)
        logger.info(
            "Moving player off outdated lobby (player={}, from={}, to={})",
            player.username,
            from,
            targetName,
        )
        player.sendMessage(MOVE_MESSAGE)
        // connect(), not fireAndForget(): the future says when the move is over, and a failed move
        // leaves the player where they are — on a lobby that still works — to be retried next tick.
        player.createConnectionRequest(target).connect().whenComplete { result, error ->
            inFlight.remove(player.uniqueId)
            if (error != null || result?.isSuccessful != true) {
                logger.warn(
                    "Failed to move player off outdated lobby (player={}, to={}, status={})",
                    player.username,
                    targetName,
                    error?.message ?: result?.status,
                )
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_MOVES_PER_TICK = 10
        private const val TICK_SECONDS = 5L
        val MOVE_MESSAGE: Component = Component.text("Moving you to an updated lobby…")
    }
}
