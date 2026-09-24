package dev.lostxposed.features.smartstatusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTemplateTest {

    private val pieces = mapOf(
        "fuzzy" to "half past three",
        "time" to "15:32",
        "battery" to "53%",
        "day" to "Tue",
    )

    private fun expand(template: String) = ClockTemplate.expand(template) { pieces[it] }

    @Test
    fun `substitutes every known piece`() {
        assertEquals("half past three · 53%", expand("{fuzzy} · {battery}"))
        assertEquals("Tue 15:32", expand("{day} {time}"))
    }

    @Test
    fun `keeps whatever else is on the line exactly as typed`() {
        assertEquals("[15:32]", expand("[{time}]"))
        assertEquals("bat 53%!", expand("bat {battery}!"))
        assertEquals("no tokens here", expand("no tokens here"))
    }

    /**
     * A typo stays on screen. A status bar showing `{batery}` tells you what you got wrong;
     * one that quietly drops it leaves you restarting SystemUI trying to work it out.
     */
    @Test
    fun `an unknown token is left visible rather than dropped`() {
        assertEquals("15:32 {batery}", expand("{time} {batery}"))
        assertEquals(listOf("batery"), ClockTemplate.unknownTokens("{time} {batery}"))
    }

    @Test
    fun `unknown tokens are reported once each`() {
        assertEquals(
            listOf("nope"),
            ClockTemplate.unknownTokens("{nope} {time} {nope}"),
        )
        assertTrue(ClockTemplate.unknownTokens("{fuzzy} {battery}").isEmpty())
    }

    @Test
    fun `the same piece can be used more than once`() {
        assertEquals("53% 53%", expand("{battery} {battery}"))
    }

    @Test
    fun `malformed braces are not tokens`() {
        assertEquals("{time", expand("{time"))
        assertEquals("time}", expand("time}"))
        assertEquals("{ time }", expand("{ time }"))
    }

    @Test
    fun `the default template only uses pieces that exist`() {
        assertTrue(ClockTemplate.unknownTokens(ClockTemplate.DEFAULT).isEmpty())
    }

    @Test
    fun `every advertised token resolves`() {
        val missing = ClockTemplate.TOKENS.filter { ClockTemplate.unknownTokens("{$it}").isNotEmpty() }
        assertEquals(emptyList<String>(), missing)
    }
}
