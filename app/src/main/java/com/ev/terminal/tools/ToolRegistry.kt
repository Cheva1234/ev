package com.ev.terminal.tools

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.location.LocationTool
import com.ev.terminal.tools.mail.MailTool
import com.ev.terminal.tools.math.MathTool
import com.ev.terminal.tools.time.TimeTool
import com.ev.terminal.tools.weather.WeatherTool
import com.ev.terminal.tools.web.WebTool

class ToolRegistry {
    private val tools: Map<String, Tool> = listOf(
        MathTool(),
        TimeTool(),
        WeatherTool(),
        WebTool(),
        MailTool(),
        LocationTool()
    ).associateBy { it.family }

    fun get(family: String): Tool? = tools[family.uppercase()]

    fun all(): List<Tool> = tools.values.toList()

    fun families(): List<String> = tools.keys.toList()

    suspend fun execute(command: EvclCommand): ToolResult {
        val family = when (command) {
            is EvclCommand.Math -> "MATH"
            is EvclCommand.Time -> "TIME"
            is EvclCommand.Weather -> "WEATHER"
            is EvclCommand.Web -> "WEB"
            is EvclCommand.Mail -> "MAIL"
            is EvclCommand.Location -> "LOCATION"
            is EvclCommand.Unknown -> return ToolResult(
                "UNKNOWN",
                ToolStatus.ERROR,
                "unrecognized command",
                "RESULT\nstatus=ERROR\nreason=unknown_command"
            )
        }
        return get(family)?.execute(command) ?: ToolResult(
            family,
            ToolStatus.ERROR,
            "tool not available",
            "RESULT\nstatus=ERROR\nreason=tool_unavailable"
        )
    }
}
