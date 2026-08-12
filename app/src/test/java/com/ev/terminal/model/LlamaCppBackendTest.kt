package com.ev.terminal.model

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class LlamaCppBackendTest {

    @Test
    fun `process output is bounded even when model emits no newlines`() {
        val output = ByteArrayInputStream("x".repeat(1_000_000).toByteArray())

        val captured = collectProcessOutput(output, maxChars = 64)

        assertEquals(64, captured.length)
    }
}
