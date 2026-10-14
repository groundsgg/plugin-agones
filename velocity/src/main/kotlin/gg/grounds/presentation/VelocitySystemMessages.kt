package gg.grounds.presentation

import com.velocitypowered.api.proxy.Player
import gg.grounds.i18n.SystemMessageRenderer
import gg.grounds.proxy.api.PlayerChatAppearanceQuery
import gg.grounds.proxy.api.ProxyServiceRegistry

/** Resolve readiness at delivery time: plugins never cache a player's pack state. */
internal object VelocitySystemMessages {
    val renderer =
        SystemMessageRenderer.withLogoReadiness { audience ->
            val player = audience as? Player
            player != null &&
                runCatching {
                        ProxyServiceRegistry.get(PlayerChatAppearanceQuery::class.java)
                            ?.appearanceOf(player.uniqueId)
                            ?.chatFontReady == true
                    }
                    .getOrDefault(false)
        }
}
