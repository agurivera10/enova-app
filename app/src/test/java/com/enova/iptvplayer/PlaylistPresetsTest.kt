package com.enova.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPresetsTest {
    @Test fun presetsIncludeFiveDirectPlaylistsAndCustomOption() {
        assertEquals(6, PlaylistPresets.entries.size)
        assertEquals("", PlaylistPresets.entries.first().url)
        assertTrue(PlaylistPresets.entries.drop(1).all { it.url.endsWith(".m3u") && it.url.startsWith("https://") })
    }

    @Test fun unverifiedSourcesAreMarked() {
        assertEquals(3, PlaylistPresets.entries.count { it.isUnverified })
        assertTrue(PlaylistPresets.entries.take(3).all { !it.isUnverified })
    }
}
