package com.ev.terminal.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val status: String,
    val completedBytes: Long,
    val totalBytes: Long,
    val percent: Double
) {
    val isDone: Boolean get() = status == "success"
}

class ModelDownloader(private val baseUrl: String) {

    private val _progress = MutableSharedFlow<DownloadProgress>(extraBufferCapacity = 64)
    val progress: SharedFlow<DownloadProgress> = _progress.asSharedFlow()

    suspend fun pull(modelName: String) {
        withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/pull")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 0
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("model", modelName)
                put("stream", true)
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                throw RuntimeException("ollama pull failed: http $code $err")
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val json = JSONObject(line)
                        val status = json.optString("status", "")
                        val completed = json.optLong("completed", 0)
                        val total = json.optLong("total", 0)
                        val percent = if (total > 0) completed * 100.0 / total else 0.0
                        _progress.tryEmit(DownloadProgress(status, completed, total, percent))
                    } catch (e: Exception) {
                        // skip malformed line
                    }
                }
            }
            conn.disconnect()
        }
    }
}
