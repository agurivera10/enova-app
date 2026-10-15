package com.enova.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsCatalogTest {
    @Test fun parsesDirectPagesAndRetainsSameNameVariants() {
        val result = SportsCatalog.parseTsv("""
            ESPN	espn
            ESPN	espnmx
            TyC Sports	tycsports
            Link malo	javascript:alert(1)
        """.trimIndent())
        assertEquals(3, result.size)
        assertEquals("https://tvf90.com/online.php?stream=espn", result.first().pageUrl)
        assertEquals("https://tvf90.com/online.php?stream=espnmx", result[1].pageUrl)
        assertTrue(result[2].name == "TyC Sports")
    }

    @Test fun duplicateIdsAreDeduplicated() {
        val result = SportsCatalog.parseTsv("ESPN\tespn\nESPN repetido\tespn")
        assertEquals(1, result.size)
    }
}
