package dev.lostxposed.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTest {

    @Test
    fun `round trips ordinary values`() {
        val values = mapOf(
            "core.smartstatusbar|*|style" to "fuzzy",
            "core.smartstatusbar|*|showBattery" to true,
            "core.displayprofiles|com.example.app|densityDpi" to 400,
            "core.textengine|*|swipeFactor" to 1.05f,
        )
        assertEquals(
            mapOf(
                "core.smartstatusbar|*|style" to "fuzzy",
                "core.smartstatusbar|*|showBattery" to "true",
                "core.displayprofiles|com.example.app|densityDpi" to "400",
                "core.textengine|*|swipeFactor" to "1.05",
            ),
            Snapshot.parse(Snapshot.format(values)),
        )
    }

    /**
     * The case that makes hand-rolled formats dangerous: a notification keyword is free text
     * the user typed, and an `=` in it must not split the line in the wrong place.
     */
    @Test
    fun `round trips a value containing the separator`() {
        val values = mapOf("core.notificationrules|com.x|keywords" to "total=0,a=b=c")
        val parsed = Snapshot.parse(Snapshot.format(values))
        assertEquals("total=0,a=b=c", parsed["core.notificationrules|com.x|keywords"])
    }

    @Test
    fun `round trips newlines and backslashes`() {
        val values = mapOf("k" to "line one\nline two\r\\end\\")
        assertEquals("line one\nline two\r\\end\\", Snapshot.parse(Snapshot.format(values))["k"])
    }

    @Test
    fun `a value containing a newline stays on one line`() {
        assertEquals(1, Snapshot.format(mapOf("k" to "a\nb")).trim().lines().size)
    }

    /** Byte-stable output is what makes "did this change on disk?" answerable by looking. */
    @Test
    fun `output is sorted and stable regardless of input order`() {
        val a = Snapshot.format(mapOf("b" to 1, "a" to 2, "c" to 3))
        val b = Snapshot.format(mapOf("c" to 3, "b" to 1, "a" to 2))
        assertEquals(a, b)
        assertEquals("a=2\nb=1\nc=3\n", a)
    }

    @Test
    fun `null values are dropped rather than written as the string null`() {
        val text = Snapshot.format(mapOf("a" to "kept", "b" to null))
        assertEquals("a=kept\n", text)
        assertNull(Snapshot.parse(text)["b"])
    }

    @Test
    fun `parse ignores blank lines comments and lines with no separator`() {
        val parsed = Snapshot.parse(
            """
            # a comment
            
            a=1
            junk-with-no-separator
            b=2
            """.trimIndent(),
        )
        assertEquals(mapOf("a" to "1", "b" to "2"), parsed)
    }

    @Test
    fun `parse keeps an empty value`() {
        assertEquals("", Snapshot.parse("a=\n")["a"])
    }

    @Test
    fun `parse of empty input yields nothing`() {
        assertTrue(Snapshot.parse("").isEmpty())
    }
}
