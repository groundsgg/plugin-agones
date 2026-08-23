package gg.grounds.drain

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.CookieReceiveEvent
import gg.grounds.discovery.DiscoveryPlayerListener
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrainCookieListenerOrderingTest {

    @Test
    fun `source cookie suppression runs before drain cookie completion`() {
        val discoveryPriority =
            DiscoveryPlayerListener::class
                .java
                .getDeclaredMethod("onCookieReceive", CookieReceiveEvent::class.java)
                .getAnnotation(Subscribe::class.java)
                .priority
        val drainPriority =
            DrainListener::class
                .java
                .getDeclaredMethod("onCookieReceive", CookieReceiveEvent::class.java)
                .getAnnotation(Subscribe::class.java)
                .priority

        assertTrue(
            discoveryPriority > drainPriority,
            "Discovery must suppress source drain cookies before DrainListener completes them",
        )
    }
}
