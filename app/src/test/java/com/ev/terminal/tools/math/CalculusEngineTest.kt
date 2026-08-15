package com.ev.terminal.tools.math

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculusEngineTest {

    @Test
    fun `numeric evaluator accepts variable bindings`() {
        assertEquals(9.0, ExpressionEvaluator.eval("x^2", mapOf("x" to 3.0)), 1e-9)
    }

    @Test
    fun `calculus engine can infer the variable`() {
        assertEquals("x", CalculusEngine.inferVariable("sin(x) + x^2"))
    }

    @Test
    fun `derivative returns a latex expression`() {
        val result = CalculusEngine.derivative("x^2 + sin(x)", "x")

        assertEquals("2x+\\cos\\left(x\\right)", result.latex)
    }

    @Test
    fun `antiderivative handles a polynomial`() {
        val result = CalculusEngine.antiderivative("x^2", "x")

        assertEquals("\\frac{x^{3}}{3}", result.latex)
    }

    @Test
    fun `definite integral evaluates numerically`() {
        val result = CalculusEngine.definiteIntegral("x^2", "x", 0.0, 3.0)

        assertEquals(9.0, result, 1e-5)
    }

    @Test
    fun `definite integral accepts implicit multiplication`() {
        val result = CalculusEngine.definiteIntegral("3x^2", "x", 0.0, 1.0)

        assertEquals(1.0, result, 1e-5)
    }

    @Test
    fun `limit evaluates a removable discontinuity`() {
        val result = CalculusEngine.limit("(x^2-1)/(x-1)", "x", 1.0)

        assertTrue(abs(result - 2.0) < 1e-4)
    }

    @Test
    fun `limit continues sampling for a more accurate result`() {
        val result = CalculusEngine.limit("sin(x)/x", "x", 0.0)

        assertEquals(1.0, result, 1e-6)
    }
}
