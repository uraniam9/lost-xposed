package dev.lostxposed.features.textengine

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

class TextEngineSettingsTest {

    private fun settings(vararg pairs: Pair<String, String>) =
        TextEngineSettings.from(FakeConfig(pairs.toMap()))

    @Test
    fun `on by default, because a keyboard in scope was an opt-in already`() {
        val defaults = settings()
        assertTrue(defaults.enabled)
        assertFalse(defaults.wordJump)
        assertFalse(defaults.selectMode)
        assertEquals(28f, defaults.pxPerStep, 0.001f)
    }

    /**
     * A tiny pxPerStep means one pixel of travel moves the caret many characters, which is
     * not a fast cursor but an uncontrollable one. Zero would divide by zero.
     */
    @Test
    fun `step distance is floored so the gesture cannot become unusable`() {
        assertEquals(4f, settings(TextEngineSettings.KEY_PX_PER_STEP to "0").pxPerStep, 0.001f)
        assertEquals(4f, settings(TextEngineSettings.KEY_PX_PER_STEP to "-10").pxPerStep, 0.001f)
        assertEquals(4f, settings(TextEngineSettings.KEY_PX_PER_STEP to "1").pxPerStep, 0.001f)
        assertEquals(40f, settings(TextEngineSettings.KEY_PX_PER_STEP to "40").pxPerStep, 0.001f)
    }

    /**
     * Truncated, not rounded. A drag has to fully cross a step before it counts, so a finger
     * resting just past the threshold does not jitter the caret back and forth.
     */
    @Test
    fun `a drag has to cross a whole step before the caret moves`() {
        val s = TextEngineSettings(pxPerStep = 28f)
        assertEquals(0, s.steps(0f))
        assertEquals(0, s.steps(27.9f))
        assertEquals(1, s.steps(28f))
        assertEquals(1, s.steps(55.9f))
        assertEquals(2, s.steps(56f))
    }

    @Test
    fun `dragging back moves the caret back`() {
        val s = TextEngineSettings(pxPerStep = 28f)
        assertEquals(-1, s.steps(-28f))
        assertEquals(-3, s.steps(-90f))
        assertEquals(0, s.steps(-27.9f))
    }

    @Test
    fun `a faster setting means fewer pixels per step`() {
        val slow = TextEngineSettings(pxPerStep = 40f)
        val fast = TextEngineSettings(pxPerStep = 10f)
        assertTrue(fast.steps(100f) > slow.steps(100f))
        assertEquals(10, fast.steps(100f))
        assertEquals(2, slow.steps(100f))
    }

    @Test
    fun `flags are read from config`() {
        val s = settings(
            TextEngineSettings.KEY_WORD_JUMP to "true",
            TextEngineSettings.KEY_SELECT_MODE to "true",
            TextEngineSettings.KEY_ENABLED to "false",
        )
        assertTrue(s.wordJump)
        assertTrue(s.selectMode)
        assertFalse(s.enabled)
    }

    /** Nothing picked falls back to the built-in list rather than hooking nothing at all. */
    @Test
    fun `no chosen keyboards means the known ones`() {
        assertEquals(
            TextEngineSettings.DEFAULT_IME_PACKAGES,
            TextEngineSettings.imePackages(FakeConfig(emptyMap())),
        )
        assertEquals(
            TextEngineSettings.DEFAULT_IME_PACKAGES,
            TextEngineSettings.imePackages(
                FakeConfig(mapOf(TextEngineSettings.KEY_IME_PACKAGES to "  ,  , ")),
            ),
        )
    }

    @Test
    fun `chosen keyboards replace the built-in list, trimmed`() {
        assertEquals(
            setOf("com.a.keyboard", "com.b.keyboard"),
            TextEngineSettings.imePackages(
                FakeConfig(
                    mapOf(
                        TextEngineSettings.KEY_IME_PACKAGES to " com.a.keyboard , com.b.keyboard ,",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `the built-in list covers the keyboards most people use`() {
        listOf(
            "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey",
            "com.samsung.android.honeyboard",
        ).forEach {
            assertTrue("$it missing", it in TextEngineSettings.DEFAULT_IME_PACKAGES)
        }
    }
}
