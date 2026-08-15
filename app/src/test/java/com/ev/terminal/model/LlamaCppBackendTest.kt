package com.ev.terminal.model

import java.io.File
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppBackendTest {

    @Test
    fun `llama cli uses conversation mode and keeps system prompt separate`() {
        val system = "You are EV."
        val prompt = "User: hello\nEV:"

        val command = buildLlamaCommand(
            cli = File("/bin/llama-cli"),
            model = File("/models/ev.gguf"),
            request = ModelRequest(
                system = system,
                prompt = prompt,
                maxTokens = 64,
                temperature = 0.1f,
                topP = 0.9f
            )
        )

        assertTrue(command.contains("-cnv"))
        assertTrue(command.contains("-st"))
        assertFalse(command.contains("--no-conversation"))
        assertEquals(system, command[command.indexOf("-sys") + 1])
        assertEquals(prompt, command[command.indexOf("-p") + 1])
    }

    @Test
    fun `llama cli disables reasoning for concise app responses`() {
        val command = buildLlamaCommand(
            cli = File("/bin/llama-cli"),
            model = File("/models/ev.gguf"),
            request = ModelRequest(system = "EV", prompt = "hello", maxTokens = 64)
        )

        assertEquals("off", command[command.indexOf("--reasoning") + 1])
        assertEquals("none", command[command.indexOf("--reasoning-format") + 1])
    }

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
    fun `cli parser removes qwen thinking tags and end marker`() {
        val output = "<think>internal reasoning</think>\nHello! [end of text]\n"

        assertEquals("Hello!", parseCliOutput(output))
    }

    @Test
    fun `cli parser does not expose legacy interactive thinking transcript`() {
        val output = """
            build      : b10369
            model      : /data/user/0/com.ev.terminal/files/.ev/models/qwen.gguf
            type       : Q4_0
            modalities : text
            using custom system prompt
            available commands:
            /exit or Ctrl+C stop or exit
            User: Hello
            EV:
            [Start thinking] Thinking Process:
            Constraint Check: answer directly
        """.trimIndent()

        assertEquals("", parseCliOutput(output))
    }

    @Test
    fun `stream filter waits for marker and streams only the answer`() {
        val filter = ResponseStreamFilter("EV:")

        assertEquals("", filter.accept("system\nUser: helloE"))
        assertEquals("Hello", filter.accept("V:\nHello"))
        assertEquals(" there", filter.accept(" there"))
    }
}
