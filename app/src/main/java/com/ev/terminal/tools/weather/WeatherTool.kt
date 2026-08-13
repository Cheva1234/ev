package com.ev.terminal.tools.weather

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.Tool
import com.ev.terminal.tools.ToolResult
import com.ev.terminal.tools.ToolStatus
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherTool : Tool {
    override val family = "WEATHER"
    override val operations = listOf("current", "today", "tomorrow", "forecast")
    override val usage = "WEATHER: live weather for a named location. Requires network. " +
        "Examples: @weather current Bangkok, @weather forecast Chiang Mai days:3"

    override suspend fun execute(command: EvclCommand): ToolResult {
        val cmd = command as? EvclCommand.Weather ?: return error("bad command")
        if (cmd.location.isBlank()) {
            return ToolResult(family, ToolStatus.AMBIGUOUS, "location required", "WEATHER_RESULT\nstatus=AMBIGUOUS\nreason=missing_location")
        }
        return try {
            val data = fetch(cmd.location)
            val normalized = normalize(data, cmd.location)
            val summary = when (cmd.operation) {
                "current" -> "${data.optString("condition", "unknown")}, ${data.optInt("temp", 0)}C"
                "today" -> "${data.optString("condition", "unknown")}, high ${data.optInt("high", 0)}C low ${data.optInt("low", 0)}C"
                "tomorrow" -> "${data.optString("condition", "unknown")}, high ${data.optInt("high", 0)}C low ${data.optInt("low", 0)}C"
                else -> "${data.optString("condition", "unknown")}, high ${data.optInt("high", 0)}C low ${data.optInt("low", 0)}C"
            }
            ToolResult(family, ToolStatus.SUCCESS, summary, normalized)
        } catch (e: Exception) {
            val reason = if (e is java.net.UnknownHostException || e is java.net.ConnectException) {
                "network_unavailable"
            } else {
                "provider_error"
            }
            ToolResult(family, ToolStatus.ERROR, "Network unavailable.", "WEATHER_RESULT\nstatus=ERROR\nreason=$reason")
        }
    }

    private fun fetch(location: String): JSONObject {
        val url = URL("https://wttr.in/${java.net.URLEncoder.encode(location, "UTF-8")}?format=j1")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        if (code != 200) throw RuntimeException("http $code")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val root = JSONObject(body)
        val current = root.getJSONArray("current_condition").getJSONObject(0)
        val today = root.getJSONArray("weather").getJSONObject(0)
        val tomorrow = root.getJSONArray("weather").getJSONObject(1)
        val condition = current.optJSONArray("weatherDesc")
            ?.optJSONObject(0)
            ?.optString("value", "unknown")
            ?: "unknown"
        return JSONObject().apply {
            put("condition", condition)
            put("temp", current.optInt("temp_C", 0))
            put("rain", current.optString("precipMM", "0"))
            put("high", today.optInt("maxtempC", 0))
            put("low", today.optInt("mintempC", 0))
            put("tomorrow_high", tomorrow.optInt("maxtempC", 0))
            put("tomorrow_low", tomorrow.optInt("mintempC", 0))
        }
    }

    private fun normalize(data: JSONObject, location: String): String {
        val sb = StringBuilder()
        sb.append("WEATHER\n")
        sb.append("location=$location\n")
        sb.append("condition=${data.optString("condition", "unknown")}\n")
        sb.append("temp=${data.optInt("temp", 0)}C\n")
        sb.append("rain=${data.optString("rain", "0")}mm\n")
        sb.append("high=${data.optInt("high", 0)}C\n")
        sb.append("low=${data.optInt("low", 0)}C\n")
        return sb.toString()
    }

    private fun error(msg: String): ToolResult =
        ToolResult(family, ToolStatus.ERROR, msg, "WEATHER_RESULT\nstatus=ERROR\nreason=$msg")
}
