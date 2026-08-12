package com.ev.terminal.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class GgufDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percent: Double
)

class GgufDownloader(private val context: Context) {

    private val _progress = MutableSharedFlow<GgufDownloadProgress>(extraBufferCapacity = 64)
    val progress: SharedFlow<GgufDownloadProgress> = _progress.asSharedFlow()

    val modelUrl = "https://registry.ollama.ai/v2/oamazonasgabriel/lfm2.5-2.6b/blobs/sha256:79fdf00351b46cf26f020aead28d01889886be87c55fa0eb907e6f9b00bfee14"
    val modelSizeBytes: Long = 1_674_454_848L

    fun modelFile(): File = File(context.filesDir, ".ev/models/lfm2.5-2.6b-q4_k_m.gguf")

    fun isDownloaded(): Boolean = modelFile().exists() && modelFile().length() == modelSizeBytes

    suspend fun download() {
        withContext(Dispatchers.IO) {
            val dir = modelFile().parentFile
            dir?.mkdirs()
            val tmp = File(dir, "lfm2.5-2.6b-q4_k_m.gguf.part")
            val conn = URL(modelUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "EV/0.2")
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                throw RuntimeException("GGUF download failed: http $code")
            }
            val total = conn.contentLengthLong
            val input = conn.inputStream
            val output = tmp.outputStream()
            val buffer = ByteArray(64 * 1024)
            var downloaded = 0L
            var lastEmit = 0L
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (downloaded - lastEmit > 1024 * 1024) {
                        lastEmit = downloaded
                        _progress.tryEmit(
                            GgufDownloadProgress(downloaded, total, downloaded * 100.0 / total)
                        )
                    }
                }
            } finally {
                input.close()
                output.close()
                conn.disconnect()
            }
            if (downloaded != total) {
                tmp.delete()
                throw RuntimeException("download incomplete: $downloaded / $total")
            }
            tmp.renameTo(modelFile())
            _progress.tryEmit(GgufDownloadProgress(total, total, 100.0))
        }
    }
}
