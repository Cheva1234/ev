package com.ev.terminal.tools

import com.ev.terminal.evcl.EvclCommand

enum class ToolStatus {
    SUCCESS,
    ERROR,
    PARTIAL,
    PERMISSION_REQUIRED,
    AMBIGUOUS,
    NOT_FOUND
}

data class ToolResult(
    val family: String,
    val status: ToolStatus,
    val summary: String,
    val detail: String = "",
    val durationMs: Long = 0
)

interface Tool {
    val family: String
    val operations: List<String>
    suspend fun execute(command: EvclCommand): ToolResult
}
