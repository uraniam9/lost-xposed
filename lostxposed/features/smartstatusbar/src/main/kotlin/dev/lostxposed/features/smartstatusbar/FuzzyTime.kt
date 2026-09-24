package dev.lostxposed.features.smartstatusbar

import java.util.Calendar

/**
 * Time as people say it out loud: "five past three", "twenty to nine", "half past two".
 *
 * Rounded to the nearest five minutes on purpose. "Twenty-three minutes past four" humanises
 * nothing — the point is that an approximate time reads at a glance, and precision is what an
 * ordinary clock already gives you.
 *
 * [compact] exists because the status bar is not a sentence. "twenty-five to four" is
 * nineteen characters competing with every notification icon on the device, and the words
 * carrying that length are the numbers. Compact keeps "quarter" and "half", which are short
 * and read well, and uses digits for the rest: "25 to 4".
 */
object FuzzyTime {

    /** Indexed by slot 1..6. Slot 0 and 12 are handled as "o'clock". */
    private val MINUTES = arrayOf(
        "", "five", "ten", "quarter", "twenty", "twenty-five", "half",
    )

    private val MINUTES_COMPACT = arrayOf(
        "", "5", "10", "quarter", "20", "25", "half",
    )

    private val HOURS = arrayOf(
        "twelve", "one", "two", "three", "four", "five",
        "six", "seven", "eight", "nine", "ten", "eleven",
    )

    fun format(calendar: Calendar = Calendar.getInstance(), compact: Boolean = false): String {
        val minute = calendar.get(Calendar.MINUTE)
        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)

        // 0..12, each step five minutes, offset so we round to nearest rather than down.
        val slot = (minute + 2) / 5

        // Past the half hour the phrase counts down to the NEXT hour: "twenty to nine".
        // Slot 7 is the first that does, and slot 12 is the next hour exactly.
        val rollForward = slot >= 7
        val index = (if (rollForward) hour24 + 1 else hour24) % 12

        val hour = if (compact) (if (index == 0) "12" else index.toString()) else HOURS[index]
        val minutes = if (compact) MINUTES_COMPACT else MINUTES

        return when {
            slot == 0 || slot == 12 -> "$hour o'clock"
            rollForward -> "${minutes[12 - slot]} to $hour"
            else -> "${minutes[slot]} past $hour"
        }
    }
}
