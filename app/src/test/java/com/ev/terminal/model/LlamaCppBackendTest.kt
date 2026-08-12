package com.ev.terminal.model

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LlamaCppBackendTest {

    @Test
    fun `process output is bounded even when model emits no newlines`() = runBlocking {
        val output = ByteArrayInputStream("x".repeat(1_000_000).toByteArray())

        val captured = collectProcessOutput(output, maxChars = 64)

        assertEquals(64, captured.length)
    }

    @Test
    fun `process output reports chunks while it is being read`() = runBlocking {
        val chunks = mutableListOf<String>()

        collectProcessOutput(
            ByteArrayInputStream("hello world".toByteArray()),
            maxChars = 64,
            onChunk = { chunks += it }
        )

        assertEquals("hello world", chunks.joinToString(""))
    }
}
