package com.ev.terminal.harness

import com.ev.terminal.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeModel(private val responses: List<String>) : AgentModel {
    private val queue = responses.toMutableList()
    val prompts = mutableListOf<String>()

    override suspend fun generate(
        system: String,
        prompt: String,
        maxTokens: Int,
        onChunk: suspend (String) -> Unit
    ): String {
        prompts.add(prompt)
        return queue.removeAt(0).also { onChunk(it) }
    }
}

class AgentRunnerTest {

    private val registry = ToolRegistry()

    @Test
    fun `direct math answer is executed without a model pass`() = runBlocking {
        val runner = AgentRunner(FakeModel(emptyList()), registry)

        val result = runner.tryDirectMath("84*9.81")

        assertEquals("824.040", result?.summary)
    }

    @Test
    fun `direct math rejects words so questions never route to math`() = runBlocking {
        val runner = AgentRunner(FakeModel(emptyList()), registry)

        assertNull(runner.tryDirectMath("what's the weather tomorrow?"))
        assertNull(runner.tryDirectMath("what is 2+2"))
        assertNull(runner.tryDirectMath("2+2?"))
    }

    @Test
    fun `tool call is executed and final answer returned`() = runBlocking {
        val model = FakeModel(
            listOf(
                "TOOL: @math 2+2",
                "The result is 4."
            )
        )
        val runner = AgentRunner(model, registry)

        val turn = runner.run("compute 2+2", "tools\nMATH: pure arithmetic", {})

        assertEquals("The result is 4.", turn.text)
        assertEquals(1, turn.toolCalls.size)
        assertEquals("MATH", turn.toolCalls[0].family)
        assertEquals("4", turn.toolCalls[0].result.summary)
        assertTrue(model.prompts[1].contains("TOOL RESULT: MATH SUCCESS 4"))
        assertTrue(model.prompts[1].contains("value=4"))
    }

    @Test
    fun `live data answer is rejected until its tool runs`() = runBlocking {
        val model = FakeModel(
            listOf(
                "It is probably noon.",
                "TOOL: @time now",
                "The current time is shown in the tool result."
            )
        )
        val runner = AgentRunner(model, registry)

        val turn = runner.run(
            "what time is it?",
            AgentRunner.systemPrompt(registry.describeTools()),
            {}
        )

        assertEquals(1, turn.toolCalls.size)
        assertEquals("TIME", turn.toolCalls.single().family)
        assertTrue(model.prompts[1].contains("requires the TIME tool"))
    }

    @Test
    fun `calculus answer is rejected until the math tool runs`() = runBlocking {
        val model = FakeModel(
            listOf(
                "The derivative is 2x.",
                "TOOL: @math diff(x^2,x)"
            )
        )
        val runner = AgentRunner(model, registry)

        val turn = runner.run(
            "Find the derivative of x^2",
            AgentRunner.systemPrompt(registry.describeTools()),
            {}
        )

        assertEquals("$$\\frac{d}{dx}\\left(x^{2}\\right)=2x$$", turn.text)
        assertEquals("MATH", turn.toolCalls.single().family)
        assertTrue(turn.toolCalls.single().result.summary.contains("$$"))
        assertTrue(model.prompts[1].contains("requires the MATH tool"))
    }

    @Test
    fun `successful calculus tool result is returned without model rewriting`() = runBlocking {
        val model = FakeModel(listOf("TOOL: @math diff(x^2,x)"))
        val runner = AgentRunner(model, registry)

        val turn = runner.run(
            "Find the derivative of x^2",
            AgentRunner.systemPrompt(registry.describeTools()),
            {}
        )

        assertEquals("$$\\frac{d}{dx}\\left(x^{2}\\right)=2x$$", turn.text)
        assertEquals(1, turn.toolCalls.size)
        assertEquals(1, model.prompts.size)
    }

    @Test
    fun `tool protocol and rejected answers are not streamed to the chat`() = runBlocking {
        val model = FakeModel(
            listOf(
                "TOOL: @math 2+2",
                "The result is 4."
            )
        )
        val streamed = mutableListOf<String>()
        val runner = AgentRunner(model, registry)

        runner.run(
            "compute 2+2",
            AgentRunner.systemPrompt(registry.describeTools()),
            onChunk = { streamed += it }
        )

        assertEquals("The result is 4.", streamed.joinToString(""))
    }

    @Test
    fun `disabled tools are neither required nor executed`() = runBlocking {
        val model = FakeModel(listOf("TOOL: @time now", "Time access is disabled."))
        val runner = AgentRunner(model, registry)

        val turn = runner.run(
            "what time is it?",
            AgentRunner.systemPrompt("MATH: pure arithmetic"),
            allowedTools = setOf("MATH")
        )

        assertTrue(turn.toolCalls.isEmpty())
        assertEquals("Time access is disabled.", turn.text)
        assertTrue(model.prompts[1].contains("TIME ERROR tool_disabled"))
    }

    @Test
    fun `tool requirement policy only gates requests that need runtime evidence`() {
        assertEquals("WEATHER", ToolRequirementPolicy.requiredFamily("Will it rain tomorrow?"))
        assertEquals("MAIL", ToolRequirementPolicy.requiredFamily("Check my latest email"))
        assertEquals("MATH", ToolRequirementPolicy.requiredFamily("What is 12 * 7?"))
        assertEquals("MATH", ToolRequirementPolicy.requiredFamily("Find the derivative of x^2"))
        assertEquals("MATH", ToolRequirementPolicy.requiredFamily("Evaluate the integral of sin(x)"))
        assertEquals("WEB", ToolRequirementPolicy.requiredFamily("Search the web for EV news"))
        assertNull(ToolRequirementPolicy.requiredFamily("Explain time complexity"))
    }

    @Test
    fun `answer without tool calls is returned as-is`() = runBlocking {
        val model = FakeModel(listOf("Hello there."))
        val runner = AgentRunner(model, registry)

        val turn = runner.run("hi", "tools", {})

        assertEquals("Hello there.", turn.text)
        assertTrue(turn.toolCalls.isEmpty())
    }

    @Test
    fun `unknown tool family does not crash the loop`() = runBlocking {
        val model = FakeModel(listOf("TOOL: @bogus foo", "done"))
        val runner = AgentRunner(model, registry)

        val turn = runner.run("do it", "tools", {})

        assertEquals("done", turn.text)
        assertTrue(turn.toolCalls.isEmpty())
    }

    @Test
    fun `bare at-command lines are accepted without the TOOL prefix`() = runBlocking {
        val model = FakeModel(listOf("@math 2+2", "The result is 4."))
        val runner = AgentRunner(model, registry)

        val turn = runner.run("compute 2+2", AgentRunner.systemPrompt("MATH: pure arithmetic"), {})

        assertEquals("The result is 4.", turn.text)
        assertEquals(1, turn.toolCalls.size)
        assertEquals("MATH", turn.toolCalls[0].family)
    }

    @Test
    fun `tool prefix is accepted regardless of capitalization`() = runBlocking {
        val model = FakeModel(listOf("tool: @math 2+2", "The result is 4."))
        val runner = AgentRunner(model, registry)

        val turn = runner.run(
            "compute 2+2",
            AgentRunner.systemPrompt("MATH: pure arithmetic"),
            {}
        )

        assertEquals("The result is 4.", turn.text)
        assertEquals("MATH", turn.toolCalls.single().family)
    }

    @Test
    fun `answers containing at-words are not treated as commands`() = runBlocking {
        val model = FakeModel(listOf("Email me at foo@bar.com about it."))
        val runner = AgentRunner(model, registry)

        val turn = runner.run("hi", "tools", {})

        assertEquals("Email me at foo@bar.com about it.", turn.text)
        assertTrue(turn.toolCalls.isEmpty())
    }

    @Test
    fun `system prompt advertises tools and the TOOL protocol`() {
        val prompt = AgentRunner.systemPrompt("MATH: pure arithmetic")

        assertTrue(prompt.contains("MATH: pure arithmetic"))
        assertTrue(prompt.contains("TOOL:"))
    }

    @Test
    fun `system prompt avoids transcript examples that the small model may imitate`() {
        val prompt = AgentRunner.systemPrompt("MATH: pure arithmetic")

        assertTrue(prompt.contains("Never output analysis"))
        assertTrue(!prompt.contains("Example: User:"))
    }

    @Test
    fun `model receives the request without synthetic transcript labels`() = runBlocking {
        val model = FakeModel(listOf("Hello there."))
        val runner = AgentRunner(model, registry)

        runner.run("hi", AgentRunner.systemPrompt("MATH: pure arithmetic"), {})

        assertEquals("hi", model.prompts.single())
    }

    @Test
    fun `full system prompt echo is blanked so the loop retries`() {
        val system = AgentRunner.systemPrompt("MATH: pure arithmetic")
        val guard = PromptLeakGuard(system)

        assertEquals("", guard.clean(system))
        assertEquals("", guard.clean("some prefix. $system"))
    }

    @Test
    fun `prompt echoes are blanked but real answers pass through`() {
        val system = AgentRunner.systemPrompt("MATH: pure arithmetic")
        val guard = PromptLeakGuard(system)

        assertEquals("", guard.clean(system))
        assertEquals("", guard.clean("$system\n4"))
        assertEquals("4", guard.clean("4"))
    }

    @Test
    fun `blanked responses produce a retry and a graceful fallback`() = runBlocking {
        val system = AgentRunner.systemPrompt("MATH: pure arithmetic")
        val model = FakeModel(listOf(system, system, system, system))
        val runner = AgentRunner(model, registry)

        val turn = runner.run("2+2", system, {})

        assertTrue(turn.text.isNotBlank())
        assertTrue(turn.toolCalls.isEmpty())
    }
}
