package com.enova.iptvplayer

data class SportsChannel(val name: String, val id: String) {
    val pageUrl: String get() = "https://tvf90.com/online.php?stream=" + id
}

/**
 * Catálogo estático de los enlaces online.php compartidos por el usuario.
 * No son URL de video, ni se ofrecen como fuentes M3U/Media3.
 */
object SportsCatalog {
    fun parseTsv(text: String): List<SportsChannel> {
        val validId = Regex("^[A-Za-z0-9_]{1,80}$")
        val entries = LinkedHashMap<String, SportsChannel>()
        text.lineSequence().forEach { line ->
            val parts = line.trim().split('\t')
            if (parts.size == 2) {
                val name = parts[0].trim()
                val id = parts[1].trim()
                if (name.isNotEmpty() && name.length <= 100 && validId.matches(id)) {
                    entries[id] = SportsChannel(name, id)
                }
            }
        }
        return entries.values.toList()
    }
}
