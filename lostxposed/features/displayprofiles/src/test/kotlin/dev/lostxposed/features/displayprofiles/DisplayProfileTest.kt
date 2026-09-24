package dev.lostxposed.features.displayprofiles

import dev.lostxposed.core.api.ConfigSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class DisplayProfileTest {

    /**
     * The rule the whole feature rests on: a profile that sets one thing must not quietly
     * reset the other two. Zero means "leave alone", not "set it to zero" — which for density
     * would be an app that cannot lay itself out.
     */
    @Test
    fun `an unset field overrides nothing`() {
        val profile = DisplayProfile(densityDpi = 400)
        assertTrue(profile.overridesDensity)
        assertFalse(profile.overridesFontScale)
        assertFalse(profile.overridesRefreshRate)
    }

    @Test
    fun `a negative value is treated as unset rather than applied`() {
        val profile = DisplayProfile(densityDpi = -1, fontScale = -0.5f, refreshRate = -60f)
        assertTrue(profile.isEmpty)
        assertFalse(profile.overridesDensity)
        assertFalse(profile.overridesFontScale)
        assertFalse(profile.overridesRefreshRate)
    }

    @Test
    fun `empty means the hook has nothing to do`() {
        assertTrue(DisplayProfile().isEmpty)
        assertFalse(DisplayProfile(fontScale = 1.1f).isEmpty)
        assertFalse(DisplayProfile(refreshRate = 60f).isEmpty)
    }

    @Test
    fun `describe lists only what is actually overridden`() {
        assertEquals("nothing", DisplayProfile().describe())
        assertEquals("400dpi", DisplayProfile(densityDpi = 400).describe())
        assertEquals("60Hz", DisplayProfile(refreshRate = 60f).describe())
        assertEquals(
            "400dpi, font x1.15, 120Hz",
            DisplayProfile(400, 1.15f, 120f).describe(),
        )
    }

    @Test
    fun `reads each value from config under its own key`() {
        val profile = DisplayProfile.from(
            FakeConfig(
                mapOf(
                    DisplayProfile.KEY_DENSITY to "480",
                    DisplayProfile.KEY_FONT_SCALE to "1.2",
                    DisplayProfile.KEY_REFRESH_RATE to "90",
                ),
            ),
        )
        assertEquals(480, profile.densityDpi)
        assertEquals(1.2f, profile.fontScale, 0.001f)
        assertEquals(90f, profile.refreshRate, 0.001f)
    }

    /**
     * A malformed value must read as unset, not as zero. Config arrives as strings over a
     * binder call, so a bad one is a thing that can actually happen.
     */
    @Test
    fun `an unparseable value leaves that field alone`() {
        val profile = DisplayProfile.from(
            FakeConfig(
                mapOf(
                    DisplayProfile.KEY_DENSITY to "large",
                    DisplayProfile.KEY_FONT_SCALE to "",
                ),
            ),
        )
        assertTrue(profile.isEmpty)
    }

    @Test
    fun `nothing configured produces an empty profile`() {
        assertTrue(DisplayProfile.from(FakeConfig(emptyMap())).isEmpty)
    }
}
