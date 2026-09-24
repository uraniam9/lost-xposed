package dev.lostxposed.core.compat

import dev.lostxposed.core.api.Environment
import dev.lostxposed.core.api.FrameworkInfo
import dev.lostxposed.core.api.Oem
import dev.lostxposed.core.api.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatTableTest {

    private fun env(sdk: Int, oem: Oem) = Environment(
        sdkInt = sdk,
        release = sdk.toString(),
        oem = oem,
        oemVersion = null,
        fingerprint = "test",
        framework = FrameworkInfo.UNKNOWN,
    )

    private val table = compatTable<String> {
        match(sdk = 35..37, Oem.ONE_UI) using "one-ui"
        match(sdk = 35..37, Oem.AOSP, Oem.PIXEL) using "aosp"
        match(sdk = 33..34) using "legacy-any-oem"
        fallbackUnsupported()
    }

    @Test
    fun `matches on sdk and oem together`() {
        assertEquals("one-ui", table.resolveOrNull(env(36, Oem.ONE_UI)))
        assertEquals("aosp", table.resolveOrNull(env(36, Oem.PIXEL)))
        assertEquals("aosp", table.resolveOrNull(env(36, Oem.AOSP)))
    }

    /** A rule with no OEM listed matches every OEM. */
    @Test
    fun `a rule without oems matches any oem`() {
        assertEquals("legacy-any-oem", table.resolveOrNull(env(33, Oem.NOTHING)))
        assertEquals("legacy-any-oem", table.resolveOrNull(env(34, Oem.ONE_UI)))
    }

    @Test
    fun `the sdk range is inclusive at both ends`() {
        assertEquals("aosp", table.resolveOrNull(env(35, Oem.PIXEL)))
        assertEquals("aosp", table.resolveOrNull(env(37, Oem.PIXEL)))
        assertNull(table.resolveOrNull(env(38, Oem.PIXEL)))
    }

    /** Declaration order is the tie-break, so a specific row can precede a general one. */
    @Test
    fun `the first matching rule wins`() {
        val ordered = compatTable<String> {
            match(sdk = 36..36, Oem.NOTHING) using "specific"
            match(sdk = 30..40) using "general"
            fallbackUnsupported()
        }
        assertEquals("specific", ordered.resolveOrNull(env(36, Oem.NOTHING)))
        assertEquals("general", ordered.resolveOrNull(env(36, Oem.PIXEL)))
    }

    @Test
    fun `an unmatched environment names a reason rather than failing silently`() {
        val resolution = table.resolve(env(36, Oem.HYPER_OS))
        assertTrue(resolution is CompatTable.Resolution.None)
        assertEquals(
            Reason.NO_STRATEGY_FOR_ENV,
            (resolution as CompatTable.Resolution.None).reason,
        )
    }

    @Test
    fun `the unsupported reason is configurable`() {
        val strict = compatTable<String> {
            match(sdk = 36..36, Oem.PIXEL) using "pixel"
            fallbackUnsupported(Reason.FRAMEWORK_TOO_OLD)
        }
        val resolution = strict.resolve(env(30, Oem.PIXEL)) as CompatTable.Resolution.None
        assertEquals(Reason.FRAMEWORK_TOO_OLD, resolution.reason)
    }

    @Test
    fun `a fallback strategy catches everything the rules miss`() {
        val lenient = compatTable<String> {
            match(sdk = 36..36, Oem.PIXEL) using "pixel"
            fallback("generic")
        }
        assertEquals("pixel", lenient.resolveOrNull(env(36, Oem.PIXEL)))
        assertEquals("generic", lenient.resolveOrNull(env(21, Oem.OTHER)))
    }

    @Test
    fun `a table with no rules at all resolves to none`() {
        val empty = compatTable<String> { fallbackUnsupported() }
        assertTrue(empty.resolve(env(36, Oem.PIXEL)) is CompatTable.Resolution.None)
    }
}
