package com.ev.terminal.model

import android.content.Context
import com.ev.terminal.harness.AgentModel
import com.ev.terminal.harness.EVRuntime
import com.ev.terminal.harness.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    READY,
    LOADING,
    ACTIVE,
    UNLOADING,
    ERROR
}

class ModelSupervisor(
    private val runtime: EVRuntime,
    context: Context
) : AgentModel {

    val backend: EVModelBackend = LlamaCppBackend(context)

    private val _state = MutableStateFlow(ModelState.READY)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val taskMutex = Mutex()

    val modelName: String = BUNDLED_MODEL_NAME
    /** The model is part of the APK and copied to app storage on first use. */
    fun isInstalled(): Boolean = true

    override suspend fun generate(
        system: String,
        prompt: String,
        maxTokens: Int,
        onChunk: suspend (String) -> Unit
    ): String = runTask(
        system = system,
        prompt = prompt,
        maxTokens = maxTokens,
        onChunk = onChunk
    ).text

    suspend fun runTask(
        system: String,
        prompt: String,
        maxTokens: Int = 128,
        onChunk: suspend (String) -> Unit = {}
    ): ModelResponse = taskMutex.withLock {
        val taskId = runtime.state.nextTask()
        runtime.eventBus.emit("task_start", "task" to taskId)
        runtime.state.setRuntimeState(RuntimeState.AI)
        runtime.eventBus.emit("model_spawn", "task" to taskId)

        val loadStart = System.currentTimeMillis()
        try {
            backend.load()
        } catch (e: Exception) {
            runtime.state.setRuntimeState(RuntimeState.ERROR)
            runtime.eventBus.emit("model_load_error", "task" to taskId, "reason" to (e.message ?: "unknown"))
            throw e
        }
        val loadMs = System.currentTimeMillis() - loadStart
        _state.value = ModelState.ACTIVE
        runtime.eventBus.emit("model_loaded", "task" to taskId, "load_ms" to loadMs)

        val response = try {
            backend.generate(
                ModelRequest(
                    system = system,
                    prompt = prompt,
                    maxTokens = maxTokens,
                    temperature = runtime.settings.temperature,
                    topP = 0.9f
                ),
                onChunk
            )
        } finally {
            runtime.eventBus.emit("model_unload", "task" to taskId)
            try {
                backend.unload()
            } catch (e: Exception) {
                // best-effort
            }
            _state.value = ModelState.READY
            runtime.eventBus.emit("model_unloaded", "task" to taskId)
            runtime.state.setRuntimeState(RuntimeState.IDLE)
        }
        return response
    }

    fun shutdown() {
        // Active tasks unload the backend in runTask's finally block.
    }
}
