package org.fuchss.matrix.bots

import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Ensures kotlinx-serialization-core, -json and -json-io resolve to compatible versions.
 * Trixnity decodes all server responses via json-io, so a mismatch breaks every bot on login.
 */
class SerializationCompatibilityTest {
    @Test
    fun decodeFromSourceWorks() {
        val source = Buffer().apply { writeString("""{"user_id":"@bot:example.org"}""") }
        val json = createMatrixEventJson()
        val result = json.decodeFromSource<JsonObject>(source)
        assertEquals("@bot:example.org", result["user_id"]?.jsonPrimitive?.content)
    }
}
