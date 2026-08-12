package com.ev.terminal.model

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.ev.terminal.harness.EVRuntime
import com.ev.terminal.harness.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val ggufDownloader = GgufDownloader(context)
    val backend: EVModelBackend = LlamaCppBackend(context)

    private fun bothReady(): Boolean = ggufDownloader.isDownloaded() && (backend as LlamaCppBackend).cliExists()

    private val _state = MutableStateFlow(
        if (bothReady()) ModelState.READY
        else if (ggufDownloader.isDownloaded()) ModelState.ERROR
        else ModelState.NOT_INSTALLED
    )
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<GgufDownloadProgress?>(null)
    val progress: StateFlow<GgufDownloadProgress?> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var downloadJob: Job? = null
    private val taskMutex = Mutex()

    val modelName: String = "LFM2.5-2.6B Q4_K_M (on-device)"
    val modelSizeBytes: Long = ggufDownloader.modelSizeBytes

    fun isInstalled(): Boolean = _state.value == ModelState.READY

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        _state.value = ModelState.DOWNLOADING
        _error.value = null
        runtime.eventBus.emit("model_download_start", "model" to modelName)
        downloadJob = scope.launch {
            try {
                ggufDownloader.progress.collect { p ->
                    _progress.value = p
                    runtime.eventBus.emit(
                        "model_download",
                        "percent" to String.format("%.1f", p.percent),
                        "mb" to (p.downloadedBytes / (1024 * 1024))
                    )
                }
            } catch (e: Exception) {
                // collector cancelled
            }
        }
        scope.launch {
            try {
                val dlJob = scope.launch {
                    ggufDownloader.download()
                }
                var lastActivity = System.currentTimeMillis()
                val watchdog = scope.launch {
                    while (dlJob.isActive) {
                        kotlinx.coroutines.delay(5000)
                        val p = _progress.value
                        if (p != null && p.downloadedBytes > 0) {
                            lastActivity = System.currentTimeMillis()
                        }
                        if (System.currentTimeMillis() - lastActivity > 30000) {
                            dlJob.cancel()
                            throw RuntimeException("download stalled (no progress for 30s)")
                        }
                    }
                }
                dlJob.join()
                watchdog.cancel()
                if (bothReady()) {
                    _state.value = ModelState.READY
                    _progress.value = GgufDownloadProgress(modelSizeBytes, modelSizeBytes, 100.0)
                    runtime.eventBus.emit("model_download_done", "model" to modelName)
                } else {
                    val cliOk = (backend as LlamaCppBackend).cliExists()
                    _state.value = ModelState.ERROR
                    _error.value = if (!cliOk) "llama-cli binary missing from APK" else "model file incomplete"
                    runtime.eventBus.emit("model_download_error", "reason" to (_error.value ?: "unknown"))
                }
            } catch (e: Exception) {
                _state.value = ModelState.ERROR
                _error.value = e.message ?: "download failed"
                runtime.eventBus.emit("model_download_error", "reason" to (e.message ?: "unknown"))
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _state.value = ModelState.NOT_INSTALLED
        _progress.value = null
        runtime.eventBus.emit("model_download_cancelled", "model" to modelName)
    }

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

    fun freeSpaceBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBytes
        } catch (e: Exception) {
            -1
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
