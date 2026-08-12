package com.ev.terminal.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OllamaBackend(
    private val baseUrl: String,
    private val modelName: String
) : EVModelBackend {

    private var loaded = false
    private var lastLoadMs = 0L

    override suspend fun load() {
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val url = URL("$baseUrl/api/generate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 120000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("model", modelName)
                put("prompt", "ping")
                put("stream", false)
                put("keep_alive", "5m")
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.inputStream?.close()
            conn.disconnect()
            if (code != 200) throw RuntimeException("ollama load failed: http $code")
            lastLoadMs = System.currentTimeMillis() - start
            loaded = true
        }
    }

    override suspend fun generate(request: ModelRequest): ModelResponse {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val url = URL("$baseUrl/api/generate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 120000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("model", modelName)
                put("prompt", request.prompt)
                put("system", request.system)
                put("stream", false)
                put("options", JSONObject().apply {
                    put("temperature", request.temperature)
                    put("top_p", request.topP)
                    put("num_predict", request.maxTokens)
                })
                put("keep_alive", "5m")
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                throw RuntimeException("ollama generate failed: http $code $err")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(text)
            val durationMs = System.currentTimeMillis() - start
            val output = json.optString("response", "")
            val promptTokens = json.optInt("prompt_eval_count", 0)
            val outputTokens = json.optInt("eval_count", 0)
            val tokPerSec = if (durationMs > 0) outputTokens * 1000.0 / durationMs else 0.0
            ModelResponse(
                text = output,
                promptTokens = promptTokens,
                outputTokens = outputTokens,
                durationMs = durationMs,
                tokPerSec = tokPerSec
            )
        }
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/api/generate")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().apply {
                    put("model", modelName)
                    put("prompt", "ping")
                    put("stream", false)
                    put("keep_alive", 0)
                }.toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.inputStream?.close()
                conn.disconnect()
            } catch (e: Exception) {
                // unload is best-effort
            }
            loaded = false
        }
    }

    override suspend fun status(): ModelStatus = ModelStatus(loaded, modelName)

    companion object {
        fun encodeModel(name: String): String = URLEncoder.encode(name, "UTF-8")
    }
}
