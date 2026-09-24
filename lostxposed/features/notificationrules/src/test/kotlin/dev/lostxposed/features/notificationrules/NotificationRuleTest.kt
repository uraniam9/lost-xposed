package dev.lostxposed.features.notificationrules

import dev.lostxposed.core.api.ConfigSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Config as a plain map, so the parsing rules can be checked without an Android runtime. */
private class FakeConfig(private val values: Map<String, String>) : ConfigSource {
    override fun contains(key: String) = key in values
    override fun string(key: String, default: String?) = values[key] ?: default
    override fun int(key: String, default: Int) = values[key]?.toIntOrNull() ?: default
    override fun float(key: String, default: Float) = values[key]?.toFloatOrNull() ?: default
    override fun boolean(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
    override fun isEmpty() = values.isEmpty()
}

class NotificationRuleTest {

    private val fields = listOf("Payment received", "Your balance is now £40")

    @Test
    fun `keyword matching ignores case by default`() {
        assertTrue(NotificationRule(keywords = listOf("payment")).matches(fields))
        assertTrue(NotificationRule(keywords = listOf("PAYMENT")).matches(fields))
    }

    @Test
    fun `case sensitive matching respects case`() {
        val rule = NotificationRule(keywords = listOf("payment"), caseSensitive = true)
        assertFalse(rule.matches(fields))
        assertTrue(rule.copy(keywords = listOf("Payment")).matches(fields))
    }

    @Test
    fun `any keyword matching any field is enough`() {
        assertTrue(NotificationRule(keywords = listOf("nope", "balance")).matches(fields))
    }

    @Test
    fun `a substring counts as a match`() {
        assertTrue(NotificationRule(keywords = listOf("bala")).matches(fields))
    }

    @Test
    fun `no keywords matches nothing`() {
        assertFalse(NotificationRule().matches(fields))
        assertFalse(NotificationRule(keywords = listOf("payment")).matches(emptyList()))
    }

    @Test
    fun `isActive tracks whether the rule would ever do anything`() {
        assertFalse(NotificationRule().isActive)
        assertTrue(NotificationRule(blockAll = true).isActive)
        assertTrue(NotificationRule(keywords = listOf("x")).isActive)
        // caseSensitive alone is a modifier, not a rule.
        assertFalse(NotificationRule(caseSensitive = true).isActive)
    }

    @Test
    fun `keywords are split on commas, trimmed, and emptied entries dropped`() {
        val rule = NotificationRule.from(
            FakeConfig(mapOf(NotificationRule.KEY_KEYWORDS to " payment , ,balance,, ")),
        )
        assertEquals(listOf("payment", "balance"), rule.keywords)
    }

    @Test
    fun `an absent keyword list is empty rather than a list containing one empty string`() {
        val rule = NotificationRule.from(FakeConfig(emptyMap()))
        assertEquals(emptyList<String>(), rule.keywords)
        assertFalse(rule.isActive)
    }

    @Test
    fun `flags are read from config`() {
        val rule = NotificationRule.from(
            FakeConfig(
                mapOf(
                    NotificationRule.KEY_BLOCK_ALL to "true",
                    NotificationRule.KEY_CASE_SENSITIVE to "true",
                    NotificationRule.KEY_KEYWORDS to "x",
                ),
            ),
        )
        assertTrue(rule.blockAll)
        assertTrue(rule.caseSensitive)
    }

    @Test
    fun `describe says what the rule does`() {
        assertEquals("inactive", NotificationRule().describe())
        assertEquals("block all", NotificationRule(blockAll = true).describe())
        assertEquals(
            "keywords=payment/balance",
            NotificationRule(keywords = listOf("payment", "balance")).describe(),
        )
    }
}
