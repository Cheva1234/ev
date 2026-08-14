package com.ev.terminal.harness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RuntimeState {
    IDLE,
    AI,
    TOOL,
    ERROR
}

data class TaskRecord(
    val id: Int,
    val family: String,
    val durationMs: Long,
    val status: String
)

data class MemorySnapshot(
    val systemTotalMb: Long,
    val baselineMb: Long,
    val currentMb: Long,
    val lastPeakMb: Long,
    val modelRamMb: Long,
    val cleanupDeltaMb: Long
)

data class ModelSnapshot(
    val name: String,
    val quant: String,
    val state: String,
    val lastLoadMs: Long,
    val tokPerSec: Double,
    val promptTokens: Int,
    val outputTokens: Int,
    val contextUsed: Int,
    val contextMax: Int,
    val lastTask: Int
)

class StateStore {
    private val _runtimeState = MutableStateFlow(RuntimeState.IDLE)
    val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()

    private val _sessionId = MutableStateFlow(1)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    private val _taskCounter = MutableStateFlow(0)
    val taskCounter: StateFlow<Int> = _taskCounter.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskRecord>>(emptyList())
    val tasks: StateFlow<List<TaskRecord>> = _tasks.asStateFlow()

    private val _memory = MutableStateFlow(
        MemorySnapshot(0, 0, 0, 0, 0, 0)
    )
    val memory: StateFlow<MemorySnapshot> = _memory.asStateFlow()

    private val _model = MutableStateFlow(
        ModelSnapshot("qwen3.5:0.8b", "GGUF bundled", "UNLOADED", 0, 0.0, 0, 0, 0, 4096, 0)
    )
    val model: StateFlow<ModelSnapshot> = _model.asStateFlow()

    fun setRuntimeState(state: RuntimeState) {
        _runtimeState.value = state
    }

    fun newSession() {
        _sessionId.value += 1
    }

    fun nextTask(): Int {
        _taskCounter.value += 1
        return _taskCounter.value
    }

    fun addTask(record: TaskRecord) {
        _tasks.value = listOf(record) + _tasks.value.take(49)
    }

    fun updateMemory(snapshot: MemorySnapshot) {
        _memory.value = snapshot
    }

    fun updateModel(snapshot: ModelSnapshot) {
        _model.value = snapshot
    }
}
