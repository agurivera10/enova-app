package com.enova.iptvplayer

data class PlaylistPreset(
    val name: String,
    val url: String,
    val isUnverified: Boolean = false
)

/**
 * Los preajustes son direcciones externas aportadas por el usuario, no contenido
 * de video empaquetado. Se descargan solo al pulsar "Cargar URL".
 */
object PlaylistPresets {
    val entries = listOf(
        PlaylistPreset("URL personalizada (escribí o pegá una)", ""),
        PlaylistPreset("iptv-org · Todos los países", "https://iptv-org.github.io/iptv/index.m3u"),
        PlaylistPreset("iptv-org · Argentina", "https://iptv-org.github.io/iptv/countries/ar.m3u"),
        PlaylistPreset("TecnoTV · Deportes (no verificada)", "https://tecnotv.club/kaw3/deportes.m3u", true),
        PlaylistPreset("TecnoTV · Lista 1 (no verificada)", "https://tecnotv.club/kaw3/lista1.m3u", true),
        PlaylistPreset("TecnoTV · Lista (no verificada)", "https://tecnotv.club/kaw3/lista.m3u", true)
    )
}
