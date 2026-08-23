package gg.grounds.drain

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates store/request/echo before a drain transfer, with a bounded normal-transfer fallback.
 */
class DrainTransferStager(private val scheduleTimeout: ((() -> Unit) -> Unit)) {
    private val pending = ConcurrentHashMap<String, Pending>()

    fun stage(
        playerId: String,
        payload: ByteArray,
        store: () -> Unit,
        request: () -> Unit,
        transfer: () -> Unit,
    ) {
        val stage = Pending(payload, request, transfer)
        if (pending.putIfAbsent(playerId, stage) != null) return
        try {
            store()
            request()
            scheduleTimeout { complete(playerId, stage) }
        } catch (_: Exception) {
            complete(playerId, stage)
        }
    }

    fun onCookie(playerId: String, payload: ByteArray?) {
        val stage = pending[playerId] ?: return
        if (payload != null && MessageDigest.isEqual(stage.payload, payload)) {
            complete(playerId, stage)
        } else {
            try {
                stage.request()
            } catch (_: Exception) {
                complete(playerId, stage)
            }
        }
    }

    fun isPending(playerId: String): Boolean = pending.containsKey(playerId)

    private fun complete(playerId: String, stage: Pending) {
        if (pending.remove(playerId, stage)) stage.transfer()
    }

    private class Pending(val payload: ByteArray, val request: () -> Unit, val transfer: () -> Unit)
}
