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
        scope.launch {
            ensureState(
                desiredState = "Allocated",
                successMessage = "Agones GameServer marked Allocated",
                failureMessage = "Failed to mark Agones GameServer Allocated",
            ) {
                agonesClient.allocate()
            }
        }
    }

    fun ready() {
        scope.launch {
            ensureState(
                desiredState = "Ready",
                successMessage = "Agones GameServer marked Ready",
                failureMessage = "Failed to mark Agones GameServer Ready",
            ) {
                agonesClient.ready()
            }
        }
    }

    private suspend fun ensureState(
        desiredState: String,
        successMessage: String,
        failureMessage: String,
        action: suspend () -> Unit,
    ) {
        val isInState =
            try {
                agonesClient.isGameServerInState(desiredState)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.error("Failed to check Agones GameServer state", error)
                return
            }

        if (isInState) {
            return
        }

        try {
            action()
            logger.info(successMessage)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logger.error(failureMessage, error)
        }
    }
}
