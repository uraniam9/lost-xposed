package dev.lostxposed

/**
 * Every outward-facing address in one place.
 *
 * Any entry left null hides its row rather than showing a dead link. That is deliberate, and
 * it is the same rule Lune Bridge follows: a donate button that 404s is worse than no donate
 * button, and an About screen that offers "Source code" and then does nothing is worse than
 * one that never offered it.
 */
object Links {

    /**
     * The repository is public, so the links below resolve and are shown.
     *
     * It stays a switch rather than becoming a constant because a build cut before a repo
     * exists should hide its own dead links, and that will be true again for the next thing
     * this pattern gets copied into.
     */
    private const val PUBLISHED = true

    private const val REPO = "https://github.com/uraniam9/lost-xposed"

    val SOURCE: String? = REPO.takeIf { PUBLISHED }

    /** The person, not this one repo. Not gated on [PUBLISHED]: the profile exists either way. */
    const val GITHUB = "https://github.com/uraniam9"

    /**
     * Straight at the templates rather than at the issue list, for the reason Lune Bridge
     * gives: a form that already asks for the device and the diagnostics gets those answers,
     * where an empty box gets "it doesn't work".
     */
    val BUG: String? = "$REPO/issues/new?template=bug_report.yml".takeIf { PUBLISHED }
    val FEATURE: String? = "$REPO/issues/new?template=feature_request.yml".takeIf { PUBLISHED }

    /**
     * One support link, not two. A second button pointing at the same intention makes people
     * choose instead of act, and Buy Me a Coffee already carries recurring support behind
     * "Make this monthly".
     */
    val COFFEE: String? = "https://buymeacoffee.com/uraniam9"

    /** Fallback for reports while there is no issue tracker to send them to. */
    const val CONTACT_EMAIL = "soundsoftlab@gmail.com"

    /** No promise about what this costs later. Only what a coffee is for now. */
    /**
     * Seen right next to the money ask, so it has to do two jobs at once: say what somebody
     * actually gets, and make "not a paywall" provable in the same breath.
     *
     * Three things, because one was not an offer. "Supporters see it first" is a fact about
     * scheduling; getting builds early, getting a say in what is built next, and being able
     * to ask the person who wrote it is a reason.
     */
    val SUPPORT_NOTE =
        "Early builds, a say in what gets built next, and help straight from me. The source " +
            "stays public. You are paying for first, not for access."

    /** The two things on the credit line, each to its own page. */
    const val SONOLUNE = "https://play.google.com/store/apps/details?id=com.soundsoftlab.sonolune"
    const val LUNE_BRIDGE = "https://github.com/uraniam9/lune-bridge"

    data class Work(val name: String, val tagline: String, val url: String?)

    /**
     * The other things. Taglines are the author's own words, taken from those projects rather
     * than written here.
     *
     * SonoLune carries its publisher name because that is how it is listed on the store, and
     * attributing it loosely would be worse than attributing it precisely.
     */
    val OTHER_WORK: List<Work> = listOf(
        Work(
            name = "Lune Bridge",
            tagline = "Warmer, darker, calmer screens. Candlelight down to 1700K, dimming " +
                "below the panel minimum, and a flicker-safe mode for PWM eye strain. A root " +
                "module, no Xposed needed.",
            url = "https://github.com/uraniam9/lune-bridge",
        ),
        Work(
            // The heading above this list already says these are mine. Repeating the
            // publisher name here made it read like somebody else's app had wandered in.
            name = "SonoLune",
            tagline = "A calm-first sleep and focus app. Turns the phone into a calm " +
                "environment rather than another thing demanding something from you. Works " +
                "offline. No ads, no tracking, no streaks, no data collection.",
            // The store listing, not the site: somebody tapping this on a phone wants
            // to install it, and the site would only send them here anyway.
            url = "https://play.google.com/store/apps/details?id=com.soundsoftlab.sonolune",
        ),
    )

    /** Shown only when there is something real to show. */
    fun visibleWork(): List<Work> = OTHER_WORK.filter { it.tagline.isNotBlank() || it.url != null }
}
