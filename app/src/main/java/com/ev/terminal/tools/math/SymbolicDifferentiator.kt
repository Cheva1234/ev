package com.ev.terminal.tools.math

object SymbolicDifferentiator {

    fun diff(expr: String, variable: String): String {
        val tokens = tokenize(expr)
        val result = differentiate(parse(tokens), variable)
        return simplify(result)
    }

    private sealed class Node {
        data class Num(val value: Double) : Node()
        data class Var(val name: String) : Node()
        data class Add(val a: Node, val b: Node) : Node()
        data class Sub(val a: Node, val b: Node) : Node()
        data class Mul(val a: Node, val b: Node) : Node()
        data class Div(val a: Node, val b: Node) : Node()
        data class Pow(val a: Node, val b: Node) : Node()
        data class Func(val name: String, val arg: Node) : Node()
        data class Neg(val a: Node) : Node()
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        val s = expr.replace(" ", "")
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    tokens.add(s.substring(start, i))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && s[i].isLetterOrDigit()) i++
                    tokens.add(s.substring(start, i))
                }
                c == '*' && i + 1 < s.length && s[i + 1] == '*' -> {
                    tokens.add("**")
                    i += 2
                }
                else -> {
                    tokens.add(c.toString())
                    i++
                }
            }
        }
        return tokens
    }

    private fun parse(tokens: List<String>): Node = Parser(tokens).parse()

    private class Parser(private val tokens: List<String>) {
        private var pos = 0

        fun parse(): Node = parseExpr()

        private fun peek(): String? = tokens.getOrNull(pos)
        private fun next(): String = tokens[pos++]

        private fun parseExpr(): Node {
            var left = parseTerm()
            while (peek() == "+" || peek() == "-") {
                val op = next()
                val right = parseTerm()
                left = if (op == "+") Node.Add(left, right) else Node.Sub(left, right)
            }
            return left
        }

        private fun parseTerm(): Node {
            var left = parseFactor()
            while (peek() == "*" || peek() == "/") {
                val op = next()
                val right = parseFactor()
                left = if (op == "*") Node.Mul(left, right) else Node.Div(left, right)
            }
            return left
        }

        private fun parseFactor(): Node {
            var base = parsePrimary()
            if (peek() == "^" || peek() == "**") {
                next()
                val exp = parseFactor()
                base = Node.Pow(base, exp)
            }
            return base
        }

        private fun parsePrimary(): Node {
            val t = next()
            return when {
                t == "(" -> {
                    val inner = parseExpr()
                    next() // consume ')'
                    inner
                }
                t == "-" -> Node.Neg(parsePrimary())
                t == "+" -> parsePrimary()
                t.toDoubleOrNull() != null -> Node.Num(t.toDouble())
                t == "x" || t == "y" || t == "z" || t == "t" -> Node.Var(t)
                t == "pi" -> Node.Num(Math.PI)
                t == "e" -> Node.Num(Math.E)
                t == "sin" || t == "cos" || t == "tan" || t == "exp" || t == "ln" || t == "sqrt" -> {
                    next() // consume '('
                    val arg = parseExpr()
                    next() // consume ')'
                    Node.Func(t, arg)
                }
                else -> Node.Var(t)
            }
        }
    }

    private fun differentiate(node: Node, variable: String): Node = when (node) {
        is Node.Num -> Node.Num(0.0)
        is Node.Var -> if (node.name == variable) Node.Num(1.0) else Node.Num(0.0)
        is Node.Add -> Node.Add(differentiate(node.a, variable), differentiate(node.b, variable))
        is Node.Sub -> Node.Sub(differentiate(node.a, variable), differentiate(node.b, variable))
        is Node.Mul -> Node.Add(
            Node.Mul(differentiate(node.a, variable), node.b),
            Node.Mul(node.a, differentiate(node.b, variable))
        )
        is Node.Div -> Node.Div(
            Node.Sub(
                Node.Mul(differentiate(node.a, variable), node.b),
                Node.Mul(node.a, differentiate(node.b, variable))
            ),
            Node.Pow(node.b, Node.Num(2.0))
        )
        is Node.Pow -> {
            if (node.b is Node.Num) {
                Node.Mul(
                    Node.Mul(node.b, Node.Pow(node.a, Node.Num(node.b.value - 1))),
                    differentiate(node.a, variable)
                )
            } else {
                Node.Mul(
                    node,
                    differentiate(Node.Mul(node.b, Node.Func("ln", node.a)), variable)
                )
            }
        }
        is Node.Func -> when (node.name) {
            "sin" -> Node.Mul(Node.Func("cos", node.arg), differentiate(node.arg, variable))
            "cos" -> Node.Mul(Node.Neg(Node.Func("sin", node.arg)), differentiate(node.arg, variable))
            "tan" -> Node.Mul(
                Node.Div(Node.Num(1.0), Node.Pow(Node.Func("cos", node.arg), Node.Num(2.0))),
                differentiate(node.arg, variable)
            )
            "exp" -> Node.Mul(Node.Func("exp", node.arg), differentiate(node.arg, variable))
            "ln" -> Node.Div(differentiate(node.arg, variable), node.arg)
            "sqrt" -> Node.Mul(
                Node.Div(Node.Num(1.0), Node.Mul(Node.Num(2.0), Node.Func("sqrt", node.arg))),
                differentiate(node.arg, variable)
            )
            else -> Node.Num(0.0)
        }
        is Node.Neg -> Node.Neg(differentiate(node.a, variable))
    }

    private fun simplify(node: Node): String = when (node) {
        is Node.Num -> {
            val v = node.value
            if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else String.format("%.6g", v)
        }
        is Node.Var -> node.name
        is Node.Neg -> "-" + parenthesize(simplify(node.a), node.a)
        is Node.Add -> {
            val a = simplify(node.a)
            val b = simplify(node.b)
            if (a == "0") b else if (b == "0") a else "$a + $b"
        }
        is Node.Sub -> {
            val a = simplify(node.a)
            val b = simplify(node.b)
            if (b == "0") a else "$a - $b"
        }
        is Node.Mul -> {
            val a = simplify(node.a)
            val b = simplify(node.b)
            when {
                a == "0" || b == "0" -> "0"
                a == "1" -> b
                b == "1" -> a
                else -> "${parenthesize(a, node.a)}*${parenthesize(b, node.b)}"
            }
        }
        is Node.Div -> {
            val a = simplify(node.a)
            val b = simplify(node.b)
            if (b == "1") a else "${parenthesize(a, node.a)}/${parenthesize(b, node.b)}"
        }
        is Node.Pow -> {
            val a = simplify(node.a)
            val b = simplify(node.b)
            if (b == "1") a else "$a^$b"
        }
        is Node.Func -> "${node.name}(${simplify(node.arg)})"
    }

    private fun parenthesize(s: String, node: Node): String {
        val needsParens = node is Node.Add || node is Node.Sub || node is Node.Div || node is Node.Neg
        return if (needsParens) "($s)" else s
    }
}
