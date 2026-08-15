package com.ev.terminal.harness

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.evcl.EvclParser
import com.ev.terminal.tools.ToolRegistry
import com.ev.terminal.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

data class TaskOutcome(
    val taskId: Int,
    val family: String,
    val result: ToolResult,
    val durationMs: Long,
    val responseStreamed: Boolean = false
)

class TaskManager(
    private val runtime: EVRuntime,
    private val registry: ToolRegistry
) {

    suspend fun runEvcl(raw: String): TaskOutcome {
        val taskId = runtime.state.nextTask()
        runtime.eventBus.emit("task_start", "task" to taskId)
        runtime.state.setRuntimeState(RuntimeState.TOOL)

        val command = EvclParser.parse(raw)
        val family = familyOf(command)
        runtime.eventBus.emit("route", "task" to taskId, "family" to family)

        val start = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
            registry.execute(command)
        }
        val duration = System.currentTimeMillis() - start

        runtime.eventBus.emit(
            "tool",
            "task" to taskId,
            "name" to family.lowercase(),
            "status" to result.status.name,
            "duration_ms" to duration,
            "summary" to result.summary
        )
        runtime.state.addTask(
            TaskRecord(taskId, family, duration, result.status.name)
        )
        runtime.eventBus.emit("task_end", "task" to taskId, "status" to result.status.name)
        runtime.state.setRuntimeState(RuntimeState.IDLE)

        return TaskOutcome(taskId, family, result, duration)
    }

    private fun familyOf(command: EvclCommand): String = when (command) {
        is EvclCommand.Math -> "MATH"
        is EvclCommand.Time -> "TIME"
        is EvclCommand.Weather -> "WEATHER"
        is EvclCommand.Web -> "WEB"
        is EvclCommand.Mail -> "MAIL"
        is EvclCommand.Location -> "LOCATION"
        is EvclCommand.Unknown -> "UNKNOWN"
    }
}
