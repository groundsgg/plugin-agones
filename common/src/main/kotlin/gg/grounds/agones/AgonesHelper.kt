package gg.grounds.agones

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AgonesLogger {
    fun info(message: String)

    fun error(message: String, error: Throwable)
}

class AgonesHelper(
    private val agonesClient: AgonesRestClient,
    private val logger: AgonesLogger,
    private val scope: CoroutineScope,
) {
    @Volatile private var retired = false
    private val shutdownRequested = AtomicBoolean(false)

    fun allocate() {
        scope.launch { ensureState(desiredState = "Allocated") { agonesClient.allocate() } }
    }

    /**
     * Ready, unless this server is [retire]d: then it leaves instead. Every caller of [ready] means
     * "this server is empty", and a retired server that is empty has nothing left to wait for.
     */
    fun ready() {
        if (retired) {
            shutdown()
            return
        }
        scope.launch { ensureState(desiredState = "Ready") { agonesClient.ready() } }
    }

    /**
     * Marks this server as one to replace: the [RETIRED_LABEL] label tells proxies to move its
     * players elsewhere and send no new ones, and the next [ready] shuts it down so the Fleet
     * starts a fresh one.
     */
    fun retire() {
        retired = true
        scope.launch {
            try {
                agonesClient.setLabel(RETIRED_LABEL, "true")
                logger.info("Retired Agones GameServer successfully (label=$RETIRED_LABEL)")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.error("Failed to label Agones GameServer as retired", error)
            }
        }
    }

    private fun shutdown() {
        // Once: an empty server is told it is empty every few seconds until the shutdown lands.
        if (!shutdownRequested.compareAndSet(false, true)) return
        scope.launch {
            try {
                agonesClient.shutdown()
                logger.info("Requested Agones GameServer shutdown successfully (reason=retired)")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                shutdownRequested.set(false)
                logger.error("Failed to request Agones GameServer shutdown", error)
            }
        }
    }

    private suspend fun ensureState(desiredState: String, action: suspend () -> Unit) {
        val isInState =
            try {
                agonesClient.isGameServerInState(desiredState)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.error("Failed to check Agones GameServer state (state=$desiredState)", error)
                return
            }

        if (isInState) {
            return
        }

        try {
            action()
            logger.info("Updated Agones GameServer state successfully (state=$desiredState)")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logger.error("Failed to update Agones GameServer state (state=$desiredState)", error)
        }
    }

    companion object {
        /** Read back by proxies as `agones.dev/sdk-grounds-retired`. */
        const val RETIRED_LABEL = "grounds-retired"
    }
}
