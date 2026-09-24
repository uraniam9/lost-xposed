package dev.lostxposed.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigContractTest {

    private val all = mapOf(
        ConfigSchema.KEY_VERSION to "1",
        "core.displayprofiles|*|refreshRate" to "120",
        "core.displayprofiles|com.maps|densityDpi" to "400",
        "core.displayprofiles|com.bank|densityDpi" to "560",
        "core.notificationrules|com.bank|keywords" to "overdraft,declined",
    )

    @Test
    fun `a caller sees its own settings and the star defaults`() {
        assertEquals(
            mapOf(
                ConfigSchema.KEY_VERSION to "1",
                "core.displayprofiles|*|refreshRate" to "120",
                "core.displayprofiles|com.maps|densityDpi" to "400",
            ),
            ConfigContract.visibleTo(all, setOf("com.maps"), privileged = false),
        )
    }

    /**
     * The point of the filter. Notification keywords are words the user chose to hide things
     * by; an unrelated app being able to enumerate them would be a privacy regression
     * introduced by the transport, not by the feature.
     */
    @Test
    fun `a caller cannot see another package's settings`() {
        val visible = ConfigContract.visibleTo(all, setOf("com.maps"), privileged = false)
        assertFalse(visible.keys.any { it.contains("com.bank") })
    }

    @Test
    fun `a uid shared by several packages sees all of them`() {
        val visible = ConfigContract.visibleTo(all, setOf("com.maps", "com.bank"), privileged = false)
        assertTrue(visible.containsKey("core.displayprofiles|com.maps|densityDpi"))
        assertTrue(visible.containsKey("core.notificationrules|com.bank|keywords"))
    }

    /** system_server hosts features that act across every package. */
    @Test
    fun `a privileged caller sees everything`() {
        assertEquals(all, ConfigContract.visibleTo(all, emptySet(), privileged = true))
    }

    @Test
    fun `an unknown caller still gets the schema version so it can refuse a newer config`() {
        assertEquals(
            mapOf(ConfigSchema.KEY_VERSION to "1", "core.displayprofiles|*|refreshRate" to "120"),
            ConfigContract.visibleTo(all, emptySet(), privileged = false),
        )
    }

    @Test
    fun `a malformed key is withheld rather than leaked`() {
        val odd = mapOf("not-a-structured-key" to "x", "a|b" to "y", "a|b|c|d" to "z")
        assertTrue(ConfigContract.visibleTo(odd, setOf("b"), privileged = false).isEmpty())
        assertEquals(odd, ConfigContract.visibleTo(odd, emptySet(), privileged = true))
    }
}
