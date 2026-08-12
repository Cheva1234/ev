package com.ev.terminal.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LlamaCppBackend(
    private val context: Context
) : EVModelBackend {

    private var process: Process? = null
    private var loaded = false

    private fun nativeLibDir(): File {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" } ?: "arm64-v8a"
        return File(context.applicationInfo.nativeLibraryDir)
    }

    private fun modelFile(): File = File(context.filesDir, ".ev/models/lfm2.5-2.6b-q4_k_m.gguf")

    override suspend fun load() {
        withContext(Dispatchers.IO) {
            val model = modelFile()
            if (!model.exists()) throw RuntimeException("model file not found: ${model.absolutePath}")
            val cli = File(nativeLibDir(), "llama-cli")
            if (!cli.exists()) throw RuntimeException("llama-cli not found")
            cli.setExecutable(true)
            loaded = true
        }
    }

    override suspend fun generate(request: ModelRequest): ModelResponse {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cli = File(nativeLibDir(), "llama-cli")
            val model = modelFile()
            val prompt = if (request.system.isNotBlank()) {
                "${request.system}\n\n${request.prompt}"
            } else {
                request.prompt
            }
            val cmd = listOf(
                cli.absolutePath,
                "-m", model.absolutePath,
                "-p", prompt,
                "-n", request.maxTokens.toString(),
                "--temp", request.temperature.toString(),
                "--top-p", request.topP.toString(),
                "-c", "4096",
                "-st",
                "-t", "4"
            )
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            process = proc
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val exit = proc.waitFor()
            process = null
            if (exit != 0) {
                throw RuntimeException("llama-cli exited $exit: ${output.take(200)}")
            }
            val durationMs = System.currentTimeMillis() - start
            val text = parseOutput(output)
            ModelResponse(
                text = text,
                durationMs = durationMs,
                tokPerSec = if (durationMs > 0) text.length / 4.0 * 1000 / durationMs else 0.0
            )
        }
    }

    private fun parseOutput(output: String): String {
        var text = output
        if (text.contains("[End thinking]")) {
            text = text.substringAfterLast("[End thinking]")
        }
        text = text.substringBefore("[ Prompt:")
        text = text.substringBefore("Exiting...")
        text = text.replace(Regex("\\[Start thinking\\].*?\\[End thinking\\]", RegexOption.DOT_MATCHES_ALL), "")
        return text.trim()
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            try {
                process?.destroy()
                process?.waitFor()
            } catch (e: Exception) {
                // best-effort
            }
            process = null
            loaded = false
        }
    }

    override suspend fun status(): ModelStatus = ModelStatus(loaded, "LFM2.5-2.6B Q4_K_M")
}
