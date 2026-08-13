package com.ev.terminal.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OllamaBackendTest {

    @Test
    fun `qwen chat payload selects the model and disables thinking`() {
        val payload = buildOllamaChatPayload(
            modelName = DEFAULT_OLLAMA_MODEL,
            request = ModelRequest(
                system = "You are EV.",
                prompt = "User: hello",
                maxTokens = 64
            )
        )

        assertEquals("qwen3.5:0.8b", payload.model)
        assertFalse(payload.think)

        assertEquals("system", payload.messages[0].role)
        assertEquals("user", payload.messages[1].role)
    }
}
