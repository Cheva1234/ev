package com.ev.terminal.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

private const val MAX_PROCESS_OUTPUT_CHARS = 65536
private const val PROCESS_TIMEOUT_MS = 120000L

/**
 * Captures process output without assuming that the child writes newline-delimited text.
 *
 * llama-cli can stream generated tokens as one very long line. Reading with readLine()
 * would therefore allocate the whole response before a size limit can be applied.
 */
internal suspend fun collectProcessOutput(
    input: InputStream,
    maxChars: Int,
    onChunk: suspend (String) -> Unit = {}
): String {
    require(maxChars > 0) { "maxChars must be positive" }

    val output = StringBuilder(minOf(maxChars, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break

        if (output.length < maxChars && count > 0) {
            val remaining = maxChars - output.length
            val chunk = String(
                buffer,
                0,
                minOf(count, remaining),
                StandardCharsets.UTF_8
            )
            output.append(chunk)
            onChunk(chunk)
        }
        // Continue draining after the cap so llama-cli cannot block on a full pipe.
    }
    return output.toString()
}

class LlamaCppBackend(
    private val context: Context
) : EVModelBackend {

    @Volatile
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
        return generate(request) {}
    }

    override suspend fun generate(
        request: ModelRequest,
        onChunk: suspend (String) -> Unit
    ): ModelResponse {
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
                "-c", "2048",
                "-st",
                "-t", "4",
                "--no-warmup",
                "--reasoning-budget", "0"
            )
            Log.i("EV_MODEL", "spawning: ${cli.absolutePath} -m ${model.absolutePath} -st -n ${request.maxTokens} -c 2048 --reasoning-budget 0")
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] = nativeLibDir().absolutePath
            val proc = pb.start()
            process = proc
            val outputJob = async(Dispatchers.IO) {
                proc.inputStream.use { input ->
                    collectProcessOutput(input, MAX_PROCESS_OUTPUT_CHARS, onChunk)
                }
            }
            val exitJob = async(Dispatchers.IO) { proc.waitFor() }
            try {
                val exitCode = withTimeout(PROCESS_TIMEOUT_MS) { exitJob.await() }
                val output = outputJob.await()
                Log.i("EV_MODEL", "llama-cli exited $exitCode, output length=${output.length}")
                if (exitCode != 0) {
                    throw RuntimeException("llama-cli exited $exitCode: ${output.take(300)}")
                }
                val durationMs = System.currentTimeMillis() - start
                val text = parseOutput(output)
                Log.i("EV_MODEL", "parsed text length=${text.length}, tok/s=${if (durationMs > 0) text.length / 4.0 * 1000 / durationMs else 0.0}")
                ModelResponse(
                    text = text,
                    durationMs = durationMs,
                    tokPerSec = if (durationMs > 0) text.length / 4.0 * 1000 / durationMs else 0.0
                )
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw RuntimeException("llama-cli timed out (${PROCESS_TIMEOUT_MS / 1000}s)", e)
            } finally {
                withContext(NonCancellable) {
                    if (proc.isAlive) proc.destroyForcibly()
                    runCatching { outputJob.cancelAndJoin() }
                    runCatching { exitJob.cancelAndJoin() }
                    if (process === proc) process = null
                }
            }
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
