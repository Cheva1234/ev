package com.ev.terminal.model

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.IOException

internal const val BUNDLED_MODEL_NAME = "qwen3.5:0.8b"
internal const val BUNDLED_MODEL_ASSET = "models/qwen3.5-0.8b.gguf"
internal const val BUNDLED_MODEL_FILE_NAME = "qwen3.5-0.8b.gguf"

internal fun bundledModelFile(filesDir: File): File =
    File(filesDir, ".ev/models/$BUNDLED_MODEL_FILE_NAME")

/** Copies the bundled APK model to a normal filesystem path for llama.cpp. */
internal class BundledModelInstaller(private val context: Context) {

    fun ensureInstalled(): File {
        val target = bundledModelFile(context.filesDir)
        val expectedSize = bundledModelSize()

        removeLegacyModel()
        if (target.isFile && target.length() == expectedSize) return target

        val parent = target.parentFile
            ?: throw IOException("Cannot create model directory for ${target.absolutePath}")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create model directory ${parent.absolutePath}")
        }

        val temporary = File(parent, "${target.name}.part")
        try {
            context.assets.open(BUNDLED_MODEL_ASSET, AssetManager.ACCESS_STREAMING).use { input ->
                temporary.outputStream().use { output ->
                    input.copyTo(output, COPY_BUFFER_BYTES)
                }
            }
            if (temporary.length() != expectedSize) {
                throw IOException(
                    "Bundled model copy is incomplete: ${temporary.length()} of $expectedSize bytes"
                )
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Cannot replace old model at ${target.absolutePath}")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Cannot finalize model at ${target.absolutePath}")
            }
            return target
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun bundledModelSize(): Long {
        return try {
            context.assets.openFd(BUNDLED_MODEL_ASSET).use { it.length }
        } catch (error: Exception) {
            throw IOException(
                "Bundled model asset is missing or compressed incorrectly: $BUNDLED_MODEL_ASSET",
                error
            )
        }
    }

    private fun removeLegacyModel() {
        File(context.filesDir, ".ev/models/lfm2.5-2.6b-q4_k_m.gguf").delete()
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 1024 * 1024
    }
}
