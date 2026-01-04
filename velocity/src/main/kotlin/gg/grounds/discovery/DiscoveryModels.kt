package gg.grounds.discovery

data class GameServerList(val items: List<GameServer> = emptyList())

data class GameServer(val metadata: Metadata? = null, val status: Status? = null)

data class Metadata(val name: String? = null)

data class Status(val state: String? = null, val addresses: List<GameServerAddress> = emptyList())

data class GameServerAddress(val address: String? = null, val type: String? = null)
