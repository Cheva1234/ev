package com.ev.terminal.tools.mail

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.Tool
import com.ev.terminal.tools.ToolResult
import com.ev.terminal.tools.ToolStatus

class MailTool : Tool {
    override val family = "MAIL"
    override val operations = listOf("latest", "search", "read")

    override suspend fun execute(command: EvclCommand): ToolResult {
        val cmd = command as? EvclCommand.Mail ?: return error("bad command")
        return when (cmd.operation) {
            "latest" -> ToolResult(
                family, ToolStatus.PERMISSION_REQUIRED,
                "Mail access not configured.",
                "MAIL_RESULT\nstatus=PERMISSION_REQUIRED\nreason=not_configured"
            )
            "search" -> ToolResult(
                family, ToolStatus.PERMISSION_REQUIRED,
                "Mail access not configured.",
                "MAIL_RESULT\nstatus=PERMISSION_REQUIRED\nreason=not_configured"
            )
            "read" -> ToolResult(
                family, ToolStatus.PERMISSION_REQUIRED,
                "Mail access not configured.",
                "MAIL_RESULT\nstatus=PERMISSION_REQUIRED\nreason=not_configured"
            )
            else -> error("unknown operation")
        }
    }

    private fun error(msg: String): ToolResult =
        ToolResult(family, ToolStatus.ERROR, msg, "MAIL_RESULT\nstatus=ERROR\nreason=$msg")
}
