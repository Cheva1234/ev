package com.ev.terminal.tools.math

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.Tool
import com.ev.terminal.tools.ToolResult
import com.ev.terminal.tools.ToolStatus
import java.util.Locale

class MathTool : Tool {
    override val family = "MATH"
    override val operations = listOf("numeric", "symbolic", "unit")

    override suspend fun execute(command: EvclCommand): ToolResult {
        val expr = (command as? EvclCommand.Math)?.expression ?: return error("no expression")
        if (expr.isBlank()) {
            return ToolResult(family, ToolStatus.ERROR, "empty expression", "MATH_RESULT\nstatus=ERROR\nreason=empty_expression")
        }
        if (isForbidden(expr)) {
            return ToolResult(family, ToolStatus.ERROR, "expression rejected", "MATH_RESULT\nstatus=ERROR\nreason=forbidden_construct")
        }
        return try {
            val value = evaluate(expr)
            ToolResult(
                family,
                ToolStatus.SUCCESS,
                value,
                "MATH_RESULT\nstatus=SUCCESS\nvalue=$value"
            )
        } catch (e: Exception) {
            ToolResult(family, ToolStatus.ERROR, "parse error", "MATH_RESULT\nstatus=ERROR\nreason=${e.message ?: "unknown"}")
        }
    }

    private fun isForbidden(expr: String): Boolean {
        val lower = expr.lowercase(Locale.US)
        return listOf("import", "exec", "eval", "open(", "read(", "write(", "system", "subprocess", "os.", "shutil", "socket", "http").any { lower.contains(it) }
    }

    private fun evaluate(expr: String): String {
        val trimmed = expr.trim()
        val diffMatch = Regex("^diff\\((.+),([a-z])+\\)$").find(trimmed)
        if (diffMatch != null) {
            val inner = diffMatch.groupValues[1]
            val variable = diffMatch.groupValues[2]
            return SymbolicDifferentiator.diff(inner, variable)
        }
        val normalized = trimmed
            .replace("^", "**")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("π", "PI")
            .replace("√", "sqrt")
        val result = ExpressionEvaluator.eval(normalized)
        return formatNumber(result)
    }

    private fun formatNumber(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
        if (v == Math.floor(v) && Math.abs(v) < 1e15) {
            return v.toLong().toString()
        }
        return String.format(Locale.US, "%.6g", v)
    }

    private fun error(msg: String): ToolResult =
        ToolResult(family, ToolStatus.ERROR, msg, "MATH_RESULT\nstatus=ERROR\nreason=$msg")
}
