package com.ev.terminal.tools.math

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.ToolStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathToolTest {

    @Test
    fun `calculus result includes latex for the chat renderer`() = runBlocking {
        val result = MathTool().execute(EvclCommand.Math("diff(x^2,x)"))

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertTrue(result.summary.contains("$$"))
        assertTrue(result.summary.contains("\\frac{d}{dx}"))
        assertTrue(result.detail.contains("latex="))
    }

    @Test
    fun `unsupported symbolic antiderivative returns an honest error`() = runBlocking {
        val result = MathTool().execute(EvclCommand.Math("integrate(sin(x^2),x)"))

        assertEquals(ToolStatus.ERROR, result.status)
        assertTrue(result.summary.contains("unsupported", ignoreCase = true))
    }

    @Test
    fun `definite integral returns a latex block and numeric value`() = runBlocking {
        val result = MathTool().execute(EvclCommand.Math("integral(3x^2,0,1,x)"))

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertTrue(result.summary.contains("$$"))
        assertTrue(result.summary.contains("\\int_{0}^{1}"))
        assertTrue(result.detail.contains("value=1"))
    }

    @Test
    fun `one argument and natural language derivative calls use inferred x`() = runBlocking {
        val oneArgument = MathTool().execute(EvclCommand.Math("derivative(x^2)"))
        val naturalLanguage = MathTool().execute(EvclCommand.Math("find the derivative of x^2"))

        assertEquals(ToolStatus.SUCCESS, oneArgument.status)
        assertEquals(ToolStatus.SUCCESS, naturalLanguage.status)
        assertTrue(oneArgument.summary.contains("=2x"))
        assertTrue(naturalLanguage.summary.contains("=2x"))
    }
}
