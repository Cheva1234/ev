package com.ev.terminal.tools.location

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.Tool
import com.ev.terminal.tools.ToolResult
import com.ev.terminal.tools.ToolStatus

class LocationTool : Tool {
    override val family = "LOCATION"
    override val operations = listOf("current", "distance", "nearby", "geocode")
    override val usage = "LOCATION: device location. Example: @loc current"

    override suspend fun execute(command: EvclCommand): ToolResult {
        val cmd = command as? EvclCommand.Location ?: return error("bad command")
        return when (cmd.operation) {
            "current" -> ToolResult(
                family, ToolStatus.PERMISSION_REQUIRED,
                "Location permission not granted.",
                "LOCATION_RESULT\nstatus=PERMISSION_REQUIRED\nreason=permission"
            )
            "distance" -> {
                if (cmd.args.size < 2) {
                    return ToolResult(family, ToolStatus.AMBIGUOUS, "two places required", "LOCATION_RESULT\nstatus=AMBIGUOUS\nreason=missing_args")
                }
                ToolResult(
                    family, ToolStatus.PERMISSION_REQUIRED,
                    "Location permission not granted.",
                    "LOCATION_RESULT\nstatus=PERMISSION_REQUIRED\nreason=permission"
                )
            }
            "nearby" -> ToolResult(
                family, ToolStatus.PERMISSION_REQUIRED,
                "Location permission not granted.",
                "LOCATION_RESULT\nstatus=PERMISSION_REQUIRED\nreason=permission"
            )
            "geocode" -> ToolResult(
                family, ToolStatus.PERMISSION_REQUIRED,
                "Location permission not granted.",
                "LOCATION_RESULT\nstatus=PERMISSION_REQUIRED\nreason=permission"
            )
            else -> error("unknown operation")
        }
    }

    private fun error(msg: String): ToolResult =
        ToolResult(family, ToolStatus.ERROR, msg, "LOCATION_RESULT\nstatus=ERROR\nreason=$msg")
}
