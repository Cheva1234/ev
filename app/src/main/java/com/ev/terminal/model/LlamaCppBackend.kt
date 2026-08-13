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
import java.util.Locale

private const val MAX_PROCESS_OUTPUT_CHARS = 65536
private const val MAX_DIAGNOSTIC_OUTPUT_CHARS = 32768
private const val PROCESS_TIMEOUT_MS = 120000L
private val ANSI_ESCAPE = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
private val CLI_DIAGNOSTIC_PREFIXES = listOf(
    "llama_", "ggml_", "load_", "loading", "progress", "main:", "system_",
    "sampling", "prompt eval", "eval time", "llama_perf", "build:", "print_info",
    "offloaded", "init_"
)

private fun isCliDiagnosticLine(line: String): Boolean {
    val normalized = line.trim().lowercase(Locale.US)
    return normalized.matches(Regex("^[|/\\\\—-]+$")) ||
        CLI_DIAGNOSTIC_PREFIXES.any { normalized.startsWith(it) }
}

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

/** Removes terminal control sequences and carriage-return progress frames. */
internal fun sanitizeCliOutput(output: String): String {
    return ANSI_ESCAPE.replace(output, "")
        .replace('\r', '\n')
        .lineSequence()
        .filterNot(::isCliDiagnosticLine)
        .joinToString("\n")
}

private fun responseMarker(prompt: String): String? {
    return when {
        prompt.trimEnd().endsWith("EVCL:") -> "EVCL:"
        prompt.trimEnd().endsWith("EV:") -> "EV:"
        else -> null
    }
}

/**
 * Holds model output until the prompt's response marker has been seen.
 *
 * Some llama.cpp builds ignore the prompt-display flag and echo the complete
 * prompt before generating. That echo must never reach the chat bubble.
 */
internal class ResponseStreamFilter(private val marker: String?) {
    private var responseStarted = marker == null
    private val pending = StringBuilder()

    fun accept(chunk: String): String {
        val cleanChunk = sanitizeCliOutput(chunk)
        if (cleanChunk.isEmpty()) return ""
        if (responseStarted) return cleanChunk

        pending.append(cleanChunk)
        val buffered = pending.toString()
        val markerIndex = buffered.lastIndexOf(marker!!)
        if (markerIndex < 0) {
            // Keep the whole marker in the buffer so a marker split over two
            // chunks is always recognized, while bounding memory use.
            val keep = marker.length
            if (pending.length > 8192) {
                val tail = pending.substring(pending.length - maxOf(keep, 0))
                pending.clear()
                pending.append(tail)
            }
            return ""
        }

        responseStarted = true
        val response = buffered.substring(markerIndex + marker.length)
        pending.clear()
        return response.trimStart()
    }
}

internal fun parseCliOutput(output: String, marker: String? = null): String {
    var text = sanitizeCliOutput(output)
    if (marker != null) {
        val markerIndex = text.lastIndexOf(marker)
        if (markerIndex >= 0) {
            text = text.substring(markerIndex + marker.length)
        }
    }
    if (text.contains("[End thinking]")) {
        text = text.substringAfterLast("[End thinking]")
    }
    text = text.substringBefore("[ Prompt:")
    text = text.substringBefore("Exiting...")
    text = text.replace(Regex("\\[Start thinking\\].*?\\[End thinking\\]", RegexOption.DOT_MATCHES_ALL), "")

    return text.lineSequence()
        .filterNot(::isCliDiagnosticLine)
        .joinToString("\n")
        .trim()
}

/**
 * Builds a single-turn llama-cli invocation using the model's chat template.
 *
 * llama-cli owns the system/user role formatting when conversation mode is
 * enabled. Passing both prompts as one `-p` value makes an instruct model see
 * the system instructions as ordinary user text, which can lead to prompt
 * echoes or tool-only output.
 */
internal fun buildLlamaCommand(
    cli: File,
    model: File,
    request: ModelRequest
): List<String> = buildList {
    add(cli.absolutePath)
    addAll(listOf("-m", model.absolutePath))
    if (request.system.isNotBlank()) {
        addAll(listOf("-sys", request.system))
    }
    addAll(
        listOf(
            "-p", request.prompt,
            "-n", request.maxTokens.toString(),
            "--temp", request.temperature.toString(),
            "--top-p", request.topP.toString(),
            "-c", "2048",
            "-cnv",
            "-st",
            "-t", "4",
            "--no-warmup",
            "--no-display-prompt",
            "--reasoning-budget", "0"
        )
    )
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
            val cmd = buildLlamaCommand(cli, model, request)
            Log.i("EV_MODEL", "spawning llama-cli in single-turn conversation mode")
            val pb = ProcessBuilder(cmd)
            // stdout is model output; stderr is llama.cpp diagnostics and must not be parsed as text.
            pb.redirectErrorStream(false)
            pb.environment()["LD_LIBRARY_PATH"] = nativeLibDir().absolutePath
            val proc = pb.start()
            process = proc
            // Conversation mode applies the model's chat template and does not
            // echo the raw `User: ... EV:` prompt. Stream generated text directly.
            val streamFilter = ResponseStreamFilter(null)
            val outputJob = async(Dispatchers.IO) {
                proc.inputStream.use { input ->
                    collectProcessOutput(input, MAX_PROCESS_OUTPUT_CHARS) { chunk ->
                        val cleanChunk = streamFilter.accept(chunk)
                        if (cleanChunk.isNotEmpty()) onChunk(cleanChunk)
                    }
                }
            }
            val diagnosticJob = async(Dispatchers.IO) {
                proc.errorStream.use { input ->
                    collectProcessOutput(input, MAX_DIAGNOSTIC_OUTPUT_CHARS)
                }
            }
            val exitJob = async(Dispatchers.IO) { proc.waitFor() }
            try {
                val exitCode = withTimeout(PROCESS_TIMEOUT_MS) { exitJob.await() }
                val output = outputJob.await()
                val diagnostics = diagnosticJob.await()
                Log.i("EV_MODEL", "llama-cli exited $exitCode, output length=${output.length}")
                if (diagnostics.isNotBlank()) {
                    Log.i("EV_MODEL", "llama-cli diagnostics: ${diagnostics.take(1000)}")
                }
                if (exitCode != 0) {
                    throw RuntimeException("llama-cli exited $exitCode: ${diagnostics.take(300)}")
                }
                val durationMs = System.currentTimeMillis() - start
                val text = parseCliOutput(output)
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
                    runCatching { diagnosticJob.cancelAndJoin() }
                    runCatching { exitJob.cancelAndJoin() }
                    if (process === proc) process = null
                }
            }
        }
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
