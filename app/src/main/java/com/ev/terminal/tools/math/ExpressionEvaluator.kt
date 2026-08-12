package com.ev.terminal.tools.math

import java.util.ArrayDeque

object ExpressionEvaluator {

    fun eval(expr: String): Double {
        val tokens = tokenize(expr)
        val rpn = shuntingYard(tokens)
        return evaluateRpn(rpn)
    }

    private sealed class Token {
        data class Num(val value: Double) : Token()
        data class Op(val symbol: String, val precedence: Int, val rightAssoc: Boolean) : Token()
        data class Func(val name: String) : Token()
        object LParen : Token()
        object RParen : Token()
        object Comma : Token()
    }

    private val functions = mapOf(
        "sin" to { x: Double -> Math.sin(x) },
        "cos" to { x: Double -> Math.cos(x) },
        "tan" to { x: Double -> Math.tan(x) },
        "asin" to { x: Double -> Math.asin(x) },
        "acos" to { x: Double -> Math.acos(x) },
        "atan" to { x: Double -> Math.atan(x) },
        "sqrt" to { x: Double -> Math.sqrt(x) },
        "exp" to { x: Double -> Math.exp(x) },
        "ln" to { x: Double -> Math.log(x) },
        "abs" to { x: Double -> Math.abs(x) },
        "floor" to { x: Double -> Math.floor(x) },
        "ceil" to { x: Double -> Math.ceil(x) }
    )

    private val binaryFunctions = mapOf(
        "log" to { a: Double, b: Double -> Math.log(a) / Math.log(b) }
    )

    private val constants = mapOf(
        "pi" to Math.PI,
        "e" to Math.E
    )

    private val operators = mapOf(
        "+" to Token.Op("+", 2, false),
        "-" to Token.Op("-", 2, false),
        "*" to Token.Op("*", 3, false),
        "/" to Token.Op("/", 3, false),
        "%" to Token.Op("%", 3, false),
        "^" to Token.Op("^", 4, true),
        "**" to Token.Op("**", 4, true)
    )

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val s = expr.trim()
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    tokens.add(Token.Num(s.substring(start, i).toDouble()))
                    continue
                }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    val name = s.substring(start, i).lowercase()
                    when {
                        constants.containsKey(name) -> tokens.add(Token.Num(constants[name]!!))
                        functions.containsKey(name) || binaryFunctions.containsKey(name) -> tokens.add(Token.Func(name))
                        else -> throw IllegalArgumentException("unknown function: $name")
                    }
                    continue
                }
                c == '(' -> tokens.add(Token.LParen)
                c == ')' -> tokens.add(Token.RParen)
                c == ',' -> tokens.add(Token.Comma)
                c == ' ' -> {}
                else -> {
                    val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
                    if (operators.containsKey(two)) {
                        tokens.add(operators[two]!!)
                        i += 2
                        continue
                    }
                    val one = c.toString()
                    if (operators.containsKey(one)) {
                        tokens.add(operators[one]!!)
                    } else {
                        throw IllegalArgumentException("unexpected character: $c")
                    }
                }
            }
            i++
        }
        return tokens
    }

    private fun shuntingYard(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()
        var prev: Token? = null

        for (token in tokens) {
            when (token) {
                is Token.Num -> {
                    output.add(token)
                    if (prev is Token.Num) throw IllegalArgumentException("missing operator")
                }
                is Token.Func -> stack.addFirst(token)
                is Token.Op -> {
                    if (prev == null || prev is Token.Op || prev is Token.LParen) {
                        if (token.symbol == "-") {
                            output.add(Token.Num(-1.0))
                            stack.addFirst(Token.Op("*", 3, false))
                            prev = token
                            continue
                        }
                        if (token.symbol == "+") {
                            prev = token
                            continue
                        }
                    }
                    while (stack.isNotEmpty()) {
                        val top = stack.first()
                        if (top is Token.Op) {
                            val topPrec = top.precedence
                            val curPrec = token.precedence
                            val shouldPop = if (token.rightAssoc) topPrec > curPrec else topPrec >= curPrec
                            if (shouldPop) output.add(stack.removeFirst()) else break
                        } else break
                    }
                    stack.addFirst(token)
                }
                is Token.LParen -> stack.addFirst(token)
                is Token.RParen -> {
                    var found = false
                    while (stack.isNotEmpty()) {
                        val top = stack.removeFirst()
                        if (top is Token.LParen) {
                            found = true
                            break
                        }
                        output.add(top)
                    }
                    if (!found) throw IllegalArgumentException("unbalanced parentheses")
                    if (stack.isNotEmpty() && stack.first() is Token.Func) {
                        output.add(stack.removeFirst())
                    }
                }
                is Token.Comma -> {
                    while (stack.isNotEmpty() && stack.first() !is Token.LParen) {
                        output.add(stack.removeFirst())
                    }
                }
            }
            prev = token
        }
        while (stack.isNotEmpty()) {
            val top = stack.removeFirst()
            if (top is Token.LParen) throw IllegalArgumentException("unbalanced parentheses")
            output.add(top)
        }
        return output
    }

    private fun evaluateRpn(rpn: List<Token>): Double {
        val stack = ArrayDeque<Double>()
        for (token in rpn) {
            when (token) {
                is Token.Num -> stack.addFirst(token.value)
                is Token.Op -> {
                    if (stack.size < 2) throw IllegalArgumentException("not enough operands")
                    val b = stack.removeFirst()
                    val a = stack.removeFirst()
                    stack.addFirst(applyOp(token.symbol, a, b))
                }
                is Token.Func -> {
                    if (stack.isEmpty()) throw IllegalArgumentException("not enough operands")
                    val x = stack.removeFirst()
                    val fn = functions[token.name]
                    if (fn != null) {
                        stack.addFirst(fn(x))
                    } else {
                        val bf = binaryFunctions[token.name]
                            ?: throw IllegalArgumentException("unknown function: ${token.name}")
                        if (stack.isEmpty()) throw IllegalArgumentException("not enough operands")
                        val y = stack.removeFirst()
                        stack.addFirst(bf(y, x))
                    }
                }
                else -> throw IllegalArgumentException("invalid token in rpn")
            }
        }
        if (stack.size != 1) throw IllegalArgumentException("malformed expression")
        return stack.removeFirst()
    }

    private fun applyOp(symbol: String, a: Double, b: Double): Double = when (symbol) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        "/" -> {
            if (b == 0.0) throw IllegalArgumentException("division by zero")
            a / b
        }
        "%" -> a % b
        "^" -> Math.pow(a, b)
        "**" -> Math.pow(a, b)
        else -> throw IllegalArgumentException("unknown operator: $symbol")
    }
}
