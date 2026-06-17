package gg.grounds

import gg.grounds.runtime.GroundsModule
import gg.grounds.runtime.GroundsModuleProvider
import gg.grounds.runtime.ServerType

class GroundsPluginAgonesProvider : GroundsModuleProvider {
    override val id: String = GroundsPluginAgones.MODULE_ID
    override val version: String = BuildInfo.VERSION
    override val serverTypes: Set<ServerType> = ServerType.entries.toSet()

    override fun create(): GroundsModule = GroundsPluginAgones()
}
