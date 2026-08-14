package com.ev.terminal.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class BundledModelTest {

    @Test
    fun `bundled model uses the qwen asset and app storage path`() {
        val filesDir = File("/data/user/0/com.ev.terminal/files")

        assertEquals("models/qwen3.5-0.8b.gguf", BUNDLED_MODEL_ASSET)
        assertEquals(
            File(filesDir, ".ev/models/qwen3.5-0.8b.gguf"),
            bundledModelFile(filesDir)
        )
    }
}
