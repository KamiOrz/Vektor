package com.vektor.app

import java.net.HttpURLConnection
import java.net.URL

class M3uRepository {
    suspend fun validateAndParse(url: String): Result<List<Channel>> = runCatching {
        require(isHttpUrl(url)) { "Only http and https M3U URLs are supported." }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.requestMethod = "GET"
        connection.inputStream.bufferedReader().use { reader ->
            parse(reader.readText(), url)
        }
    }

    fun parse(body: String, sourceUrl: String? = null): List<Channel> {
        val lines = body
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        require(lines.any { it.startsWith("#EXTM3U") || it.startsWith("#EXTINF") }) {
            "The link did not return a valid M3U playlist."
        }

        val channels = mutableListOf<Channel>()
        var pending: ParsedInfo? = null

        lines.forEach { line ->
            when {
                line.startsWith("#EXTINF") -> pending = parseInfo(line)
                line.startsWith("#") -> Unit
                else -> {
                    val streamUrl = normalizeUrl(line, sourceUrl) ?: return@forEach
                    val index = channels.size + 1
                    val info = pending
                    channels += Channel(
                        id = "$index-${streamUrl.hashCode()}",
                        streamUrl = streamUrl,
                        title = info?.title?.takeIf { it.isNotBlank() } ?: "Channel $index",
                        logoUrl = info?.logo?.let { normalizeUrl(it, sourceUrl) },
                        group = info?.group?.takeIf { it.isNotBlank() } ?: "Ungrouped",
                        index = index
                    )
                    pending = null
                }
            }
        }

        require(channels.isNotEmpty()) { "No playable channels were found in this playlist." }
        return channels
    }

    companion object {
        fun isHttpUrl(raw: String): Boolean = raw.startsWith("http://") || raw.startsWith("https://")

        private fun normalizeUrl(raw: String, sourceUrl: String?): String? {
            if (isHttpUrl(raw)) return raw
            return sourceUrl?.let {
                runCatching { URL(URL(it), raw).toString() }.getOrNull()
            }?.takeIf(::isHttpUrl)
        }

        private fun parseInfo(line: String): ParsedInfo {
            val title = line.substringAfter(",", missingDelimiterValue = "").trim()
            return ParsedInfo(
                title = title,
                logo = attr("tvg-logo", line),
                group = attr("group-title", line)
            )
        }

        private fun attr(name: String, line: String): String? {
            val regex = Regex("""$name="([^"]*)"""")
            return regex.find(line)?.groupValues?.getOrNull(1)
        }
    }
}

private data class ParsedInfo(
    val title: String,
    val logo: String?,
    val group: String?
)
