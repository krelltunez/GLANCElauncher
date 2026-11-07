package app.glance.launcher.ui.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.pow

/**
 * Arithmetic typed into search ("12*7", "(3+4)/2", "2^10"), as Sesame answers it. Pure, tested
 * on the JVM. Only a query made of numbers and operators, with at least one operator between two
 * numbers, is arithmetic: a bare number, a date or a phone number is not, so it stays a search.
 */
object Calculator {
    // No comma: "1,000" and "1,5" disagree on what it means, so a query with one is not arithmetic.
    private val ALLOWED = Regex("""^[\d\s.+\-*/x×÷%^()]+$""")
    private val HAS_OPERATION = Regex("""\d\s*[+\-*/x×÷%^]\s*[(\-]*\d""")
    /** Dates and phone numbers: "2026-10-22", "10/22", "10/22/26", "555-123-4567". Quick capture wants dates. */
    private val NOT_ARITHMETIC = listOf(
        Regex("""^\d+(-\d+){2,}$"""),
        Regex("""^\d{1,2}/\d{1,2}(/\d{2,4})?$"""),
    )

    /** The result of [query], or null when it is not arithmetic or has no finite answer. */
    fun evaluate(query: String): Double? {
        val q = query.trim()
        if (q.isEmpty() || !ALLOWED.matches(q) || !HAS_OPERATION.containsMatchIn(q)) return null
        if (NOT_ARITHMETIC.any { it.matches(q) }) return null
        val normalized = q.replace('x', '*').replace('×', '*').replace('÷', '/').replace(" ", "")
        return try {
            val parser = Parser(normalized)
            val value = parser.expression()
            if (!parser.atEnd() || value.isNaN() || value.isInfinite()) null else value
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** A result as a row shows it: whole numbers without a decimal point, others to ten significant digits. */
    fun format(value: Double): String {
        if (value == Math.rint(value) && abs(value) < 1e15) return value.toLong().toString()
        return BigDecimal(value).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    /** Recursive descent: expression = term (+|- term)*; term = power (*|/|% power)*; power = unary (^ power)?. */
    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i == s.length

        fun expression(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]
                val r = power()
                v = when (op) {
                    '*' -> v * r
                    '/' -> if (r == 0.0) throw IllegalArgumentException("division by zero") else v / r
                    else -> if (r == 0.0) throw IllegalArgumentException("modulo by zero") else v % r
                }
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return base.pow(power()) // right-associative: 2^3^2 is 2^9
            }
            return base
        }

        private fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return primary()
        }

        private fun primary(): Double {
            if (i < s.length && s[i] == '(') {
                i++
                val v = expression()
                if (i >= s.length || s[i] != ')') throw IllegalArgumentException("unclosed parenthesis")
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("number expected at $start")
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("bad number")
        }
    }
}
