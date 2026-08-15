package com.ev.terminal.tools.math

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.Tool
import com.ev.terminal.tools.ToolResult
import com.ev.terminal.tools.ToolStatus
import java.util.Locale

class MathTool : Tool {
    override val family = "MATH"
    override val operations = listOf("numeric", "derivative", "integral", "limit")
    override val usage = "MATH: arithmetic and elementary calculus. Use for exact calculation, not general knowledge. " +
        "Calculus forms require an expression and variable; one-variable forms may omit the variable. " +
        "Examples: @math 84*9.81, @math diff(x^2+sin(x),x), @math integrate(x^2,x), " +
        "@math integral(x^2,0,3,x), @math limit(sin(x)/x,x,0)"

    override suspend fun execute(command: EvclCommand): ToolResult {
        val expr = (command as? EvclCommand.Math)?.expression ?: return error("no expression", "no_expression")
        if (expr.isBlank()) {
            return ToolResult(family, ToolStatus.ERROR, "empty expression", "MATH_RESULT\nstatus=ERROR\nreason=empty_expression")
        }
        if (isForbidden(expr)) {
            return ToolResult(family, ToolStatus.ERROR, "expression rejected", "MATH_RESULT\nstatus=ERROR\nreason=forbidden_construct")
        }
        return try {
            executeExpression(expr)
        } catch (e: UnsupportedOperationException) {
            error(e.message ?: "unsupported calculus operation", "unsupported")
        } catch (e: Exception) {
            error("parse error", e.message ?: "unknown")
        }
    }

    private fun isForbidden(expr: String): Boolean {
        val lower = expr.lowercase(Locale.US)
        return listOf("import", "exec", "eval", "open(", "read(", "write(", "system", "subprocess", "os.", "shutil", "socket", "http").any { lower.contains(it) }
    }

    private fun executeExpression(expr: String): ToolResult {
        val call = parseCall(expr) ?: parseNaturalCalculus(expr)
        return when (call?.name) {
            "diff", "derivative" -> executeDerivative(call)
            "integrate", "antiderivative" -> executeAntiderivative(call)
            "integral", "definite" -> executeDefiniteIntegral(call)
            "limit" -> executeLimit(call)
            else -> {
                val normalized = expr.trim()
                    .replace("^", "**")
                    .replace("×", "*")
                    .replace("÷", "/")
                    .replace("π", "PI")
                    .replace("√", "sqrt")
                val value = formatNumber(ExpressionEvaluator.eval(normalized))
                ToolResult(
                    family,
                    ToolStatus.SUCCESS,
                    value,
                    "MATH_RESULT\nstatus=SUCCESS\nvalue=$value"
                )
            }
        }
    }

    private fun executeDerivative(call: FunctionCall): ToolResult {
        require(call.args.size in 1..2) {
            "${call.name} expects an expression and optional variable"
        }
        val expression = call.args[0]
        val variable = call.args.getOrNull(1) ?: CalculusEngine.inferVariable(expression)
        val answer = CalculusEngine.derivative(expression, variable)
        val formula = blockLatex(
            "\\frac{d}{d$variable}\\left(${CalculusEngine.toLatex(expression)}\\right)=${answer.latex}"
        )
        return calculusSuccess("derivative", formula, answer.plain)
    }

    private fun executeAntiderivative(call: FunctionCall): ToolResult {
        require(call.args.size in 1..2) {
            "${call.name} expects an expression and optional variable"
        }
        val expression = call.args[0]
        val variable = call.args.getOrNull(1) ?: CalculusEngine.inferVariable(expression)
        val answer = CalculusEngine.antiderivative(expression, variable)
        val formula = blockLatex(
            "\\int ${CalculusEngine.toLatex(expression)}\\,d$variable=${answer.latex}+C"
        )
        return calculusSuccess("antiderivative", formula, answer.plain)
    }

    private fun executeDefiniteIntegral(call: FunctionCall): ToolResult {
        requireArgs(call, 4)
        val expression = call.args[0]
        val lower = call.args[1].toDoubleOrNull() ?: throw IllegalArgumentException("invalid lower bound")
        val upper = call.args[2].toDoubleOrNull() ?: throw IllegalArgumentException("invalid upper bound")
        val variable = call.args[3]
        val value = CalculusEngine.definiteIntegral(expression, variable, lower, upper)
        val formatted = CalculusEngine.formatNumber(value)
        val formula = blockLatex(
            "\\int_{${CalculusEngine.formatNumber(lower)}}^{${CalculusEngine.formatNumber(upper)}} " +
                "${CalculusEngine.toLatex(expression)}\\,d$variable=$formatted"
        )
        return calculusSuccess("definite_integral", formula, formatted)
    }

    private fun executeLimit(call: FunctionCall): ToolResult {
        requireArgs(call, 3)
        val expression = call.args[0]
        val variable = call.args[1]
        val point = call.args[2].toDoubleOrNull() ?: throw IllegalArgumentException("invalid limit point")
        val value = CalculusEngine.limit(expression, variable, point)
        val formatted = CalculusEngine.formatNumber(value)
        val formula = blockLatex(
            "\\lim_{${variable}\\to${CalculusEngine.formatNumber(point)}} " +
                "${CalculusEngine.toLatex(expression)}=$formatted"
        )
        return calculusSuccess("limit", formula, formatted)
    }

    private fun calculusSuccess(operation: String, formula: String, value: String): ToolResult =
        ToolResult(
            family,
            ToolStatus.SUCCESS,
            formula,
            "MATH_RESULT\nstatus=SUCCESS\noperation=$operation\nlatex=$formula\nvalue=$value"
        )

    private fun blockLatex(body: String): String = "\$\$${body}\$\$"

    private fun requireArgs(call: FunctionCall, expected: Int) {
        require(call.args.size == expected) {
            "${call.name} expects $expected arguments"
        }
    }

    private fun parseCall(expression: String): FunctionCall? {
        val trimmed = expression.trim()
        val open = trimmed.indexOf('(')
        if (open <= 0 || !trimmed.endsWith(')')) return null
        val name = trimmed.substring(0, open).trim().lowercase(Locale.US)
        if (!name.matches(Regex("[a-z][a-z0-9_]*"))) return null
        val body = trimmed.substring(open + 1, trimmed.length - 1)
        return FunctionCall(name, splitArguments(body))
    }

    private fun parseNaturalCalculus(expression: String): FunctionCall? {
        val trimmed = expression.trim()
        val derivative = Regex(
            "(?i)^(?:(?:find|calculate|compute)\\s+)?(?:the\\s+)?derivative\\s+of\\s+(.+)$"
        ).matchEntire(trimmed)
        if (derivative != null) {
            return FunctionCall("derivative", listOf(derivative.groupValues[1]))
        }
        val integral = Regex(
            "(?i)^(?:(?:find|calculate|compute)\\s+)?(?:the\\s+)?(?:anti-?derivative|integral)\\s+of\\s+(.+)$"
        ).matchEntire(trimmed)
        if (integral != null) {
            return FunctionCall("integrate", listOf(integral.groupValues[1]))
        }
        return null
    }

    private fun splitArguments(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        input.forEachIndexed { index, character ->
            when (character) {
                '(' -> depth++
                ')' -> {
                    depth--
                    require(depth >= 0) { "unbalanced parentheses" }
                }
                ',' -> if (depth == 0) {
                    result += input.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        require(depth == 0) { "unbalanced parentheses" }
        result += input.substring(start).trim()
        require(result.all { it.isNotEmpty() }) { "empty calculus argument" }
        return result
    }

    private fun error(message: String, reason: String): ToolResult =
        ToolResult(family, ToolStatus.ERROR, message, "MATH_RESULT\nstatus=ERROR\nreason=$reason")

    private data class FunctionCall(val name: String, val args: List<String>)

    private fun formatNumber(v: Double): String = CalculusEngine.formatNumber(v)
}
