package dev.lostxposed.features.smartstatusbar

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTimeTest {

    private fun at(hour: Int, minute: Int): String = FuzzyTime.format(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        },
    )

    @Test
    fun `on the hour`() {
        assertEquals("three o'clock", at(3, 0))
        assertEquals("three o'clock", at(15, 0))
    }

    @Test
    fun `rounds to the nearest five minutes, not down`() {
        assertEquals("three o'clock", at(3, 2))
        assertEquals("five past three", at(3, 3))
        assertEquals("five past three", at(3, 5))
        assertEquals("five past three", at(3, 7))
        assertEquals("ten past three", at(3, 8))
    }

    @Test
    fun `counts up to the half hour`() {
        assertEquals("quarter past three", at(3, 15))
        assertEquals("twenty past three", at(3, 20))
        assertEquals("twenty-five past three", at(3, 25))
        assertEquals("half past three", at(3, 30))
    }

    /** Past the half hour the phrase counts down to the *next* hour. */
    @Test
    fun `counts down to the next hour`() {
        assertEquals("twenty-five to four", at(3, 35))
        assertEquals("twenty to four", at(3, 40))
        assertEquals("quarter to four", at(3, 45))
        assertEquals("ten to four", at(3, 50))
        assertEquals("five to four", at(3, 55))
    }

    @Test
    fun `rounds up into the next hour`() {
        assertEquals("four o'clock", at(3, 58))
        assertEquals("four o'clock", at(3, 59))
    }

    @Test
    fun `twelve hour names, with noon and midnight both twelve`() {
        assertEquals("twelve o'clock", at(0, 0))
        assertEquals("twelve o'clock", at(12, 0))
        assertEquals("ten past twelve", at(0, 10))
        assertEquals("twenty past one", at(13, 20))
        assertEquals("quarter to nine", at(20, 45))
    }

    /** The hour rolls over the end of the day rather than indexing past the array. */
    @Test
    fun `wraps from eleven at night to twelve`() {
        assertEquals("five to twelve", at(23, 55))
        assertEquals("twelve o'clock", at(23, 59))
    }

    private fun compactAt(hour: Int, minute: Int): String = FuzzyTime.format(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        },
        compact = true,
    )

    /**
     * Compact keeps the words that are already short and read well, and uses digits for the
     * ones that make the phrase long. The status bar is competing with notification icons.
     */
    @Test
    fun `compact uses digits for the numbers and keeps quarter and half`() {
        assertEquals("3 o'clock", compactAt(3, 0))
        assertEquals("5 past 3", compactAt(3, 5))
        assertEquals("quarter past 3", compactAt(3, 15))
        assertEquals("20 past 3", compactAt(3, 20))
        assertEquals("half past 3", compactAt(3, 30))
        assertEquals("25 to 4", compactAt(3, 35))
        assertEquals("quarter to 4", compactAt(3, 45))
    }

    @Test
    fun `compact is never longer than the spelled out form`() {
        for (hour in 0..23) {
            for (minute in 0..59) {
                val long = at(hour, minute)
                val short = compactAt(hour, minute)
                assertTrue(
                    "$hour:$minute -> '$short' is longer than '$long'",
                    short.length <= long.length,
                )
            }
        }
    }

    @Test
    fun `compact wraps midnight and noon to twelve`() {
        assertEquals("12 o'clock", compactAt(0, 0))
        assertEquals("12 o'clock", compactAt(12, 0))
        assertEquals("12 o'clock", compactAt(23, 59))
    }

    /**
     * The phrase is built from two arrays indexed by arithmetic on the minute, so every
     * minute of every hour has to be walked: an off-by-one shows up as a crash in
     * SystemUI, which is not a place to discover one.
     */
    @Test
    fun `every minute of the day produces a phrase`() {
        for (hour in 0..23) {
            for (minute in 0..59) {
                val phrase = at(hour, minute)
                assertTrue("$hour:$minute produced '$phrase'", phrase.isNotBlank())
                assertTrue("$hour:$minute produced '$phrase'", phrase.none { it.isDigit() })
            }
        }
    }
}
