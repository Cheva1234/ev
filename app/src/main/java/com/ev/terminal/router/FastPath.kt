package com.ev.terminal.router

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.evcl.EvclParser
import com.ev.terminal.tools.ToolRegistry
import com.ev.terminal.tools.ToolResult

class FastPath(private val registry: ToolRegistry) {

    suspend fun tryResolve(input: String): ToolResult? {
        val trimmed = input.trim()
        if (trimmed.startsWith("@")) {
            val command = EvclParser.parse(trimmed)
            if (command is EvclCommand.Unknown) return null
            return registry.execute(command)
        }
        return null
    }
}
