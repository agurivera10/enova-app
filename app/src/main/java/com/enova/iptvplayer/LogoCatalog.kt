package com.enova.iptvplayer

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Logo URLs resolved at build time; never treats a generic name match as proof. */
class LogoCatalog(context: Context) {
    private val byId: Map<String, String>
    private val byName: Map<String, String>
    init {
        val raw = runCatching {
            context.assets.open("sports_logos.json").bufferedReader().use { it.readText() }
        }.getOrDefault("{}")
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val entries = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            val value = json.optString(key)
            if (value.startsWith("https://")) entries[key] = value
        }
        byId = entries
        val nameMap = mutableMapOf<String, String>()
        val table = runCatching {
            context.assets.open("sports_channels.tsv").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        SportsCatalog.parseTsv(table).forEach { channel ->
            entries[channel.id]?.let { nameMap.putIfAbsent(normalize(channel.name), it) }
        }
        byName = nameMap
    }

    fun forSport(channel: SportsChannel) = byId[channel.id]

    fun forIptv(channel: Channel): String? {
        if (!channel.logo.isNullOrBlank() &&
            (channel.logo.startsWith("https://") || channel.logo.startsWith("http://"))) {
            return channel.logo
        }
        return byName[normalize(channel.name)]
    }

    private fun normalize(value: String): String {
        val ascii = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
        return ascii.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }
}
