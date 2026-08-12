package com.ev.terminal.observability

import android.content.Context
import com.ev.terminal.harness.EvEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JsonlLogger(context: Context) {
    private val root = File(context.filesDir, ".ev/logs")
    private val runtimeFile = File(root, "runtime.jsonl")
    private val memoryFile = File(root, "memory.jsonl")
    private val modelFile = File(root, "model.jsonl")
    private val toolsFile = File(root, "tools.jsonl")

    init {
        root.mkdirs()
    }

    fun log(event: EvEvent) {
        val line = event.toJson() + "\n"
        when (event.event) {
            "ram" -> memoryFile.appendText(line)
            "model_loaded", "model_unloaded", "model_ready" -> modelFile.appendText(line)
            "tool" -> toolsFile.appendText(line)
            else -> runtimeFile.appendText(line)
        }
    }

    fun deleteAll() {
        listOf(runtimeFile, memoryFile, modelFile, toolsFile).forEach { it.writeText("") }
    }

    fun logFileNames(): List<String> =
        listOf("runtime.jsonl", "memory.jsonl", "model.jsonl", "tools.jsonl")

    companion object {
        private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fun stamp(): String = fmt.format(Date())
    }
}
