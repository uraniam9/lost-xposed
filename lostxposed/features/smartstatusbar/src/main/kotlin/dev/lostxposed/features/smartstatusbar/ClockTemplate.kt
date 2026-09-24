package dev.lostxposed.features.smartstatusbar

/**
 * Builds the clock text from a line you write, mixing the available pieces:
 *
 *     {fuzzy} · {battery}      ->  half past three · 53%
 *     {day} {time}             ->  Tue 15:32
 *     {time12} {battery}       ->  3:32 53%
 *
 * The other styles each do one thing. This is the one that lets someone put the pieces in
 * their own order, with their own separators, and stop asking for a setting per combination.
 *
 * An unrecognised token is left on screen exactly as typed rather than silently dropped. A
 * status bar reading `{batery}` tells you what you got wrong; one that quietly shows the time
 * does not.
 */
object ClockTemplate {

    /** Every piece a template may refer to, in the order the editor should offer them. */
    val TOKENS = listOf("fuzzy", "time", "time12", "seconds", "day", "date", "battery")

    const val DEFAULT = "{fuzzy} · {battery}"

    /**
     * Both braces are escaped, and the closing one matters.
     *
     * `\{([A-Za-z0-9_]+)}` compiles on the JVM and throws PatternSyntaxException on Android,
     * whose regex engine is ICU rather than OpenJDK's. The unit tests run on the JVM, so they
     * passed and the app crashed on launch. Escape both.
     */
    private val PATTERN = Regex("""\{([A-Za-z0-9_]+)\}""")

    /**
     * [resolve] returns the text for a token name, or null to leave the token untouched.
     * Taking it as a function keeps this class free of clocks and batteries, which is what
     * makes it testable without a device.
     */
    fun expand(template: String, resolve: (String) -> String?): String =
        PATTERN.replace(template) { match -> resolve(match.groupValues[1]) ?: match.value }

    fun tokensIn(template: String): List<String> =
        PATTERN.findAll(template).map { it.groupValues[1] }.toList()

    /** For the editor to warn about, before someone waits for a SystemUI restart to find out. */
    fun unknownTokens(template: String): List<String> =
        tokensIn(template).filter { it !in TOKENS }.distinct()
}
