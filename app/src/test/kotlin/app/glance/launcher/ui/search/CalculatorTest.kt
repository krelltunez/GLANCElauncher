package app.glance.launcher.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculatorTest {
    private fun calc(q: String) = Calculator.evaluate(q)?.let(Calculator::format)

    @Test
    fun `arithmetic with precedence, parentheses, powers and the typed operator signs`() {
        assertEquals("84", calc("12*7"))
        assertEquals("14", calc("2+3*4"))
        assertEquals("3.5", calc("(3+4)/2"))
        assertEquals("1024", calc("2^10"))
        assertEquals("512", calc("2^3^2"))
        assertEquals("84", calc("12 x 7"))
        assertEquals("84", calc("12×7"))
        assertEquals("4", calc("12÷3"))
        assertEquals("1", calc("10 % 3"))
        assertEquals("-2", calc("-5+3"))
        assertEquals("33.33333333", calc("100/3"))
        assertEquals("7.5", calc("2.5*3"))
    }

    @Test
    fun `a bare number, a date, a phone number or text is not arithmetic`() {
        assertNull(calc("2026"))
        assertNull(calc("-5"))
        assertNull(calc("2026-10-22"))
        assertNull(calc("10/22"))
        assertNull(calc("10/22/26"))
        // A short "a/b" reads as a date, as quick capture reads it: Jan 3, not a third.
        assertNull(calc("1/3"))
        assertNull(calc("555-123-4567"))
        assertNull(calc("call mom 2pm"))
        assertNull(calc("1,000+2"))
    }

    @Test
    fun `no answer for a malformed sum or one without a finite result`() {
        assertNull(calc("1/0"))
        assertNull(calc("5%0"))
        assertNull(calc("(1+2"))
        assertNull(calc("1+2)"))
        assertNull(calc("1++"))
    }
}
