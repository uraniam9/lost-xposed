package dev.lostxposed.features.smartstatusbar

import dev.lostxposed.core.api.ConfigSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeConfig(private val values: Map<String, String>) : ConfigSource {
    override fun contains(key: String) = key in values
    override fun string(key: String, default: String?) = values[key] ?: default
    override fun int(key: String, default: Int) = values[key]?.toIntOrNull() ?: default
    override fun float(key: String, default: Float) = values[key]?.toFloatOrNull() ?: default
    override fun boolean(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
    override fun isEmpty() = values.isEmpty()
}

class ClockAppearanceTest {

    @Test
    fun `accepts the three hex lengths`() {
        assertEquals(0xFFAABBCC.toInt(), ClockAppearance.parseColour("#ABC"))
        assertEquals(0xFFAABBCC.toInt(), ClockAppearance.parseColour("#AABBCC"))
        assertEquals(0x80AABBCC.toInt(), ClockAppearance.parseColour("#80AABBCC"))
    }

    @Test
    fun `the hash is optional and whitespace is forgiven`() {
        assertEquals(0xFFFF0000.toInt(), ClockAppearance.parseColour("  #ff0000 "))
        assertEquals(0xFFFF0000.toInt(), ClockAppearance.parseColour("ff0000"))
    }

    /**
     * A malformed colour leaves the clock alone. The alternative, throwing, would be thrown
     * inside SystemUI on every repaint.
     */
    @Test
    fun `anything unparseable is null rather than an error`() {
        assertNull(ClockAppearance.parseColour(null))
        assertNull(ClockAppearance.parseColour(""))
        assertNull(ClockAppearance.parseColour("   "))
        assertNull(ClockAppearance.parseColour("#"))
        assertNull(ClockAppearance.parseColour("red"))
        assertNull(ClockAppearance.parseColour("#GGHHII"))
        assertNull(ClockAppearance.parseColour("#ABCDE"))
    }

    @Test
    fun `size is clamped so the clock cannot be clipped out of the bar`() {
        assertEquals(
            ClockAppearance.MAX_SCALE,
            ClockAppearance.from(FakeConfig(mapOf(ClockAppearance.KEY_SCALE to "9"))).scale,
            0f,
        )
        assertEquals(
            ClockAppearance.MIN_SCALE,
            ClockAppearance.from(FakeConfig(mapOf(ClockAppearance.KEY_SCALE to "0.01"))).scale,
            0f,
        )
    }

    @Test
    fun `weight maps to the two independent flags`() {
        fun weight(value: String) =
            ClockAppearance.from(FakeConfig(mapOf(ClockAppearance.KEY_WEIGHT to value)))

        assertTrue(weight("bold").bold)
        assertFalse(weight("bold").italic)
        assertTrue(weight("italic").italic)
        assertFalse(weight("italic").bold)
        assertTrue(weight("bold-italic").bold)
        assertTrue(weight("bold-italic").italic)
        assertFalse(weight("normal").bold)
    }

    @Test
    fun `an unknown font falls back to leaving the typeface alone`() {
        fun family(value: String) =
            ClockAppearance.from(FakeConfig(mapOf(ClockAppearance.KEY_FAMILY to value))).family

        assertEquals("monospace", family("monospace"))
        assertNull(family("default"))
        assertNull(family("comic-sans"))
    }

    /** Nothing configured must mean nothing applied, so the clock keeps SystemUI's own style. */
    @Test
    fun `an empty config is the default appearance`() {
        val appearance = ClockAppearance.from(FakeConfig(emptyMap()))
        assertTrue(appearance.isDefault)
        assertEquals("half past three", appearance.apply("half past three"))
    }

    @Test
    fun `any styling stops it being the default`() {
        assertFalse(ClockAppearance(bold = true).isDefault)
        assertFalse(ClockAppearance(scale = 1.2f).isDefault)
        assertFalse(ClockAppearance(colour = 1).isDefault)
        assertFalse(ClockAppearance(family = "serif").isDefault)
    }
}
