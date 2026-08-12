package com.ev.terminal.harness

import android.content.Context
import com.ev.terminal.model.ModelSupervisor
import com.ev.terminal.observability.JsonlLogger
import com.ev.terminal.observability.MemoryMonitor
import com.ev.terminal.router.FastPath
import com.ev.terminal.storage.SessionStore
import com.ev.terminal.storage.SettingsStore
import com.ev.terminal.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EVRuntime private constructor(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val eventBus = EventBus()
    val state = StateStore()
    val settings = SettingsStore(context)
    val sessionStore = SessionStore(context)
    val logger = JsonlLogger(context)
    val memoryMonitor = MemoryMonitor()
    val toolRegistry = ToolRegistry()
    val taskManager = TaskManager(this, toolRegistry)
    val fastPath = FastPath(toolRegistry)
    val modelSupervisor = ModelSupervisor(this, context)

    private val _statusLine = MutableStateFlow("IDLE")
    val statusLine: StateFlow<String> = _statusLine.asStateFlow()

    init {
        scope.launch {
            state.runtimeState.collect { s ->
                _statusLine.value = when (s) {
                    RuntimeState.IDLE -> "IDLE"
                    RuntimeState.AI -> "LFM ACTIVE"
                    RuntimeState.TOOL -> "TOOL"
                    RuntimeState.ERROR -> "ERROR"
                }
            }
        }
        scope.launch {
            memoryMonitor.samples.collect { sample ->
                val mem = state.memory.value
                state.updateMemory(
                    mem.copy(
                        systemTotalMb = sample.totalMb,
                        currentMb = sample.usedMb
                    )
                )
                eventBus.emit("ram", "used_mb" to sample.usedMb)
            }
        }
        scope.launch {
            memoryMonitor.sampleLoop(context)
        }
        scope.launch {
            eventBus.events.collect { event ->
                logger.log(event)
            }
        }
        eventBus.emit("runtime_start", "session" to state.sessionId.value)
    }

    fun newSession() {
        state.newSession()
        sessionStore.archiveCurrent()
        eventBus.emit("session_new", "session" to state.sessionId.value)
    }

    fun shutdown() {
        modelSupervisor.shutdown()
        scope.cancel()
    }

    companion object {
        @Volatile
        private var instance: EVRuntime? = null

        fun get(context: Context): EVRuntime {
            return instance ?: synchronized(this) {
                instance ?: EVRuntime(context.applicationContext).also { instance = it }
            }
        }
    }
}
