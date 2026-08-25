package gg.grounds.discovery

import gg.grounds.proxy.api.ServerDisplay
import gg.grounds.proxy.api.ServerDisplayQuery

class DiscoveryServerDisplayQuery(private val roleOf: (String) -> String?) : ServerDisplayQuery {
    override fun displayOf(serverName: String): ServerDisplay? {
        val kind = roleOf(serverName) ?: return null
        val id = serverName.substringAfterLast('-').ifEmpty { serverName }
        return ServerDisplay(kind = kind, id = id)
    }
}
