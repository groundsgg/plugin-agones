package gg.grounds.agones

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
    fun allocate() {
        scope.launch { ensureState(desiredState = "Allocated") { agonesClient.allocate() } }
    }

    fun ready() {
        scope.launch { ensureState(desiredState = "Ready") { agonesClient.ready() } }
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
}
