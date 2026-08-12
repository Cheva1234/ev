package com.ev.terminal.router

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.evcl.EvclParser
import com.ev.terminal.tools.ToolRegistry
import com.ev.terminal.tools.ToolResult
import com.ev.terminal.tools.ToolStatus
import java.util.Locale

class FastPath(private val registry: ToolRegistry) {

    suspend fun tryResolve(input: String): ToolResult? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("@")) {
            val command = EvclParser.parse(trimmed)
            if (command is EvclCommand.Unknown) return null
            return registry.execute(command)
        }

        return routeNaturalLanguage(trimmed)
    }

    private suspend fun routeNaturalLanguage(input: String): ToolResult? {
        val lower = input.lowercase(Locale.US)

        val math = matchMath(lower)
        if (math != null) return registry.execute(EvclCommand.Math(math))

        if (lower.contains("time")) {
            val zone = extractQuotedOrAfter(lower, "in ")
            if (zone != null) {
                return registry.execute(EvclCommand.Time("zone", listOf(zone)))
            }
            return registry.execute(EvclCommand.Time("now", emptyList()))
        }

        if (lower.contains("weather") || lower.contains("rain") || lower.contains("forecast")) {
            val location = extractQuotedOrAfter(lower, "in ")
            val op = when {
                lower.contains("tomorrow") -> "tomorrow"
                lower.contains("forecast") -> "forecast"
                else -> "current"
            }
            val cmd = if (location != null) {
                EvclCommand.Weather(op, location, if (op == "forecast") 3 else 0)
            } else {
                EvclCommand.Weather(op, "", 0)
            }
            return registry.execute(cmd)
        }

        if (lower.contains("search") || lower.contains("look up") || lower.contains("google")) {
            val query = input.substringAfter("search", input)
                .substringAfter("look up", input)
                .substringAfter("google", input)
                .trim()
                .trimStart(':', ' ')
            if (query.isNotEmpty() && query != input) {
                return registry.execute(EvclCommand.Web("search", query, null))
            }
        }

        if (lower.contains("email") || lower.contains("mail")) {
            return registry.execute(EvclCommand.Mail("latest", "", null))
        }

        if (lower.contains("where am i") || lower.contains("my location") || lower.contains("location")) {
            return registry.execute(EvclCommand.Location("current", emptyList()))
        }

        return null
    }

    private fun matchMath(lower: String): String? {
        val expression = when {
            lower.startsWith("what is ") || lower.startsWith("what's ") ->
                lower.removePrefix("what is ").removePrefix("what's ").trim()
            lower.startsWith("calculate ") || lower.startsWith("compute ") ->
                lower.removePrefix("calculate ").removePrefix("compute ").trim()
            lower.startsWith("solve ") ->
                lower.removePrefix("solve ").trim()
            lower.startsWith("differentiate ") || lower.startsWith("integrate ") -> {
                val expr = lower.removePrefix("differentiate ").removePrefix("integrate ").trim()
                if (expr.isNotEmpty()) {
                    val op = if (lower.startsWith("differentiate")) "diff" else "integrate"
                    return "$op(${normalizeMathWords(expr)},x)"
                }
                return null
            }
            else -> null
        }
        if (expression != null && expression.isNotEmpty()) {
            return expression
        }
        if (lower.matches(Regex("^[0-9().+\\-*/^%\\s]+$"))) {
            return lower
        }
        return null
    }

    private fun normalizeMathWords(expr: String): String {
        var e = expr
        e = e.replace("squared", "^2").replace("cubed", "^3")
        e = e.replace("times", "*").replace("multiplied by", "*")
        e = e.replace("divided by", "/").replace("plus", "+").replace("minus", "-")
        e = e.replace("sin x", "sin(x)").replace("cos x", "cos(x)").replace("tan x", "tan(x)")
        return e
    }

    private fun extractQuotedOrAfter(lower: String, marker: String): String? {
        val quoted = Regex("\"([^\"]+)\"").find(lower)?.groupValues?.get(1)
        if (quoted != null) return quoted
        if (lower.contains(marker)) {
            val after = lower.substringAfter(marker).trim()
            if (after.isNotEmpty() && after.length < 40) return after
        }
        return null
    }
}
