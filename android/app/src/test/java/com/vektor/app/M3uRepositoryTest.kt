package com.vektor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uRepositoryTest {
    @Test
    fun parserExtractsCoreFields() {
        val body = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://example.com/logo.png" group-title="News",Global News HD
            https://example.com/live.m3u8
        """.trimIndent()

        val channels = M3uRepository().parse(body)

        assertEquals(1, channels.size)
        assertEquals("Global News HD", channels[0].title)
        assertEquals("News", channels[0].group)
        assertEquals("https://example.com/logo.png", channels[0].logoUrl)
        assertEquals("https://example.com/live.m3u8", channels[0].streamUrl)
    }

    @Test
    fun parserUsesFallbacks() {
        val body = """
            #EXTM3U
            #EXTINF:-1,
            https://example.com/live.m3u8
        """.trimIndent()

        val channel = M3uRepository().parse(body).first()

        assertEquals("Channel 1", channel.title)
        assertEquals("Ungrouped", channel.group)
    }

    @Test
    fun parserRejectsEmptyPlaylist() {
        assertThrows(IllegalArgumentException::class.java) {
            M3uRepository().parse("#EXTM3U\n#EXTINF:-1,Empty")
        }
    }

    @Test
    fun urlValidationRejectsNonHttp() {
        assertFalse(M3uRepository.isHttpUrl("ftp://example.com/list.m3u"))
        assertTrue(M3uRepository.isHttpUrl("https://example.com/list.m3u"))
    }
}
