package com.ev.terminal.tools.math

import java.util.Locale
import kotlin.math.abs

data class CalculusAnswer(
    val plain: String,
    val latex: String
)

/**
 * Small, deterministic calculus engine for the on-device MATH tool.
 *
 * Symbolic operations intentionally cover a documented elementary subset. A
 * numerical method is used for definite integrals and limits because those
 * operations do not need a symbolic antiderivative.
 */
object CalculusEngine {

    fun derivative(expression: String, variable: String): CalculusAnswer {
        val variableName = requireVariable(variable)
        val result = simplify(differentiate(ExpressionParser(expression).parse(), variableName))
        return answer(result)
    }

    fun antiderivative(expression: String, variable: String): CalculusAnswer {
        val variableName = requireVariable(variable)
        val result = integrate(ExpressionParser(expression).parse(), variableName)
            ?: throw UnsupportedOperationException(
                "unsupported antiderivative for the supported elementary rules"
            )
        return answer(simplify(result))
    }

    fun definiteIntegral(
        expression: String,
        variable: String,
        lower: Double,
        upper: Double,
        subdivisions: Int = 1000
    ): Double {
        val variableName = requireVariable(variable)
        require(lower.isFinite() && upper.isFinite()) { "bounds must be finite" }
        require(subdivisions > 0 && subdivisions % 2 == 0) {
            "subdivisions must be a positive even number"
        }
        if (lower == upper) return 0.0

        val direction = if (upper >= lower) 1.0 else -1.0
        val start = if (direction > 0) lower else upper
        val end = if (direction > 0) upper else lower
        val step = (end - start) / subdivisions
        val parsed = ExpressionParser(expression).parse()
        var sum = sample(parsed, variableName, start) + sample(parsed, variableName, end)
        for (index in 1 until subdivisions) {
            val x = start + index * step
            val weight = if (index % 2 == 0) 2.0 else 4.0
            sum += weight * sample(parsed, variableName, x)
        }
        return direction * step * sum / 3.0
    }

    fun limit(expression: String, variable: String, point: Double): Double {
        val variableName = requireVariable(variable)
        require(point.isFinite()) { "limit point must be finite" }
        val epsilons = doubleArrayOf(1e-2, 1e-3, 1e-4, 1e-5, 1e-6, 1e-7)
        val parsed = ExpressionParser(expression).parse()
        var previous: Double? = null
        var converged: Double? = null
        for (epsilon in epsilons) {
            val left = sample(parsed, variableName, point - epsilon)
            val right = sample(parsed, variableName, point + epsilon)
            if (!left.isFinite() || !right.isFinite()) continue
            val average = (left + right) / 2.0
            if (abs(left - right) < 1e-4 &&
                (previous == null || abs(average - previous) < 1e-4)
            ) {
                converged = average
            }
            previous = average
        }
        if (converged != null) return converged
        throw UnsupportedOperationException("limit did not converge numerically")
    }

    fun toLatex(expression: String): String =
        toLatex(simplify(ExpressionParser(expression).parse()))

    /** Returns the first variable in an expression, or x for constant expressions. */
    fun inferVariable(expression: String): String {
        val variables = linkedSetOf<String>()
        collectVariables(ExpressionParser(expression).parse(), variables)
        return variables.firstOrNull() ?: "x"
    }

    fun formatNumber(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        if (value == Math.floor(value) && abs(value) < 1e15) return value.toLong().toString()
        return String.format(Locale.US, "%.6g", value)
    }

    private fun requireVariable(variable: String): String {
        val normalized = variable.trim().lowercase(Locale.US)
        require(normalized.matches(Regex("[a-z][a-z0-9_]*"))) {
            "invalid calculus variable: $variable"
        }
        return normalized
    }

    private fun answer(node: Node): CalculusAnswer {
        val latex = toLatex(node)
        return CalculusAnswer(plain = latex, latex = latex)
    }

    private fun sample(node: Node, variable: String, value: Double): Double {
        val result = evaluate(node, variable, value)
        if (!result.isFinite()) throw UnsupportedOperationException("expression is not finite at $value")
        return result
    }

    private fun evaluate(node: Node, variable: String, value: Double): Double = when (node) {
        is Node.Number -> node.value
        is Node.Variable -> if (node.name == variable) {
            value
        } else {
            throw UnsupportedOperationException("unbound variable: ${node.name}")
        }
        is Node.Add -> evaluate(node.left, variable, value) + evaluate(node.right, variable, value)
        is Node.Sub -> evaluate(node.left, variable, value) - evaluate(node.right, variable, value)
        is Node.Mul -> evaluate(node.left, variable, value) * evaluate(node.right, variable, value)
        is Node.Div -> evaluate(node.left, variable, value) / evaluate(node.right, variable, value)
        is Node.Pow -> Math.pow(
            evaluate(node.base, variable, value),
            evaluate(node.exponent, variable, value)
        )
        is Node.Neg -> -evaluate(node.value, variable, value)
        is Node.Function -> {
            val argument = evaluate(node.argument, variable, value)
            when (node.name) {
                "sin" -> Math.sin(argument)
                "cos" -> Math.cos(argument)
                "tan" -> Math.tan(argument)
                "asin" -> Math.asin(argument)
                "acos" -> Math.acos(argument)
                "atan" -> Math.atan(argument)
                "sqrt" -> Math.sqrt(argument)
                "exp" -> Math.exp(argument)
                "ln" -> Math.log(argument)
                "lnabs" -> Math.log(abs(argument))
                "abs" -> abs(argument)
                "floor" -> Math.floor(argument)
                "ceil" -> Math.ceil(argument)
                else -> throw UnsupportedOperationException("unsupported numerical function: ${node.name}")
            }
        }
    }

    private fun differentiate(node: Node, variable: String): Node = when (node) {
        is Node.Number -> Node.Number(0.0)
        is Node.Variable -> Node.Number(if (node.name == variable) 1.0 else 0.0)
        is Node.Add -> Node.Add(differentiate(node.left, variable), differentiate(node.right, variable))
        is Node.Sub -> Node.Sub(differentiate(node.left, variable), differentiate(node.right, variable))
        is Node.Mul -> Node.Add(
            Node.Mul(differentiate(node.left, variable), node.right),
            Node.Mul(node.left, differentiate(node.right, variable))
        )
        is Node.Div -> Node.Div(
            Node.Sub(
                Node.Mul(differentiate(node.left, variable), node.right),
                Node.Mul(node.left, differentiate(node.right, variable))
            ),
            Node.Pow(node.right, Node.Number(2.0))
        )
        is Node.Pow -> {
            if (node.exponent is Node.Number) {
                Node.Mul(
                    Node.Mul(node.exponent, Node.Pow(node.base, Node.Number(node.exponent.value - 1.0))),
                    differentiate(node.base, variable)
                )
            } else {
                Node.Mul(
                    node,
                    Node.Add(
                        Node.Mul(differentiate(node.exponent, variable), Node.Function("ln", node.base)),
                        Node.Div(
                            Node.Mul(node.exponent, differentiate(node.base, variable)),
                            node.base
                        )
                    )
                )
            }
        }
        is Node.Neg -> Node.Neg(differentiate(node.value, variable))
        is Node.Function -> {
            val inner = differentiate(node.argument, variable)
            when (node.name) {
                "sin" -> Node.Mul(Node.Function("cos", node.argument), inner)
                "cos" -> Node.Mul(Node.Neg(Node.Function("sin", node.argument)), inner)
                "tan" -> Node.Mul(
                    Node.Div(Node.Number(1.0), Node.Pow(Node.Function("cos", node.argument), Node.Number(2.0))),
                    inner
                )
                "exp" -> Node.Mul(Node.Function("exp", node.argument), inner)
                "ln" -> Node.Div(inner, node.argument)
                "sqrt" -> Node.Div(
                    inner,
                    Node.Mul(Node.Number(2.0), Node.Function("sqrt", node.argument))
                )
                "asin" -> Node.Div(inner, Node.Function("sqrt", Node.Sub(Node.Number(1.0), Node.Pow(node.argument, Node.Number(2.0)))))
                "acos" -> Node.Neg(Node.Div(inner, Node.Function("sqrt", Node.Sub(Node.Number(1.0), Node.Pow(node.argument, Node.Number(2.0))))))
                "atan" -> Node.Div(inner, Node.Add(Node.Number(1.0), Node.Pow(node.argument, Node.Number(2.0))))
                else -> throw UnsupportedOperationException("unsupported derivative function: ${node.name}")
            }
        }
    }

    private fun integrate(node: Node, variable: String): Node? = when (node) {
        is Node.Number -> Node.Mul(node, Node.Variable(variable))
        is Node.Variable -> if (node.name == variable) {
            Node.Div(Node.Pow(node, Node.Number(2.0)), Node.Number(2.0))
        } else {
            Node.Mul(node, Node.Variable(variable))
        }
        is Node.Add -> combine(node.left, node.right, variable, ::add)
        is Node.Sub -> combine(node.left, node.right, variable, ::subtract)
        is Node.Neg -> integrate(node.value, variable)?.let(::negate)
        is Node.Mul -> when {
            isConstant(node.left, variable) -> integrate(node.right, variable)?.let { Node.Mul(node.left, it) }
            isConstant(node.right, variable) -> integrate(node.left, variable)?.let { Node.Mul(it, node.right) }
            else -> null
        }
        is Node.Div -> when {
            node.left is Node.Number && node.left.value == 1.0 && node.right == Node.Variable(variable) ->
                Node.Function("lnAbs", node.right)
            node.right is Node.Number && node.right.value != 0.0 ->
                integrate(node.left, variable)?.let { Node.Div(it, node.right) }
            else -> null
        }
        is Node.Pow -> {
            val exponent = node.exponent as? Node.Number
            if (node.base == Node.Variable(variable) && exponent != null) {
                if (exponent.value == -1.0) {
                    Node.Function("lnAbs", node.base)
                } else {
                    val next = exponent.value + 1.0
                    Node.Div(Node.Pow(node.base, Node.Number(next)), Node.Number(next))
                }
            } else {
                null
            }
        }
        is Node.Function -> if (node.argument == Node.Variable(variable)) {
            val x = Node.Variable(variable)
            when (node.name) {
                "sin" -> negate(Node.Function("cos", x))
                "cos" -> Node.Function("sin", x)
                "exp" -> Node.Function("exp", x)
                "ln" -> Node.Sub(Node.Mul(x, Node.Function("ln", x)), x)
                "sqrt" -> Node.Mul(
                    Node.Div(Node.Number(2.0), Node.Number(3.0)),
                    Node.Pow(x, Node.Number(1.5))
                )
                "tan" -> negate(Node.Function("lnAbs", Node.Function("cos", x)))
                else -> null
            }
        } else {
            null
        }
    }

    private fun combine(
        left: Node,
        right: Node,
        variable: String,
        operation: (Node, Node) -> Node
    ): Node? {
        val leftIntegral = integrate(left, variable) ?: return null
        val rightIntegral = integrate(right, variable) ?: return null
        return operation(leftIntegral, rightIntegral)
    }

    private fun isConstant(node: Node, variable: String): Boolean = when (node) {
        is Node.Number -> true
        is Node.Variable -> node.name != variable
        else -> false
    }

    private fun add(left: Node, right: Node): Node = Node.Add(left, right)
    private fun subtract(left: Node, right: Node): Node = Node.Sub(left, right)
    private fun negate(node: Node): Node = Node.Neg(node)

    private fun simplify(node: Node): Node = when (node) {
        is Node.Number -> node
        is Node.Variable -> node
        is Node.Neg -> {
            val value = simplify(node.value)
            when (value) {
                is Node.Number -> Node.Number(-value.value)
                is Node.Neg -> value.value
                else -> Node.Neg(value)
            }
        }
        is Node.Add -> {
            val left = simplify(node.left)
            val right = simplify(node.right)
            when {
                left is Node.Number && right is Node.Number -> Node.Number(left.value + right.value)
                isZero(left) -> right
                isZero(right) -> left
                else -> Node.Add(left, right)
            }
        }
        is Node.Sub -> {
            val left = simplify(node.left)
            val right = simplify(node.right)
            when {
                left is Node.Number && right is Node.Number -> Node.Number(left.value - right.value)
                isZero(right) -> left
                else -> Node.Sub(left, right)
            }
        }
        is Node.Mul -> {
            val left = simplify(node.left)
            val right = simplify(node.right)
            when {
                left is Node.Number && right is Node.Number -> Node.Number(left.value * right.value)
                isZero(left) || isZero(right) -> Node.Number(0.0)
                isOne(left) -> right
                isOne(right) -> left
                else -> Node.Mul(left, right)
            }
        }
        is Node.Div -> {
            val left = simplify(node.left)
            val right = simplify(node.right)
            when {
                left is Node.Number && right is Node.Number && right.value != 0.0 -> Node.Number(left.value / right.value)
                isZero(left) -> Node.Number(0.0)
                isOne(right) -> left
                else -> Node.Div(left, right)
            }
        }
        is Node.Pow -> {
            val base = simplify(node.base)
            val exponent = simplify(node.exponent)
            when {
                exponent is Node.Number && exponent.value == 0.0 -> Node.Number(1.0)
                exponent is Node.Number && exponent.value == 1.0 -> base
                base is Node.Number && exponent is Node.Number -> Node.Number(Math.pow(base.value, exponent.value))
                else -> Node.Pow(base, exponent)
            }
        }
        is Node.Function -> Node.Function(node.name, simplify(node.argument))
    }

    private fun isZero(node: Node): Boolean = node is Node.Number && abs(node.value) < 1e-12
    private fun isOne(node: Node): Boolean = node is Node.Number && abs(node.value - 1.0) < 1e-12

    private fun collectVariables(node: Node, variables: MutableSet<String>) {
        when (node) {
            is Node.Variable -> variables += node.name
            is Node.Number -> Unit
            is Node.Add -> {
                collectVariables(node.left, variables)
                collectVariables(node.right, variables)
            }
            is Node.Sub -> {
                collectVariables(node.left, variables)
                collectVariables(node.right, variables)
            }
            is Node.Mul -> {
                collectVariables(node.left, variables)
                collectVariables(node.right, variables)
            }
            is Node.Div -> {
                collectVariables(node.left, variables)
                collectVariables(node.right, variables)
            }
            is Node.Pow -> {
                collectVariables(node.base, variables)
                collectVariables(node.exponent, variables)
            }
            is Node.Neg -> collectVariables(node.value, variables)
            is Node.Function -> collectVariables(node.argument, variables)
        }
    }

    private fun toLatex(node: Node, parentPrecedence: Int = 0): String {
        val precedence = precedence(node)
        val rendered = when (node) {
            is Node.Number -> formatNumber(node.value)
            is Node.Variable -> node.name
            is Node.Neg -> "-${toLatex(node.value, precedence)}"
            is Node.Add -> addLatex(node.left, node.right)
            is Node.Sub -> "${toLatex(node.left, precedence)}-${toLatex(node.right, precedence + 1)}"
            is Node.Mul -> {
                val left = toLatex(node.left, precedence)
                val right = toLatex(node.right, precedence)
                if (node.left is Node.Number && node.right !is Node.Number) left + right else "$left\\,$right"
            }
            is Node.Div -> "\\frac{${toLatex(node.left)}}{${toLatex(node.right)}}"
            is Node.Pow -> "${toLatex(node.base, precedence)}^{${toLatex(node.exponent)}}"
            is Node.Function -> functionLatex(node)
        }
        return if (precedence < parentPrecedence) "\\left($rendered\\right)" else rendered
    }

    private fun addLatex(left: Node, right: Node): String {
        val leftLatex = toLatex(left, 1)
        return if (right is Node.Neg) {
            "$leftLatex-${toLatex(right.value, 1)}"
        } else {
            "$leftLatex+${toLatex(right, 1)}"
        }
    }

    private fun functionLatex(node: Node.Function): String {
        val argument = toLatex(node.argument)
        return when (node.name) {
            "sqrt" -> "\\sqrt{$argument}"
            "exp" -> "e^{$argument}"
            "lnAbs" -> "\\ln\\left|$argument\\right|"
            "abs" -> "\\left|$argument\\right|"
            "ln" -> "\\ln\\left($argument\\right)"
            "sin", "cos", "tan", "asin", "acos", "atan" -> "\\${node.name}\\left($argument\\right)"
            else -> "${node.name}\\left($argument\\right)"
        }
    }

    private fun precedence(node: Node): Int = when (node) {
        is Node.Add, is Node.Sub -> 1
        is Node.Mul, is Node.Div -> 2
        is Node.Pow -> 3
        is Node.Neg -> 4
        else -> 5
    }

    private sealed class Node {
        data class Number(val value: Double) : Node()
        data class Variable(val name: String) : Node()
        data class Add(val left: Node, val right: Node) : Node()
        data class Sub(val left: Node, val right: Node) : Node()
        data class Mul(val left: Node, val right: Node) : Node()
        data class Div(val left: Node, val right: Node) : Node()
        data class Pow(val base: Node, val exponent: Node) : Node()
        data class Neg(val value: Node) : Node()
        data class Function(val name: String, val argument: Node) : Node()
    }

    private class ExpressionParser(expression: String) {
        private val tokens = tokenize(expression)
        private var position = 0

        fun parse(): Node {
            if (tokens.isEmpty()) throw IllegalArgumentException("empty expression")
            val result = parseAddSub()
            if (position != tokens.size) throw IllegalArgumentException("unexpected token: ${tokens[position]}")
            return result
        }

        private fun parseAddSub(): Node {
            var result = parseMulDiv()
            while (peek() == "+" || peek() == "-") {
                val operator = next()
                val right = parseMulDiv()
                result = if (operator == "+") Node.Add(result, right) else Node.Sub(result, right)
            }
            return result
        }

        private fun parseMulDiv(): Node {
            var result = parsePower()
            while (true) {
                when (peek()) {
                    "*" -> {
                        next()
                        result = Node.Mul(result, parsePower())
                    }
                    "/" -> {
                        next()
                        result = Node.Div(result, parsePower())
                    }
                    else -> {
                        if (startsPrimary(peek())) result = Node.Mul(result, parsePower()) else return result
                    }
                }
            }
        }

        private fun parsePower(): Node {
            val base = parseUnary()
            if (peek() == "^" || peek() == "**") {
                next()
                return Node.Pow(base, parsePower())
            }
            return base
        }

        private fun parseUnary(): Node = when (peek()) {
            "+" -> {
                next()
                parseUnary()
            }
            "-" -> {
                next()
                Node.Neg(parseUnary())
            }
            else -> parsePrimary()
        }

        private fun parsePrimary(): Node {
            val token = next()
            if (token == "(") {
                val result = parseAddSub()
                expect(")")
                return result
            }
            token.toDoubleOrNull()?.let { return Node.Number(it) }
            if (!token.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*"))) {
                throw IllegalArgumentException("unexpected token: $token")
            }
            val name = token.lowercase(Locale.US)
            if (peek() == "(") {
                next()
                val argument = parseAddSub()
                expect(")")
                return Node.Function(name, argument)
            }
            return when (name) {
                "pi" -> Node.Number(Math.PI)
                "e" -> Node.Number(Math.E)
                else -> Node.Variable(name)
            }
        }

        private fun startsPrimary(token: String?): Boolean = token != null &&
            (token == "(" || token.toDoubleOrNull() != null || token.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*")))

        private fun peek(): String? = tokens.getOrNull(position)
        private fun next(): String = tokens.getOrNull(position++)
            ?: throw IllegalArgumentException("unexpected end of expression")

        private fun expect(expected: String) {
            if (next() != expected) throw IllegalArgumentException("expected $expected")
        }

        private companion object {
            fun tokenize(expression: String): List<String> {
                val result = mutableListOf<String>()
                var index = 0
                val source = expression.trim()
                while (index < source.length) {
                    val char = source[index]
                    when {
                        char.isWhitespace() -> index++
                        char.isDigit() || char == '.' -> {
                            val start = index
                            index++
                            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
                            result += source.substring(start, index)
                        }
                        char.isLetter() || char == '_' -> {
                            val start = index
                            index++
                            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                            result += source.substring(start, index)
                        }
                        char == '*' && index + 1 < source.length && source[index + 1] == '*' -> {
                            result += "**"
                            index += 2
                        }
                        char in charArrayOf('+', '-', '*', '/', '^', '(', ')') -> {
                            result += char.toString()
                            index++
                        }
                        else -> throw IllegalArgumentException("unexpected character: $char")
                    }
                }
                return result
            }
        }
    }
}
