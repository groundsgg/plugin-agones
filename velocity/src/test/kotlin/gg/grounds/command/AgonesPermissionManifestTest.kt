package gg.grounds.command

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgonesPermissionManifestTest {

    @Test
    fun `declares agones command permission in manifest resource`() {
        val resource =
            checkNotNull(
                javaClass.classLoader.getResourceAsStream("META-INF/grounds/permissions.json")
            ) {
                "permissions manifest resource is missing"
            }

        val manifest = JsonParser.parseReader(resource.reader()).asJsonObject
        val permissions = manifest.getAsJsonArray("permissions")
        val agonesCommand =
            permissions
                .map { it.asJsonObject }
                .singleOrNull { it.get("key").asString == "grounds.command.agones" }

        assertNotNull(agonesCommand)
        assertEquals("plugin-agones", manifest.get("source").asString)
        assertEquals("Use Agones command", agonesCommand!!.get("label").asString)
        assertTrue(agonesCommand.get("description").asString.isNotBlank())
        assertEquals(
            listOf("GLOBAL", "SERVER_TYPE", "SERVER"),
            agonesCommand.getAsJsonArray("supportedScopes").map { it.asString },
        )
    }
}
