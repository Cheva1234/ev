package com.ev.terminal.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPackageTest {

    @Test
    fun `model package metadata points to the qwen release asset`() {
        assertEquals("qwen3.5:0.8b", MODEL_PACKAGE_NAME)
        assertEquals("qwen3.5-0.8b-q4_0.gguf", MODEL_PACKAGE_FILE_NAME)
        assertTrue(MODEL_PACKAGE_URL.endsWith("/v0.1.4/qwen3.5-0.8b-q4_0.gguf"))
        assertEquals(563_036_064L, MODEL_PACKAGE_SIZE_BYTES)
        assertEquals(
            "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
            MODEL_PACKAGE_SHA256
        )
    }

    @Test
    fun `model package uses private storage and resumes partial files`() {
        val filesDir = File("/data/user/0/com.ev.terminal/files")

        assertEquals(
            File(filesDir, ".ev/models/qwen3.5-0.8b-q4_0.gguf"),
            modelPackageFile(filesDir)
        )
        assertEquals("bytes=1024-", rangeHeader(1024))
        assertNull(rangeHeader(0))
    }

    @Test
    fun `download progress reports bounded percentage`() {
        assertEquals(50, ModelDownloadProgress(MODEL_PACKAGE_SIZE_BYTES / 2).percent)
        assertEquals(100, ModelDownloadProgress(MODEL_PACKAGE_SIZE_BYTES * 2).percent)
        assertEquals(0, ModelDownloadProgress(1, 0).percent)
    }

    @Test
    fun `sha256 returns the expected digest`() {
        val file = File.createTempFile("ev-model-test", ".bin")
        try {
            file.writeText("EV model package")
            assertEquals(
                "744dd3060f4be2c7fca8b7a8ceb98acd8eb7b3290a2d7166984d4681afa95212",
                sha256(file)
            )
        } finally {
            file.delete()
        }
    }
}
