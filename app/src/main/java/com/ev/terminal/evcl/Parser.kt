package com.ev.terminal.evcl

sealed class EvclCommand {
    data class Math(val expression: String) : EvclCommand()
    data class Time(val operation: String, val args: List<String>) : EvclCommand()
    data class Weather(val operation: String, val location: String, val days: Int) : EvclCommand()
    data class Web(val operation: String, val query: String, val id: Int?) : EvclCommand()
    data class Mail(val operation: String, val query: String, val id: Int?) : EvclCommand()
    data class Location(val operation: String, val args: List<String>) : EvclCommand()
    data class Unknown(val raw: String) : EvclCommand()
}

object EvclParser {

    fun parse(raw: String): EvclCommand {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("@")) return EvclCommand.Unknown(trimmed)

        val spaceIdx = trimmed.indexOf(' ')
        val head = if (spaceIdx > 0) trimmed.substring(0, spaceIdx) else trimmed
        val rest = if (spaceIdx > 0) trimmed.substring(spaceIdx + 1).trim() else ""

        return when (head) {
            "@math" -> EvclCommand.Math(rest)
            "@time" -> parseTime(rest)
            "@weather" -> parseWeather(rest)
            "@web" -> parseWeb(rest)
            "@mail" -> parseMail(rest)
            "@loc" -> parseLocation(rest)
            else -> EvclCommand.Unknown(trimmed)
        }
    }

    private fun parseTime(rest: String): EvclCommand {
        if (rest.isEmpty()) return EvclCommand.Time("now", emptyList())
        val parts = splitArgs(rest)
        return when (parts.first()) {
            "now" -> EvclCommand.Time("now", emptyList())
            "diff" -> EvclCommand.Time("diff", parts.drop(1))
            "convert" -> EvclCommand.Time("convert", parts.drop(1))
            else -> EvclCommand.Time("zone", parts)
        }
    }

    private fun parseWeather(rest: String): EvclCommand {
        val parts = splitArgs(rest)
        if (parts.isEmpty()) return EvclCommand.Weather("current", "", 0)
        val op = parts[0]
        val location = parts.getOrElse(1) { "" }
        var days = 0
        if (op == "forecast") {
            val daysArg = parts.firstOrNull { it.startsWith("days:") }
            days = daysArg?.removePrefix("days:")?.toIntOrNull() ?: 3
        }
        return EvclCommand.Weather(op, location, days)
    }

    private fun parseWeb(rest: String): EvclCommand {
        val parts = splitArgs(rest)
        if (parts.isEmpty()) return EvclCommand.Web("search", "", null)
        val op = parts[0]
        return when (op) {
            "read" -> EvclCommand.Web("read", "", parts.getOrNull(1)?.toIntOrNull())
            else -> EvclCommand.Web("search", parts.drop(1).joinToString(" "), null)
        }
    }

    private fun parseMail(rest: String): EvclCommand {
        val parts = splitArgs(rest)
        if (parts.isEmpty()) return EvclCommand.Mail("latest", "", null)
        val op = parts[0]
        return when (op) {
            "latest" -> EvclCommand.Mail("latest", "", null)
            "read" -> EvclCommand.Mail("read", "", parts.getOrNull(1)?.toIntOrNull())
            else -> EvclCommand.Mail("search", parts.drop(1).joinToString(" "), null)
        }
    }

    private fun parseLocation(rest: String): EvclCommand {
        val parts = splitArgs(rest)
        if (parts.isEmpty()) return EvclCommand.Location("current", emptyList())
        return EvclCommand.Location(parts[0], parts.drop(1))
    }

    private fun splitArgs(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        input.forEach { c ->
            when {
                c == '"' -> inQuote = !inQuote
                c == ' ' && !inQuote -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
