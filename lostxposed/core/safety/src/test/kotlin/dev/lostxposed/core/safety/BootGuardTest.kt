package dev.lostxposed.core.safety

import dev.lostxposed.core.api.FeatureId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BootGuardTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val risky = setOf(FeatureId("core.notificationrules"), FeatureId("core.hardwarekeys"))

    private fun guard(marker: File = File(temp.root, "boot.marker")) = BootGuard(marker) { 1_000L }

    @Test
    fun `a clean first boot proceeds and leaves a marker`() {
        val marker = File(temp.root, "boot.marker")
        assertEquals(BootGuard.Decision.Proceed, guard(marker).evaluate(risky))
        assertTrue(marker.exists())
    }

    /** A marker still present at the next start means the previous boot never finished. */
    @Test
    fun `finding a marker disables the risky features`() {
        val marker = File(temp.root, "boot.marker")
        guard(marker).evaluate(risky)

        val decision = guard(marker).evaluate(risky)
        assertTrue(decision is BootGuard.Decision.Disable)
        assertEquals(1, (decision as BootGuard.Decision.Disable).consecutiveFailures)
    }

    @Test
    fun `repeated failures escalate rather than oscillate`() {
        val marker = File(temp.root, "boot.marker")
        guard(marker).evaluate(risky)

        val first = guard(marker).evaluate(risky) as BootGuard.Decision.Disable
        val second = guard(marker).evaluate(risky) as BootGuard.Decision.Disable

        assertEquals(1, first.consecutiveFailures)
        assertEquals(2, second.consecutiveFailures)
        assertTrue(second.reason.contains("2 boots failed"))
        assertEquals(2, guard(marker).consecutiveFailures())
    }

    @Test
    fun `clearing the marker restores a clean boot`() {
        val marker = File(temp.root, "boot.marker")
        guard(marker).evaluate(risky)
        assertTrue(guard(marker).clearMarker())

        assertEquals(BootGuard.Decision.Proceed, guard(marker).evaluate(risky))
        assertEquals(0, guard(marker).consecutiveFailures())
    }

    @Test
    fun `clearing an absent marker succeeds`() {
        assertTrue(guard(File(temp.root, "never-written")).clearMarker())
    }

    @Test
    fun `no risky features means no marker and no decision to make`() {
        val marker = File(temp.root, "boot.marker")
        assertEquals(BootGuard.Decision.Proceed, guard(marker).evaluate(emptySet()))
        assertFalse(marker.exists())
    }

    /**
     * Fail closed. A crash detector that cannot write its marker cannot detect a crash, and
     * running risky hooks unprotected is worse than not running them. The cost of being
     * wrong here is a factory reset.
     */
    @Test
    fun `an unwritable marker disables rather than proceeds`() {
        val blocker = temp.newFile("not-a-directory")
        val decision = guard(File(blocker, "boot.marker")).evaluate(risky)

        assertTrue(decision is BootGuard.Decision.Disable)
        assertTrue((decision as BootGuard.Decision.Disable).reason.contains("not writable"))
        assertEquals(0, decision.consecutiveFailures)
    }

    /** A marker path that is neither readable nor writable still fails closed, not loudly. */
    @Test
    fun `a marker that cannot be read or written disables rather than throwing`() {
        val marker = temp.newFolder("marker-is-a-directory")
        assertTrue(guard(marker).evaluate(risky) is BootGuard.Decision.Disable)
    }
}
