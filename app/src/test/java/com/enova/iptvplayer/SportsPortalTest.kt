package com.enova.iptvplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsPortalTest {
    @Test fun onlyOfficialHttpsHostIsAllowed() {
        assertTrue(SportsPortal.isAllowedNavigation("https://tvf90.com/"))
        assertTrue(SportsPortal.isAllowedNavigation("https://tvf90.com/agenda.html?fecha=hoy"))
        assertTrue(SportsPortal.isAllowedNavigation("https://www.tvf90.com/canales"))
        assertFalse(SportsPortal.isAllowedNavigation("http://tvf90.com/"))
        assertFalse(SportsPortal.isAllowedNavigation("https://tvf90.com.evil.org/"))
        assertFalse(SportsPortal.isAllowedNavigation("https://another-site.com/"))
        assertFalse(SportsPortal.isAllowedNavigation("javascript:alert(1)"))
        assertFalse(SportsPortal.isAllowedNavigation("https://a@tvf90.com/"))
    }

    @Test fun agendaRefreshIsNotContinuousPolling() {
        assertTrue(SportsPortal.REFRESH_INTERVAL_MS >= 120_000L)
    }
}
