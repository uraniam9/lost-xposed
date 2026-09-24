package dev.lostxposed.core.config

import dev.lostxposed.core.api.FeatureId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreConfigSourceTest {

    private val feature = FeatureId("core.displayprofiles")
    private val other = FeatureId("core.textengine")

    private fun sourceOf(vararg entries: Pair<String, String>, pkg: String = "com.example.app") =
        StoreConfigSource(MapConfigStore(entries.toMap()), feature, pkg)

    @Test
    fun `a per-package value wins over the star default`() {
        val config = sourceOf(
            "core.displayprofiles|*|densityDpi" to "400",
            "core.displayprofiles|com.example.app|densityDpi" to "560",
        )
        assertEquals(560, config.int("densityDpi", 0))
    }

    @Test
    fun `falls back to the star default when the package has no value`() {
        val config = sourceOf("core.displayprofiles|*|densityDpi" to "400")
        assertEquals(400, config.int("densityDpi", 0))
    }

    @Test
    fun `another package's value is not visible`() {
        val config = sourceOf("core.displayprofiles|com.other.app|densityDpi" to "560")
        assertEquals(0, config.int("densityDpi", 0))
        assertFalse(config.contains("densityDpi"))
    }

    @Test
    fun `an unset key yields the caller's default`() {
        val config = sourceOf()
        assertEquals(120, config.int("refreshRate", 120))
        assertEquals(1.0f, config.float("scale", 1.0f), 0f)
        assertEquals("auto", config.string("mode", "auto"))
        assertTrue(config.boolean("enabled", true))
    }

    /**
     * File channels carry no type information, so every value arrives as a string. A setting
     * that will not parse must behave as unconfigured rather than throw — this runs inside
     * system_server, where a throw is a bootloop.
     */
    @Test
    fun `an unparseable value falls back to the default rather than throwing`() {
        val config = sourceOf(
            "core.displayprofiles|*|densityDpi" to "not-a-number",
            "core.displayprofiles|*|scale" to "",
        )
        assertEquals(160, config.int("densityDpi", 160))
        assertEquals(1.0f, config.float("scale", 1.0f), 0f)
    }

    @Test
    fun `booleans accept either case and reject anything else`() {
        assertTrue(sourceOf("core.displayprofiles|*|k" to "true").boolean("k", false))
        assertTrue(sourceOf("core.displayprofiles|*|k" to "TRUE").boolean("k", false))
        assertFalse(sourceOf("core.displayprofiles|*|k" to "False").boolean("k", true))
        // Not "yes" — an unrecognised value is not silently read as true.
        assertFalse(sourceOf("core.displayprofiles|*|k" to "yes").boolean("k", false))
        assertTrue(sourceOf("core.displayprofiles|*|k" to "yes").boolean("k", true))
    }

    @Test
    fun `contains distinguishes a set value from a default`() {
        val config = sourceOf("core.displayprofiles|*|densityDpi" to "400")
        assertTrue(config.contains("densityDpi"))
        assertFalse(config.contains("refreshRate"))
    }

    @Test
    fun `isEmpty ignores keys belonging to other features`() {
        val store = MapConfigStore(mapOf("${other.value}|*|swipeFactor" to "1.05"))
        assertTrue(StoreConfigSource(store, feature, "com.example.app").isEmpty())
        assertFalse(StoreConfigSource(store, other, "com.example.app").isEmpty())
    }

    @Test
    fun `isEmpty is false for a value set only on another package`() {
        // The feature has work to do somewhere, just not here — that is a different thing
        // from being unconfigured, and the cheap skip must not swallow it.
        val store = MapConfigStore(mapOf("core.displayprofiles|com.other.app|densityDpi" to "560"))
        assertFalse(StoreConfigSource(store, feature, "com.example.app").isEmpty())
    }

    @Test
    fun `the provider hands each feature and package its own view`() {
        val provider = StoreConfigProvider(
            MapConfigStore(
                mapOf(
                    "core.displayprofiles|com.a|densityDpi" to "400",
                    "core.displayprofiles|com.b|densityDpi" to "560",
                ),
            ),
        )
        assertEquals(400, provider.forFeature(feature, "com.a").int("densityDpi", 0))
        assertEquals(560, provider.forFeature(feature, "com.b").int("densityDpi", 0))
    }
}
