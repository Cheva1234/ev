package com.ev.terminal.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LlamaCppBackend(
    private val context: Context
) : EVModelBackend {

    private var process: Process? = null
    private var loaded = false
    private var cliBin: File? = null

    private fun nativeLibDir(): File =
        File(context.applicationInfo.nativeLibraryDir)

    private fun modelFile(): File = File(context.filesDir, ".ev/models/lfm2.5-2.6b-q4_k_m.gguf")

    private fun cliFile(): File {
        cliBin?.let { if (it.exists()) return it }
        val src = File(nativeLibDir(), "libllama-cli.so")
        if (!src.exists()) {
            Log.e("EV_MODEL", "libllama-cli.so missing in ${nativeLibDir().absolutePath}")
            throw RuntimeException("libllama-cli.so not found in nativeLibraryDir=${nativeLibDir().absolutePath}")
        }
        Log.i("EV_MODEL", "nativeLibraryDir=${nativeLibDir().absolutePath}")
        Log.i("EV_MODEL", "cli=${src.absolutePath}, exists=${src.exists()}, size=${src.length()}")
        src.setExecutable(true, false)
        Log.i("EV_MODEL", "cli exec=${src.canExecute()}")
        cliBin = src
        return src
    }

    override suspend fun load() {
        withContext(Dispatchers.IO) {
            val model = modelFile()
            Log.i("EV_MODEL", "model=${model.absolutePath}, exists=${model.exists()}, size=${model.length()}")
            if (!model.exists() || model.length() == 0L) {
                throw RuntimeException("model file not found or empty: ${model.absolutePath} (size=${model.length()})")
            }
            val cli = cliFile()
            if (!cli.exists() || !cli.canExecute()) {
                throw RuntimeException("llama-cli not usable: ${cli.absolutePath} (exists=${cli.exists()}, exec=${cli.canExecute()})")
            }
            loaded = true
        }
    }

    override suspend fun generate(request: ModelRequest): ModelResponse {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val cli = cliFile()
            cli.setExecutable(true, false)
            val model = modelFile()
            if (!model.exists()) {
                throw RuntimeException("model file missing: ${model.absolutePath}")
            }
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
            Log.i("EV_MODEL", "spawning: ${cli.absolutePath} -m ${model.absolutePath} -st -n ${request.maxTokens}")
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] = nativeLibDir().absolutePath
            val proc = pb.start()
            process = proc
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val exit = proc.waitFor()
            process = null
            Log.i("EV_MODEL", "llama-cli exited $exit, output length=${output.length}")
            if (exit != 0) {
                throw RuntimeException("llama-cli exited $exit: ${output.take(300)}")
            }
            val durationMs = System.currentTimeMillis() - start
            val text = parseOutput(output)
            Log.i("EV_MODEL", "parsed text length=${text.length}, tok/s=${if (durationMs > 0) text.length / 4.0 * 1000 / durationMs else 0.0}")
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

    fun cliExists(): Boolean = File(nativeLibDir(), "libllama-cli.so").exists()
    fun modelExists(): Boolean = modelFile().exists() && modelFile().length() > 0
}
