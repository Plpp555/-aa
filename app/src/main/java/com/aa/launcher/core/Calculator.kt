package com.aa.launcher.core

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow
import kotlin.math.sqrt

/** Spotlight の計算機能 (四則演算・累乗・%・括弧・√)。 */
object Calculator {

    private val allowed = Regex("^[0-9+\\-*/×÷^().,%\\s√πe]+$")

    fun looksLikeExpression(input: String): Boolean {
        val s = input.trim()
        if (s.length < 3 || !allowed.matches(s)) return false
        return s.any { it.isDigit() } && s.any { it in "+-*/×÷^%√" }
    }

    fun evaluate(input: String): String? {
        if (!looksLikeExpression(input)) return null
        return runCatching {
            val parser = Parser(input.replace('×', '*').replace('÷', '/').replace(',', '.').replace(" ", ""))
            val value = parser.parseExpression()
            if (!parser.atEnd()) return null
            format(value)
        }.getOrNull()
    }

    private fun format(v: Double): String? {
        if (v.isNaN() || v.isInfinite()) return null
        val bd = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
        return if (bd.scale() <= 0) bd.toBigInteger().toString() else bd.toPlainString()
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        private fun peek(): Char? = s.getOrNull(pos)

        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                v = when (peek()) {
                    '+' -> { pos++; v + parseTerm() }
                    '-' -> { pos++; v - parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                v = when (peek()) {
                    '*' -> { pos++; v * parseFactor() }
                    '/' -> { pos++; v / parseFactor() }
                    else -> return v
                }
            }
        }

        private fun parseFactor(): Double {
            var base = parseUnary()
            while (peek() == '%') {
                pos++
                base /= 100.0
            }
            if (peek() == '^') {
                pos++
                return base.pow(parseFactor())
            }
            return base
        }

        private fun parseUnary(): Double = when (peek()) {
            '-' -> { pos++; -parseUnary() }
            '+' -> { pos++; parseUnary() }
            '√' -> { pos++; sqrt(parseUnary()) }
            else -> parsePrimary()
        }

        private fun parsePrimary(): Double {
            val c = peek() ?: throw IllegalStateException()
            if (c == '(') {
                pos++
                val v = parseExpression()
                if (peek() != ')') throw IllegalStateException()
                pos++
                return v
            }
            if (c == 'π') { pos++; return Math.PI }
            if (c == 'e') { pos++; return Math.E }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            if (start == pos) throw IllegalStateException()
            return s.substring(start, pos).toDouble()
        }
    }
}
