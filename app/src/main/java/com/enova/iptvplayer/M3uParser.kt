package com.enova.iptvplayer

import java.net.URI

/** Analiza M3U extendido; acepta únicamente URL de canales HTTP(S). */
object M3uParser {
    private val attribute = Regex("""([\w-]+)\s*=\s*"([^"]*)"|([\w-]+)\s*=\s*([^\s,]+)""")

    fun parse(text: String, baseUrl: String? = null): List<Channel> {
        val channels = ArrayList<Channel>()
        var pending: Metadata? = null
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF:", true) -> pending = parseExtInf(line)
                line.startsWith("#EXTGRP:", true) ->
                    pending = (pending ?: Metadata()).copy(group = line.substringAfter(':').trim())
                line.startsWith("#") -> Unit
                else -> {
                    val url = resolveStream(line, baseUrl)
                    if (url != null) {
                        val meta = pending ?: Metadata()
                        channels.add(Channel(
                            name = meta.name.ifBlank { "Canal " + (channels.size + 1) },
                            streamUrl = url,
                            group = meta.group.ifBlank { "Sin categoría" },
                            logo = meta.logo,
                            tvgId = meta.tvgId
                        ))
                    }
                    pending = null
                }
            }
        }
        return channels
    }

    private fun parseExtInf(line: String): Metadata {
        val body = line.substringAfter(':', "")
        val split = titleComma(body)
        val attributes = if (split >= 0) body.substring(0, split) else body
        val title = if (split >= 0) body.substring(split + 1).trim() else ""
        val props = mutableMapOf<String, String>()
        for (match in attribute.findAll(attributes)) {
            val key = (match.groups[1]?.value ?: match.groups[3]?.value).orEmpty().lowercase()
            val value = (match.groups[2]?.value ?: match.groups[4]?.value).orEmpty()
            props[key] = value
        }
        return Metadata(
            name = title.ifBlank { props["tvg-name"].orEmpty() },
            group = props["group-title"].orEmpty(),
            logo = props["tvg-logo"]?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            tvgId = props["tvg-id"]
        )
    }

    private fun titleComma(body: String): Int {
        var quoted = false
        for (i in body.indices) {
            when (body[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return -1
    }

    private fun resolveStream(value: String, baseUrl: String?): String? = try {
        val url = URI(value)
        val resolved = if (url.isAbsolute || baseUrl == null) url else URI(baseUrl).resolve(url)
        if (resolved.scheme.equals("http", true) || resolved.scheme.equals("https", true))
            resolved.toASCIIString() else null
    } catch (_: Exception) {
        null
    }

    private data class Metadata(
        val name: String = "",
        val group: String = "",
        val logo: String? = null,
        val tvgId: String? = null
    )
}
