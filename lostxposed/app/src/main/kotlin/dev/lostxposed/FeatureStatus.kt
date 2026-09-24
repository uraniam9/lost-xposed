package dev.lostxposed

import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.entry.xposed.Features

/**
 * What has actually been proven to work, as distinct from what has been written.
 *
 * `Stability` on the descriptor is a design claim: how settled the author thinks a feature's
 * approach is. This is an evidence claim: what anybody has actually seen. They are different
 * questions and conflating them is how an alpha ends up describing itself as ready.
 *
 * **Every entry here is a factual claim and must be demoted the moment it stops being true.**
 */
object FeatureStatus {

    /**
     * Four rungs, because "untested" was doing too much work.
     *
     * It covered both "the hook installs but nobody has watched it do its job" and "nobody
     * has looked at this at all", which are very different things to hand somebody who is
     * deciding whether to switch it on. It also read as an admission that something was
     * shipped carelessly, when what it meant was that a claim had not been earned yet.
     */
    enum class State(val label: String) {
        /**
         * Seen working end to end on a device: hook installed, settings delivered,
         * effect visible. Carries no badge — see [badge].
         */
        VERIFIED("verified"),

        /** The hook installs on a device. What it does after that has not been observed. */
        UNCONFIRMED("needs testing"),

        /**
         * Its logic is covered by tests, but it has never run on a phone.
         *
         * Wears the same badge as [UNCONFIRMED] on purpose. The difference between "installs
         * but unobserved" and "tested but never run" matters when deciding what to work on
         * next; to somebody deciding whether to switch a feature on it is the same sentence,
         * and two badges for one answer is two things to learn instead of one. The detail
         * under each feature still says which it is.
         */
        LOGIC_TESTED("needs testing"),

        /** Neither. Nobody has looked. */
        UNTESTED("untested"),
        ;

        /**
         * The badge text, or null when a feature does not need one.
         *
         * A badge is for an exception. Labelling the things that work as "verified"
         * made the list read like an audit report and drew the eye to the wrong rows:
         * what a person needs to spot is the feature that might not do anything yet,
         * not the three that do.
         */
        val badge: String? get() = if (this == VERIFIED) null else label
    }

    /**
     * [short] is for the list, [detail] for the feature's own screen.
     *
     * They are separate because the list had been showing the long one. Three coloured
     * paragraphs of test commentary stacked down a features list is the project talking to
     * itself: somebody scrolling wants to know what a feature does and whether it is ready,
     * not how many unit tests cover it. Worse, two of them said almost the same sentence,
     * and repeated text down a screen is the single clearest sign nobody read it back.
     */
    data class Entry(val state: State, val short: String, val detail: String)

    private const val VERIFIED_ON =
        "Nothing Phone (AIN065) · Nothing OS 4 · Android 16 (SDK 36) · " +
            "Vector 2.2 (libxposed API 102)"

    /** The single device and framework any of this has been seen on. */
    fun provenance(): String = VERIFIED_ON

    private val BY_ID: Map<String, Entry> = mapOf(
        "core.selfcheck" to Entry(
            State.VERIFIED,
            "",
            "Reports whether the module is loaded in this process. Confirmed reporting correctly.",
        ),
        "core.noop" to Entry(
            State.VERIFIED,
            "",
            "The reference hook. Confirmed installing in SystemUI and detaching cleanly while " +
                "the process kept running — no reboot needed to turn a feature off.",
        ),
        "core.smartstatusbar" to Entry(
            State.VERIFIED,
            "",
            "Confirmed end to end: the clock renders in the status bar from settings written " +
                "in this app, including colour, weight and font. 27 tests cover the phrasing, " +
                "the mixer and the appearance parsing.",
        ),
        "core.displayprofiles" to Entry(
            State.LOGIC_TESTED,
            "Never run on a phone. Needs an app added to this module's scope.",
            "7 tests cover how a profile is read and, most importantly, that an unset or " +
                "malformed value leaves the system alone rather than applying a zero. The hook " +
                "itself has never been seen applying a profile — that needs an ordinary app " +
                "added to this module's scope.",
        ),
        "core.textengine" to Entry(
            State.LOGIC_TESTED,
            "Never run on a phone. Needs your keyboard added to this module's scope.",
            "9 tests cover the gesture maths and which keyboards it applies to. They found a " +
                "real bug: an emptied keyboard list fell through to hooking nothing at all, " +
                "silently, with every setting still looking correct. Never run on a device.",
        ),
        "core.notificationrules" to Entry(
            State.UNCONFIRMED,
            "Installs at boot. Not yet seen blocking anything.",
            "Confirmed installing in system_server at a clean boot, and 10 tests cover the " +
                "keyword matching. Whether settings reach it there is unconfirmed, so rules " +
                "may not apply yet.",
        ),
        "core.hardwarekeys" to Entry(
            State.UNCONFIRMED,
            "Installs at boot. Not yet seen remapping anything.",
            "Confirmed installing in system_server at a clean boot. Whether settings reach it " +
                "there is unconfirmed, so remappings may not apply yet.",
        ),
        "core.powerinspector" to Entry(
            State.UNCONFIRMED,
            "Installs at boot. Its counts have not been checked against anything.",
            "Confirmed installing in system_server at a clean boot. Its counts have not been " +
                "checked against another source.",
        ),
    )

    private val UNKNOWN = Entry(
        State.UNTESTED,
        "Not tested.",
        "No verification recorded for this feature.",
    )

    fun of(id: FeatureId): Entry = BY_ID[id.value] ?: UNKNOWN

    fun countsByState(): Map<State, Int> =
        Features.registry.registrations
            .groupingBy { of(it.descriptor.id).state }
            .eachCount()

    /**
     * One line for the top of the app: "3 working · 5 need testing".
     *
     * Grouped by the label rather than the state, because two states share a label and
     * "3 needs testing · 2 needs testing" is not a sentence.
     */
    fun summary(): String {
        val counts = countsByState()
        return State.entries
            .groupBy { it.label }
            .mapNotNull { (label, states) ->
                val total = states.sumOf { counts[it] ?: 0 }
                if (total == 0) null else "$total ${if (label == "verified") "working" else label}"
            }
            .joinToString(" · ")
    }
}
