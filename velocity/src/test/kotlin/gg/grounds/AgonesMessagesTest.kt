package gg.grounds

import gg.grounds.i18n.BundleCheck
import gg.grounds.i18n.SystemCategory
import gg.grounds.i18n.SystemLevel
import gg.grounds.i18n.Translations
import java.util.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgonesMessagesTest {
    private val messages =
        Translations.forBundle("gg.grounds.agones.messages", javaClass.classLoader)
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `server status arguments remain literal`() {
        val rendered =
            messages.render(
                AgonesMessage.SERVER_LINE,
                Locale.ENGLISH,
                "name" to "<red>lobby-1</red>",
                "type" to "lobby",
                "address" to "127.0.0.1:25565",
                "players" to 3,
            )

        assertEquals(
            "<red>lobby-1</red> [lobby] 127.0.0.1:25565 (3 players)",
            plain.serialize(rendered),
        )
        assertEquals(SystemCategory.GENERAL, AgonesMessage.SERVER_LINE.category)
        assertEquals(SystemLevel.INFO, AgonesMessage.SERVER_LINE.level)
    }

    @Test
    fun `restart disconnect is localized without a chat prefix`() {
        val rendered = messages.render(AgonesMessage.RESTART_DISCONNECT, Locale.GERMAN)

        assertEquals(
            "Dieser Proxy wird neu gestartet — bitte verbinde dich erneut.",
            plain.serialize(rendered),
        )
        assertEquals(SystemCategory.GENERAL, AgonesMessage.RESTART_DISCONNECT.category)
        assertEquals(SystemLevel.WARNING, AgonesMessage.RESTART_DISCONNECT.level)
    }

    @Test
    fun `missing lobby denial is localized without a chat prefix`() {
        val rendered = messages.render(AgonesMessage.NO_LOBBY_AVAILABLE, Locale.GERMAN)

        assertEquals(
            "Derzeit sind keine Lobby-Server verfügbar. Bitte versuche es gleich noch einmal.",
            plain.serialize(rendered),
        )
        assertEquals(SystemCategory.GENERAL, AgonesMessage.NO_LOBBY_AVAILABLE.category)
        assertEquals(SystemLevel.ERROR, AgonesMessage.NO_LOBBY_AVAILABLE.level)
    }

    @Test
    fun `English and German bundles cover every Agones message`() {
        assertEquals(
            emptyList<String>(),
            BundleCheck.problems(
                "gg.grounds.agones.messages",
                javaClass.classLoader,
                AgonesMessage.entries,
                translations = listOf(Locale.GERMAN),
            ),
        )
    }
}
