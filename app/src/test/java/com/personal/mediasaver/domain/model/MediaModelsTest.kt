package com.personal.mediasaver.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModelsTest {

    @Test
    fun `deserializes Python JSON contract into ParsedMedia`() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = """
            {
              "status": "ok",
              "platform": "INSTAGRAM",
              "title": "Sunset Carousel",
              "author": "travel_user",
              "media_type": "MIXED",
              "items": [
                {"url": "https://cdn.example.com/1.jpg", "type": "IMAGE", "thumbnail": "https://cdn.example.com/1_t.jpg", "quality": "1080x1350"},
                {"url": "https://cdn.example.com/2.mp4", "type": "VIDEO", "thumbnail": "https://cdn.example.com/2_t.jpg", "quality": "1080p"}
              ],
              "error_message": ""
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ParsedMedia>(payload)
        assertTrue(parsed.isSuccess)
        assertEquals(Platform.INSTAGRAM, parsed.platformEnum)
        assertEquals(MediaType.MIXED, parsed.mediaTypeEnum)
        assertEquals(2, parsed.items.size)
        assertEquals(MediaItemType.IMAGE, parsed.items[0].itemType)
        assertEquals(MediaItemType.VIDEO, parsed.items[1].itemType)
    }

    @Test
    fun `parses ThemeMode keys with safe fallback to SYSTEM`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("LIGHT"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("unknown_mode"))
    }
}
