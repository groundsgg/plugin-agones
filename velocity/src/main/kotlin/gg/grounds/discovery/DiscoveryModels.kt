package gg.grounds.discovery

data class GameServerList(val items: List<GameServer> = emptyList())

data class GameServer(val metadata: Metadata? = null, val status: Status? = null)

data class Metadata(val name: String? = null, val labels: Map<String, String> = emptyMap())

data class Status(val state: String? = null, val addresses: List<GameServerAddress> = emptyList())

data class GameServerAddress(val address: String? = null, val type: String? = null)

data class GameServerSetList(val items: List<GameServerSet> = emptyList())

data class GameServerSet(val metadata: Metadata? = null, val spec: GameServerSetSpec? = null)

data class GameServerSetSpec(val replicas: Int? = null)
