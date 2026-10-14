package gg.grounds

import gg.grounds.i18n.SystemCategory
import gg.grounds.i18n.SystemMessageKey

enum class AgonesMessage(override val id: String, override val category: SystemCategory) :
    SystemMessageKey {
    NO_SERVERS("command.no-servers", SystemCategory.ERROR),
    SERVER_HEADER("command.server-header", SystemCategory.INFO),
    SERVER_LINE("command.server-line", SystemCategory.INFO),
    SERVER_TOTAL("command.server-total", SystemCategory.INFO),
    LOCAL_PLAYER_COUNTS("command.local-player-counts", SystemCategory.WARNING),
    MOVING_TO_UPDATED_LOBBY("discovery.moving-to-updated-lobby", SystemCategory.INFO),
    RESTART_DISCONNECT("drain.restart-disconnect", SystemCategory.WARNING),
    NO_LOBBY_AVAILABLE("discovery.no-lobby-available", SystemCategory.ERROR),
}
