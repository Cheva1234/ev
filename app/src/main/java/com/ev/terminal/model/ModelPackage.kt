package com.ev.terminal.model

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

internal const val MODEL_PACKAGE_NAME = "qwen3.5:0.8b"
internal const val MODEL_PACKAGE_FILE_NAME = "qwen3.5-0.8b-q4_0.gguf"
internal const val MODEL_PACKAGE_URL =
    "https://github.com/Cheva1234/ev/releases/download/v0.1.5/qwen3.5-0.8b-q4_0.gguf"
internal const val MODEL_PACKAGE_SIZE_BYTES = 563_036_064L
internal const val MODEL_PACKAGE_SHA256 =
    "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf"

data class ModelDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long = MODEL_PACKAGE_SIZE_BYTES
) {
    val percent: Int
        get() = if (totalBytes <= 0) 0 else {
            (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
        }
}

internal fun modelPackageFile(filesDir: File): File =
    File(filesDir, ".ev/models/$MODEL_PACKAGE_FILE_NAME")

internal fun rangeHeader(existingBytes: Long): String? =
    existingBytes.takeIf { it > 0 }?.let { "bytes=$it-" }

/** Downloads, verifies, and atomically installs the model outside the APK. */
internal class ModelPackageInstaller(private val context: Context) {

    fun isInstalled(): Boolean {
        removeLegacyModel()
        val target = modelPackageFile(context.filesDir)
        return target.isFile && target.length() == MODEL_PACKAGE_SIZE_BYTES
    }

    fun partialBytes(): Long {
        val partial = partialModelFile()
        return if (partial.isFile) partial.length().coerceAtMost(MODEL_PACKAGE_SIZE_BYTES) else 0L
    }

    suspend fun ensureInstalled(): File = withContext(Dispatchers.IO) {
        removeLegacyModel()
        val target = modelPackageFile(context.filesDir)
        if (!target.isFile || target.length() != MODEL_PACKAGE_SIZE_BYTES) {
            throw IOException("Model is not downloaded: ${target.absolutePath}")
        }

        val marker = checksumMarker(target)
        if (marker.readTextOrNull() != MODEL_PACKAGE_SHA256) {
            val actual = sha256(target)
            if (actual != MODEL_PACKAGE_SHA256) {
                throw IOException("Model checksum mismatch: expected $MODEL_PACKAGE_SHA256, got $actual")
            }
            marker.writeText(MODEL_PACKAGE_SHA256)
        }
        target
    }

    suspend fun download(
        onProgress: suspend (ModelDownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        removeLegacyModel()
        val target = modelPackageFile(context.filesDir)
        val parent = target.parentFile
            ?: throw IOException("Cannot create model directory for ${target.absolutePath}")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create model directory ${parent.absolutePath}")
        }

        if (target.isFile && target.length() == MODEL_PACKAGE_SIZE_BYTES) {
            return@withContext try {
                ensureInstalled()
            } catch (error: Exception) {
                target.delete()
                checksumMarker(target).delete()
                downloadToTemporary(target, onProgress)
            }
        }
        downloadToTemporary(target, onProgress)
    }

    private suspend fun downloadToTemporary(
        target: File,
        onProgress: suspend (ModelDownloadProgress) -> Unit
    ): File {
        val partial = partialModelFile()
        var existingBytes = partialBytes()
        if (partial.isFile && partial.length() > MODEL_PACKAGE_SIZE_BYTES) {
            partial.delete()
            existingBytes = 0L
        }

        if (existingBytes == MODEL_PACKAGE_SIZE_BYTES) {
            if (sha256(partial) == MODEL_PACKAGE_SHA256) {
                if (target.exists() && !target.delete()) {
                    throw IOException("Cannot replace old model at ${target.absolutePath}")
                }
                if (!partial.renameTo(target)) {
                    throw IOException("Cannot finalize model at ${target.absolutePath}")
                }
                checksumMarker(target).writeText(MODEL_PACKAGE_SHA256)
                onProgress(ModelDownloadProgress(MODEL_PACKAGE_SIZE_BYTES))
                return target
            }
            partial.delete()
            existingBytes = 0L
        }

        val availableBytes = StatFs(partial.parentFile!!.path).availableBytes
        val bytesNeeded = (MODEL_PACKAGE_SIZE_BYTES - existingBytes).coerceAtLeast(0L)
        if (availableBytes > 0 && availableBytes < bytesNeeded + MIN_FREE_BYTES) {
            throw IOException(
                "Not enough free storage: need at least " +
                    formatBytes(bytesNeeded + MIN_FREE_BYTES) +
                    ", available " + formatBytes(availableBytes)
            )
        }

        val connection = (URL(MODEL_PACKAGE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            rangeHeader(existingBytes)?.let { setRequestProperty("Range", it) }
        }
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            // Closing the connection unblocks a read when the user presses CANCEL.
            connection.disconnect()
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                throw IOException("Model download failed: HTTP $responseCode")
            }

            val append = existingBytes > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!append) existingBytes = 0L
            onProgress(ModelDownloadProgress(existingBytes))

            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var downloaded = existingBytes
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(ModelDownloadProgress(downloaded))
                    }
                }
            }

            if (partial.length() != MODEL_PACKAGE_SIZE_BYTES) {
                throw IOException(
                    "Model download is incomplete: ${partial.length()} of $MODEL_PACKAGE_SIZE_BYTES bytes"
                )
            }

            val actual = sha256(partial)
            if (actual != MODEL_PACKAGE_SHA256) {
                partial.delete()
                throw IOException("Model checksum mismatch: expected $MODEL_PACKAGE_SHA256, got $actual")
            }

            if (target.exists() && !target.delete()) {
                throw IOException("Cannot replace old model at ${target.absolutePath}")
            }
            if (!partial.renameTo(target)) {
                throw IOException("Cannot finalize model at ${target.absolutePath}")
            }
            checksumMarker(target).writeText(MODEL_PACKAGE_SHA256)
            onProgress(ModelDownloadProgress(MODEL_PACKAGE_SIZE_BYTES))
            return target
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun partialModelFile(): File {
        val target = modelPackageFile(context.filesDir)
        return File(target.parentFile, "${target.name}.part")
    }

    private fun checksumMarker(target: File): File =
        File(target.parentFile, "${target.name}.sha256")

    private fun removeLegacyModel() {
        File(context.filesDir, ".ev/models/lfm2.5-2.6b-q4_k_m.gguf").delete()
        File(context.filesDir, ".ev/models/qwen3.5-0.8b.gguf").delete()
        File(context.filesDir, ".ev/models/qwen3.5-0.8b.gguf.sha256").delete()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val COPY_BUFFER_BYTES = 1024 * 1024
        const val MIN_FREE_BYTES = 128L * 1024L * 1024L
    }
}

private fun File.readTextOrNull(): String? =
    if (isFile) runCatching { readText().trim() }.getOrNull() else null

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(Locale.US, bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.0f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
    else -> "$bytes B"
}
