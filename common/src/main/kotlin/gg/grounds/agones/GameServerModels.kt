package gg.grounds.agones

import com.google.gson.annotations.SerializedName

data class GameServerResponse(val statusCode: Int, val body: String, val gameServer: GameServer?)

data class GameServer(
    @SerializedName(value = "object_meta", alternate = ["objectMeta"])
    val objectMeta: ObjectMeta? = null,
    val spec: Spec? = null,
    val status: Status? = null,
) {
    data class ObjectMeta(
        val name: String? = null,
        val namespace: String? = null,
        val uid: String? = null,
        @SerializedName(value = "resource_version", alternate = ["resourceVersion"])
        val resourceVersion: String? = null,
        val generation: Long? = null,
        @SerializedName(value = "creation_timestamp", alternate = ["creationTimestamp"])
        val creationTimestamp: Long? = null,
        @SerializedName(value = "deletion_timestamp", alternate = ["deletionTimestamp"])
        val deletionTimestamp: Long? = null,
        val annotations: Map<String, String>? = null,
        val labels: Map<String, String>? = null,
    )

    data class Spec(val health: Health? = null) {
        data class Health(
            val disabled: Boolean? = null,
            @SerializedName(value = "period_seconds", alternate = ["periodSeconds"])
            val periodSeconds: Int? = null,
            @SerializedName(value = "failure_threshold", alternate = ["failureThreshold"])
            val failureThreshold: Int? = null,
            @SerializedName(value = "initial_delay_seconds", alternate = ["initialDelaySeconds"])
            val initialDelaySeconds: Int? = null,
        )
    }

    data class Status(
        val state: String? = null,
        val address: String? = null,
        val addresses: List<Address>? = null,
        val ports: List<Port>? = null,
        val players: PlayerStatus? = null,
        val counters: Map<String, CounterStatus>? = null,
        val lists: Map<String, ListStatus>? = null,
    ) {
        data class Address(val type: String? = null, val address: String? = null)

        data class Port(val name: String? = null, val port: Int? = null)

        data class PlayerStatus(
            val count: Long? = null,
            val capacity: Long? = null,
            val ids: List<String>? = null,
        )

        data class CounterStatus(val count: Long? = null, val capacity: Long? = null)

        data class ListStatus(val capacity: Long? = null, val values: List<String>? = null)
    }
}
