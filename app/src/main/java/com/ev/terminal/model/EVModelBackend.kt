package com.ev.terminal.model

data class ModelRequest(
    val system: String,
    val prompt: String,
    val maxTokens: Int = 128,
    val temperature: Float = 0.1f,
    val topP: Float = 0.9f
)

data class ModelResponse(
    val text: String,
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
    val durationMs: Long = 0,
    val tokPerSec: Double = 0.0
)

data class ModelStatus(
    val loaded: Boolean,
    val name: String,
    val ramMb: Long = 0
)

interface EVModelBackend {
    suspend fun load()
    suspend fun generate(request: ModelRequest): ModelResponse

    /**
     * Generates a response and reports output chunks as the backend receives them.
     * Backends that cannot stream remain compatible by using the completed response.
     */
    suspend fun generate(
        request: ModelRequest,
        onChunk: suspend (String) -> Unit
    ): ModelResponse {
        val response = generate(request)
        if (response.text.isNotEmpty()) onChunk(response.text)
        return response
    }

    suspend fun unload()
    suspend fun status(): ModelStatus
}
