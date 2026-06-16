package gg.grounds

import gg.grounds.runtime.GroundsModule
import gg.grounds.runtime.GroundsModuleProvider

class GroundsPluginAgonesProvider : GroundsModuleProvider {
    override val id: String = GroundsPluginAgones.MODULE_ID
    override val version: String = GroundsPluginAgones.VERSION

    override fun create(): GroundsModule = GroundsPluginAgones()
}
