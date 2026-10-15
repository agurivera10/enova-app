package com.enova.iptvplayer

import java.net.URI

/** Sitio deportivo externo: URLs HTTPS fijas, sin extraer stream URLs. */
object SportsPortal {
    const val HOME_URL = "https://tvf90.com/"
    const val AGENDA_URL = "https://tvf90.com/agenda.html"
    const val REFRESH_INTERVAL_MS = 120_000L

    /** Solo se navega dentro de la web indicada. Links externos se bloquean. */
    fun isAllowedNavigation(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            (uri.host.equals("tvf90.com", ignoreCase = true) ||
                uri.host.equals("www.tvf90.com", ignoreCase = true)) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null
    } catch (_: Exception) {
        false
    }
}
