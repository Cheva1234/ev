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

    @Test
    fun `cli output removes progress frames and diagnostics before command parsing`() {
        val mixed = "\u001B[2KLoading model...\r|/\r" +
            "llama_model_loader: metadata\n" +
            "@math 84*9.81\n"

        assertEquals("@math 84*9.81", parseCliOutput(mixed))
    }

    @Test
    fun `cli command extraction ignores unrelated loading text`() {
        val mixed = "Loading model...\n\n@math 2+2\n"

        assertEquals("@math 2+2", parseCliOutput(mixed))
    }

    @Test
    fun `cli parser removes echoed prompt before response marker`() {
        val echoed = "system instructions\nUser: helloEV:\nHello! How can I help?"

        assertEquals("Hello! How can I help?", parseCliOutput(echoed, "EV:"))
    }

    @Test
    fun `stream filter waits for marker and streams only the answer`() {
        val filter = ResponseStreamFilter("EV:")

        assertEquals("", filter.accept("system\nUser: helloE"))
        assertEquals("Hello", filter.accept("V:\nHello"))
        assertEquals(" there", filter.accept(" there"))
    }
}
