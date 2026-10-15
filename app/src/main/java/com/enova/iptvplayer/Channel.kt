package com.enova.iptvplayer

data class Channel(
    val name: String,
    val streamUrl: String,
    val group: String = "Sin categoría",
    val logo: String? = null,
    val tvgId: String? = null,
)
