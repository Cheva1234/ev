package com.ev.terminal.tools.web

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.Tool
import com.ev.terminal.tools.ToolResult
import com.ev.terminal.tools.ToolStatus
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebTool : Tool {
    override val family = "WEB"
    override val operations = listOf("search", "read")
    override val usage = "WEB: web search for live, current or factual information. Requires network. " +
        "Examples: @web search Formula Student rules 2026"

    override suspend fun execute(command: EvclCommand): ToolResult {
        val cmd = command as? EvclCommand.Web ?: return error("bad command")
        return try {
            when (cmd.operation) {
                "search" -> search(cmd.query)
                "read" -> read(cmd.id)
                else -> error("unknown operation")
            }
        } catch (e: Exception) {
            val reason = if (e is java.net.UnknownHostException || e is java.net.ConnectException) {
                "network_unavailable"
            } else {
                "provider_error"
            }
            ToolResult(family, ToolStatus.ERROR, "Network unavailable.", "WEB_RESULT\nstatus=ERROR\nreason=$reason")
        }
    }

    private fun search(query: String): ToolResult {
        if (query.isBlank()) {
            return ToolResult(family, ToolStatus.AMBIGUOUS, "query required", "WEB_RESULT\nstatus=AMBIGUOUS\nreason=missing_query")
        }
        val url = URL("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) EV/0.1")
        val code = conn.responseCode
        if (code != 200) throw RuntimeException("http $code")
        val html = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val results = parseResults(html)
        if (results.isEmpty()) {
            return ToolResult(family, ToolStatus.NOT_FOUND, "no results", "WEB_RESULT\nstatus=NOT_FOUND")
        }
        val sb = StringBuilder("WEB_RESULTS\n")
        results.take(5).forEachIndexed { i, r ->
            sb.append("${i + 1} | ${r.title} | ${r.source} | ${r.snippet}\n")
        }
        return ToolResult(family, ToolStatus.SUCCESS, "${results.size} results", sb.toString())
    }

    private fun read(id: Int?): ToolResult {
        if (id == null) {
            return ToolResult(family, ToolStatus.AMBIGUOUS, "id required", "WEB_RESULT\nstatus=AMBIGUOUS\nreason=missing_id")
        }
        return ToolResult(
            family, ToolStatus.NOT_FOUND,
            "no cached result $id",
            "WEB_RESULT\nstatus=NOT_FOUND\nid=$id"
        )
    }

    private fun parseResults(html: String): List<WebResult> {
        val results = mutableListOf<WebResult>()
        val resultRegex = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>")
        val snippetRegex = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>")
        val titleMatches = resultRegex.findAll(html).toList()
        val snippetMatches = snippetRegex.findAll(html).toList()
        titleMatches.forEachIndexed { i, m ->
            val href = m.groupValues[1]
            val title = stripHtml(m.groupValues[2])
            val snippet = if (i < snippetMatches.size) stripHtml(snippetMatches[i].groupValues[1]) else ""
            val source = extractSource(href)
            results.add(WebResult(title, source, snippet))
        }
        return results
    }

    private fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&#x27;", "'").trim()

    private fun extractSource(href: String): String = try {
        val host = URL(href).host
        host.removePrefix("www.")
    } catch (e: Exception) {
        "unknown"
    }

    private fun error(msg: String): ToolResult =
        ToolResult(family, ToolStatus.ERROR, msg, "WEB_RESULT\nstatus=ERROR\nreason=$msg")

    private data class WebResult(val title: String, val source: String, val snippet: String)
}
