package com.ev.terminal.model

import android.content.Context
import com.ev.terminal.harness.AgentModel
import com.ev.terminal.harness.EVRuntime
import com.ev.terminal.harness.RuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val modelPackage = ModelPackageInstaller(context)
    val backend: EVModelBackend = LlamaCppBackend(context)

    private val _state = MutableStateFlow(
        if (modelPackage.isInstalled()) ModelState.READY else ModelState.NOT_INSTALLED
    )
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _progress = MutableStateFlow<ModelDownloadProgress?>(null)
    val progress: StateFlow<ModelDownloadProgress?> = _progress.asStateFlow()

    private val taskMutex = Mutex()
    private var downloadJob: Job? = null

    val modelName: String = MODEL_PACKAGE_NAME
    /** The model is downloaded separately and stored in app-private storage. */
    fun isInstalled(): Boolean = modelPackage.isInstalled()

    fun partialDownloadBytes(): Long = modelPackage.partialBytes()

    fun startDownload() {
        if (downloadJob?.isActive == true) return

        _error.value = null
        _state.value = ModelState.DOWNLOADING
        _progress.value = ModelDownloadProgress(modelPackage.partialBytes())
        runtime.eventBus.emit("model_download_start", "model" to modelName)

        downloadJob = runtime.scope.launch {
            try {
                modelPackage.download { progress ->
                    _progress.value = progress
                    runtime.eventBus.emit(
                        "model_download",
                        "percent" to progress.percent,
                        "mb" to (progress.downloadedBytes / (1024 * 1024))
                    )
                }
                _state.value = ModelState.READY
                runtime.eventBus.emit("model_download_done", "model" to modelName)
            } catch (cancelled: CancellationException) {
                _state.value = if (modelPackage.isInstalled()) {
                    ModelState.READY
                } else {
                    ModelState.NOT_INSTALLED
                }
                runtime.eventBus.emit("model_download_cancelled", "model" to modelName)
            } catch (error: Exception) {
                _state.value = ModelState.ERROR
                _error.value = error.message ?: "model download failed"
                runtime.eventBus.emit("model_download_error", "reason" to (_error.value ?: "unknown"))
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

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
        downloadJob?.cancel()
        // Active inference tasks unload the backend in runTask's finally block.
    }
}
