package gg.grounds.presentation

import com.velocitypowered.api.proxy.Player
import gg.grounds.i18n.SystemMessageRenderer
import gg.grounds.i18n.SystemPrefixStyle
import gg.grounds.proxy.api.PlayerChatAppearanceQuery
import gg.grounds.proxy.api.PlayerSystemBadgeQuery
import gg.grounds.proxy.api.ProxyServiceRegistry
import java.util.UUID

/** Resolves the current recipient's pack capabilities at delivery time. */
internal object VelocitySystemMessages {
    val renderer =
        SystemMessageRenderer.withPrefixStyle { audience ->
            (audience as? Player)?.uniqueId?.let(::prefixStyle) ?: SystemPrefixStyle.TEXT
        }

    fun prefixStyle(playerId: UUID): SystemPrefixStyle {
        val badgeReady =
            runCatching {
                    ProxyServiceRegistry.get(PlayerSystemBadgeQuery::class.java)
                        ?.systemBadgesReady(playerId) == true
                }
                .getOrDefault(false)
        if (badgeReady) return SystemPrefixStyle.BADGE
        val logoReady =
            runCatching {
                    ProxyServiceRegistry.get(PlayerChatAppearanceQuery::class.java)
                        ?.appearanceOf(playerId)
                        ?.chatFontReady == true
                }
                .getOrDefault(false)
        return if (logoReady) SystemPrefixStyle.LOGO else SystemPrefixStyle.TEXT
    }
}
