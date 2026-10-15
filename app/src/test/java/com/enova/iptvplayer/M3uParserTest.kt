package com.enova.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test fun parsesMetadataAndGroups() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.ar" tvg-name="Noticias" tvg-logo="https://example.org/logo.png" group-title="Noticias y más",Canal Noticias
            https://example.org/live/news.m3u8
        """.trimIndent()
        val result = M3uParser.parse(playlist)
        assertEquals(1, result.size)
        assertEquals("Canal Noticias", result.first().name)
        assertEquals("Noticias y más", result.first().group)
        assertEquals("news.ar", result.first().tvgId)
        assertEquals("https://example.org/logo.png", result.first().logo)
    }

    @Test fun resolvesRelativeUrlsAndExtGrp() {
        val playlist = "\uFEFF#EXTM3U\n#EXTINF:-1,Título, con coma\n#EXTGRP:Deportes\nstream.m3u8"
        val result = M3uParser.parse(playlist, "https://example.org/lists/lista.m3u")
        assertEquals("Título, con coma", result.single().name)
        assertEquals("Deportes", result.single().group)
        assertEquals("https://example.org/lists/stream.m3u8", result.single().streamUrl)
    }

    @Test fun ignoresUnsupportedSchemes() {
        val result = M3uParser.parse("""
            #EXTM3U
            #EXTINF:-1,No reproducir
            file:///etc/passwd
            #EXTINF:-1,Permitido
            https://example.org/stream.m3u8
            #EXTINF:-1,Inseguro
            javascript:alert(1)
        """.trimIndent())
        assertEquals(1, result.size)
        assertEquals("Permitido", result.first().name)
    }

    @Test fun handlesMissingInfoAndEmptyList() {
        assertTrue(M3uParser.parse("#EXTM3U\n# comentario").isEmpty())
        assertEquals("Canal 1", M3uParser.parse("https://example.org/stream").single().name)
    }
}
