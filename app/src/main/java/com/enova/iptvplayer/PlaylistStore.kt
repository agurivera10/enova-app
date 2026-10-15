package com.enova.iptvplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda una sola lista y los favoritos localmente en el dispositivo. */
class PlaylistStore(context: Context) {
    private val preferences = context.getSharedPreferences("enova_iptv", Context.MODE_PRIVATE)

    fun saveChannels(channels: List<Channel>) {
        val entries = JSONArray()
        channels.forEach { channel ->
            entries.put(JSONObject().apply {
                put("name", channel.name)
                put("url", channel.streamUrl)
                put("group", channel.group)
                put("logo", channel.logo)
                put("tvgId", channel.tvgId)
            })
        }
        preferences.edit().putString("channels", entries.toString()).apply()
    }

    fun loadChannels(): List<Channel> = try {
        val array = JSONArray(preferences.getString("channels", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val value = array.optJSONObject(index) ?: return@mapNotNull null
            val url = value.optString("url")
            if (url.isBlank()) return@mapNotNull null
            Channel(
                name = value.optString("name", "Canal"),
                streamUrl = url,
                group = value.optString("group", "Sin categoría"),
                logo = value.optString("logo").takeIf { it.isNotBlank() && it != "null" },
                tvgId = value.optString("tvgId").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun favorites(): Set<String> = preferences.getStringSet("favorites", emptySet())?.toSet().orEmpty()

    fun toggleFavorite(streamUrl: String): Boolean {
        val favorites = favorites().toMutableSet()
        val added = if (streamUrl in favorites) {
            favorites.remove(streamUrl)
            false
        } else {
            favorites.add(streamUrl)
            true
        }
        preferences.edit().putStringSet("favorites", favorites).apply()
        return added
    }

    fun saveSource(source: String) = preferences.edit().putString("source_url", source).apply()
    fun lastSource(): String = preferences.getString("source_url", "").orEmpty()
}
