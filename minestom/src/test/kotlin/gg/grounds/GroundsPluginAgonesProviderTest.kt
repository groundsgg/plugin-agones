package gg.grounds

import gg.grounds.runtime.GroundsModuleProvider
import gg.grounds.runtime.ServerType
import java.util.ServiceLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroundsPluginAgonesProviderTest {
    @Test
    fun `exposes agones module descriptor`() {
        val provider = GroundsPluginAgonesProvider()
        val module = provider.create()

        assertTrue(module is GroundsPluginAgones)
        assertEquals("grounds.agones", provider.id)
        assertEquals(BuildInfo.VERSION, provider.version)
        assertNotEquals("local", provider.version)
        assertEquals(ServerType.entries.toSet(), provider.serverTypes)
        assertEquals(provider.id, provider.descriptor.id)
        assertEquals(provider.version, provider.descriptor.version)
        assertEquals(provider.id, module.id)
    }

    @Test
    fun `provider is discoverable through service loader`() {
        val providers = ServiceLoader.load(GroundsModuleProvider::class.java).toList()

        assertTrue(providers.any { provider -> provider is GroundsPluginAgonesProvider })
    }
}
