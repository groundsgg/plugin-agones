package gg.grounds

import gg.grounds.i18n.SystemCategory
import gg.grounds.i18n.SystemLevel
import gg.grounds.i18n.SystemMessageKey

enum class AgonesMessage(
    override val id: String,
    override val category: SystemCategory = SystemCategory.GENERAL,
    override val level: SystemLevel = SystemLevel.INFO,
) : SystemMessageKey {
    NO_SERVERS("command.no-servers", level = SystemLevel.ERROR),
    SERVER_HEADER("command.server-header"),
    SERVER_LINE("command.server-line"),
    SERVER_TOTAL("command.server-total"),
    LOCAL_PLAYER_COUNTS("command.local-player-counts", level = SystemLevel.WARNING),
    MOVING_TO_UPDATED_LOBBY("discovery.moving-to-updated-lobby"),
    RESTART_DISCONNECT("drain.restart-disconnect", level = SystemLevel.WARNING),
    NO_LOBBY_AVAILABLE("discovery.no-lobby-available", level = SystemLevel.ERROR),
}
