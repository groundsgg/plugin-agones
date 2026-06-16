package gg.grounds

import gg.grounds.runtime.GroundsModule
import gg.grounds.runtime.GroundsModuleProvider
import gg.grounds.runtime.ServerType

class GroundsPluginAgonesProvider : GroundsModuleProvider {
    override val id: String = GroundsPluginAgones.MODULE_ID
    override val version: String = GroundsPluginAgones.VERSION
    override val serverTypes: Set<ServerType> = setOf(ServerType.MINIGAME)

    override fun create(): GroundsModule = GroundsPluginAgones()
}
