package dev.lostxposed

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.ProcessTarget
import dev.lostxposed.core.compat.EnvironmentDetector
import dev.lostxposed.core.config.ConfigWriter
import dev.lostxposed.entry.xposed.Features
import dev.lostxposed.ui.Ui

/**
 * The dashboard.
 *
 * It reports only what this process can actually see. It cannot look inside hooked processes
 * (a property of the architecture, not a gap), so anything it cannot verify is
 * stated as unknown rather than assumed good. The one exception is the settings provider: a
 * hooked process reading through it leaves a record here, and that record is real evidence.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This screen draws its own header, complete with the release badge. The platform
        // action bar would print the app label above it, saying the same thing twice.
        actionBar?.hide()
        render()
    }

    /**
     * Where the list was when this screen was last left.
     *
     * [render] rebuilds the whole view tree, which is what keeps the status and scope
     * sections honest when something changes behind the app's back. Rebuilding also resets
     * the scroll to the top, which throws the reader back to the beginning every time they
     * come back from a feature. Restoring it is the cost of rebuilding.
     */
    private var scrollY = 0
    private var scroller: ScrollView? = null

    override fun onResume() {
        super.onResume()
        render()
        // Only here: opened, never in the background. See UpdateCheck for the rest of it.
        UpdateCheck.check(this)
    }

    override fun onPause() {
        super.onPause()
        scrollY = scroller?.scrollY ?: scrollY
    }

    private fun render() {
        // Opening the writer is what republishes the settings file and snapshot, so it must
        // happen before anything reports on them.
        val writer = ConfigWriter.open(this)
        val active = ModuleStatus.isActive()
        val environment = EnvironmentDetector.detect(null)

        val column = Ui.column(this).apply {
            addView(
                Ui.screenHeader(
                    this@MainActivity,
                    "Lost Xposed",
                    badgeText = Release.STAGE,
                    showBack = false,
                ),
            )
            addView(
                Ui.body(
                    this@MainActivity,
                    "The clever Android hacks your phone keeps to itself.",
                    Ui.accent(this@MainActivity),
                ),
            )
            addView(Ui.spacer(this@MainActivity, 4))
            addView(
                // "From the maker of" rather than "By the maker of": it is the phrase this
                // sentence actually has in English, and the one people have read a thousand
                // times on a store listing. Lune Bridge comes first because anyone reading
                // this already runs root modules, and that is the one they may know.
                Ui.linkedCaption(
                    this@MainActivity,
                    "From the maker of Lune Bridge and SonoLune",
                    mapOf(
                        "Lune Bridge" to { openUrl(Links.LUNE_BRIDGE) },
                        "SonoLune" to { openUrl(Links.SONOLUNE) },
                    ),
                ),
            )
            addView(
                Ui.caption(
                    this@MainActivity,
                    "${BuildConfig.VERSION_NAME} · " +
                        "${Features.registry.registrations.size} features · " +
                        FeatureStatus.summary(),
                ),
            )

            // Above everything, because it is the one thing that happened while nobody
            // was looking and it explains a feature that stopped working by itself.
            BootIncidents.read(this@MainActivity).lastOrNull()?.let { addView(incidentCard(it)) }

            addView(Ui.heading(this@MainActivity, "Status"))
            addView(statusCard(active))

            addView(Ui.heading(this@MainActivity, "Required scope"))
            addView(scopeCard())

            val (features, scaffolding) = Features.registry.registrations
                .map { it.descriptor }
                .partition { it.userFacing }

            addView(Ui.heading(this@MainActivity, "Features"))
            features
                .sortedBy { d -> CARD_ORDER.indexOf(d.id.value).let { if (it < 0) CARD_ORDER.size else it } }
                .forEach { addView(featureCard(it)) }

            // Collapsed: these are proof the engine works, not something to act on, and
            // sitting open on the main screen gave equal weight to a self-test and a feature.
            if (scaffolding.isNotEmpty()) {
                addView(
                    Ui.expandable(this@MainActivity, "Built-in checks") {
                        addView(
                            Ui.caption(
                                this@MainActivity,
                                "Not features. These exist to prove the engine is working: " +
                                    "that the module is loaded where it should be, and that a " +
                                    "hook can be installed and removed again without a " +
                                    "reboot. They change nothing on your phone.",
                            ),
                        )
                        addView(Ui.spacer(this@MainActivity, 8))
                        scaffolding.forEach { addView(featureCard(it)) }
                    },
                )
            }

            addView(Ui.heading(this@MainActivity, "More"))
            addView(
                Ui.linkRow(
                    this@MainActivity,
                    "About Lost Xposed",
                    "What it is, what works, how to report a bug",
                ) { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) },
            )
            addView(
                Ui.linkRow(
                    this@MainActivity,
                    "Diagnostics",
                    "A full report you can share",
                ) { startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java)) },
            )

            addView(Ui.spacer(this@MainActivity, 12))
            addView(
                Ui.caption(
                    this@MainActivity,
                    "${Build.MANUFACTURER} ${Build.MODEL} · Android ${environment.release} " +
                        "(SDK ${environment.sdkInt}) · " +
                        // The framework identifies itself to hooked processes, not to this
                        // one, so there is nothing to report here rather than "unknown".
                        if (environment.framework.apiVersion > 0) {
                            "${environment.framework}"
                        } else {
                            "framework version visible only inside hooked processes"
                        },
                ),
            )

            // The true footer, below even the device line. Its own card rather than loose
            // text in the page's own padding, so it carries the same weight as every other
            // section here instead of reading like an afterthought nobody finished styling.
            addView(Ui.spacer(this@MainActivity, 12))
            addView(Ui.heading(this@MainActivity, "Author"))
            addView(authorCard())

            addView(Ui.spacer(this@MainActivity, 32))
        }

        // Only offered when something is configured for SystemUI, because a restart with
        // nothing to apply is a flicker for no reason.
        val awaitingSystemUi = Features.registry.registrations
            .map { it.descriptor }
            .filter { d -> d.injects.any { it is ProcessTarget.SystemUi } }
            .any { writer.entriesFor(it.id).isNotEmpty() }

        // One scroll container either way: `column` can only have one parent, and building
        // both variants would have given it two.
        val root = if (awaitingSystemUi) {
            Ui.screenWithAction(this, column, "Restart System UI") {
                RestartControl.restart(this, RestartControl.SYSTEM_UI)
                Toast.makeText(
                    this,
                    "Asked System UI to restart. The screen may flash a few times before it settles.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        } else {
            Ui.screen(this, column, padTop = true)
        }

        scroller = root as? ScrollView ?: root.getChildAt(0) as? ScrollView
        setContentView(root)
        scroller?.let { view -> view.post { view.scrollTo(0, scrollY) } }
    }

    /**
     * What the boot guard did, and a way to send it on.
     *
     * The guard turning features off is the system working as designed, not a failure of it,
     * but from the outside it looks like a feature quietly breaking. Saying which features,
     * why, and how many boots failed turns that into something reportable.
     */
    private fun incidentCard(incident: BootIncidents.Incident) =
        Ui.heroCard(this, Ui.danger(this)).apply {
            addView(
                Ui.body(
                    this@MainActivity,
                    "A boot failed, so features were turned off",
                    Ui.danger(this@MainActivity),
                ),
            )
            addView(Ui.spacer(this@MainActivity, 6))
            addView(Ui.caption(this@MainActivity, incident.render()))
            addView(Ui.spacer(this@MainActivity, 6))
            addView(
                Ui.caption(
                    this@MainActivity,
                    "They will stay off until you clear this. Only features that run inside " +
                        "system_server can stop a boot, so those are the only ones disabled. " +
                        "Turning off the rest would be theatre.",
                ),
            )
            addView(
                Ui.button(this@MainActivity, "Send this to the developer") {
                    sendIncident(incident)
                },
            )
            addView(
                Ui.button(this@MainActivity, "Clear and re-enable") {
                    BootIncidents.clear(this@MainActivity)
                    Toast.makeText(
                        this@MainActivity,
                        "Cleared. The features come back at the next boot.",
                        Toast.LENGTH_LONG,
                    ).show()
                    render()
                },
            )
        }

    private fun sendIncident(incident: BootIncidents.Incident) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_SENDTO,
            android.net.Uri.parse("mailto:"),
        ).apply {
            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(Links.CONTACT_EMAIL))
            putExtra(
                android.content.Intent.EXTRA_SUBJECT,
                "Lost Xposed boot failure: ${Diagnostics.header(this@MainActivity)}",
            )
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                buildString {
                    appendLine("The boot guard tripped.")
                    appendLine()
                    appendLine(incident.render())
                    appendLine()
                    appendLine("Anything you were doing before the reboot:")
                    appendLine()
                    appendLine("----- diagnostics, delete anything you would rather not send -----")
                    appendLine()
                    append(Diagnostics.report(this@MainActivity))
                },
            )
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, "No mail app to open", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Two separate facts, in the order of how much they matter.
     *
     * Whether the module is loaded *here* only says it is scoped to itself. Whether a hooked
     * process has actually read its settings is what people care about, and until one has,
     * this says so rather than implying otherwise.
     *
     * The evidence is [ServedPackages], counted for this boot only. It is on disk, so it
     * outlives Android killing this app in the background, which happens all the time and
     * says nothing about SystemUI. It does not outlive a reboot. SystemUI can come back from
     * one without its settings, and a read carried over from the boot before turned this card
     * green above a plain clock. [ConfigContentProvider.lastServed] adds the exact time and
     * count for as long as this app's process lasts.
     *
     * This app reading its own settings is left out. It is not one of the hooked apps the
     * headline is talking about.
     */
    private fun statusCard(active: Boolean): LinearLayout {
        val own = packageName
        val last = ConfigContentProvider.lastServed
            ?.let { it.copy(packages = it.packages - own) }
            ?.takeIf { it.packages.isNotEmpty() }
        val thisBoot = ServedPackages.thisBoot(this) - own
        val reaching = last != null || thisBoot.isNotEmpty()
        val lapsed = if (reaching) emptySet() else ServedPackages.onlyBeforeThisBoot(this) - own
        val colour = when {
            reaching -> Ui.ok(this)
            active -> Ui.warn(this)
            else -> Ui.danger(this)
        }

        return Ui.heroCard(this, colour).apply {
            addView(
                Ui.body(
                    this@MainActivity,
                    when {
                        reaching -> "Settings are reaching hooked apps"
                        active && lapsed.isNotEmpty() ->
                            "Nothing has read settings since the reboot"
                        active -> "Module loaded, but nothing has read settings yet"
                        else -> "Module is not loaded in this app"
                    },
                    colour,
                ),
            )
            addView(Ui.spacer(this@MainActivity, 6))
            addView(
                Ui.caption(
                    this@MainActivity,
                    when {
                        last != null ->
                            "Last read by ${last.packages.joinToString()} " +
                                "${ago(last.atMillis)} (${last.keys} settings)."

                        reaching ->
                            "Read by ${thisBoot.joinToString()} since the phone started. " +
                                "Android has restarted this app since then, which it does on " +
                                "its own, so the exact time and count are gone."

                        active && RestartControl.SYSTEM_UI in lapsed ->
                            "Read by ${lapsed.joinToString()} before the phone restarted, but " +
                                "not since. The Restart System UI button makes it ask again."

                        active && lapsed.isNotEmpty() ->
                            "Read by ${lapsed.joinToString()} before the phone restarted. Each " +
                                "one asks again the next time it starts."

                        active ->
                            "The module is running here, so it is installed and scoped to " +
                                "itself. No hooked app has asked for settings yet, which is " +
                                "normal until one restarts."

                        else ->
                            "Add \"Lost Xposed\" to this module's own scope in the framework " +
                                "manager, then reopen this app. Until then it cannot report " +
                                "its own state."
                    },
                ),
            )
        }
    }

    /**
     * What to tick in the framework manager, worked out from what the features declare.
     *
     * Here rather than buried in diagnostics because a feature whose process is out of scope
     * installs nothing and says nothing, which looks exactly like a feature that is broken.
     * Several confusing silences during development were precisely that.
     */
    private fun scopeCard() = Ui.card(this).apply {
        addView(
            Ui.caption(
                this@MainActivity,
                "Tick these in your framework manager. A feature whose process is not in " +
                    "scope installs nothing and reports nothing. It does not fail loudly. " +
                    "A tick means that process has read its settings since the phone last " +
                    "started, which is the only thing this app can know for certain. A missing " +
                    "tick proves nothing on its own: the process may not have started since " +
                    "you set it up.",
            ),
        )
        ScopeAdvice.required(this@MainActivity).forEachIndexed { index, entry ->
            if (index == 0) {
                addView(Ui.spacer(this@MainActivity, 10))
            } else {
                addView(Ui.divider(this@MainActivity))
            }
            addView(
                Ui.body(
                    this@MainActivity,
                    (if (entry.confirmed) "✓  " else "") +
                        (entry.packageName?.let { "${entry.label}  ·  $it" } ?: entry.label),
                    if (entry.confirmed) Ui.ok(this@MainActivity) else null,
                ),
            )
            addView(Ui.caption(this@MainActivity, entry.detail))
            addView(Ui.caption(this@MainActivity, "needed by ${entry.features.joinToString(", ")}"))
        }
    }

    /**
     * The credit line. A plain card, not [Ui.heroCard]: that colour is reserved for the one
     * thing on screen that matters most, which [statusCard] already uses it for, and a second
     * accent-bordered card here would read as a second status rather than a signature.
     */
    private fun authorCard(): LinearLayout = Ui.card(this).apply {
        addView(
            Ui.body(this@MainActivity, "🌙 uraniam9", Ui.accent(this@MainActivity)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                isClickable = true
                setOnClickListener { openUrl(Links.GITHUB) }
            },
        )
        addView(Ui.spacer(this@MainActivity, 6))
        addView(
            Ui.linkedCaption(
                this@MainActivity,
                "Also behind Lune Bridge and SonoLune",
                mapOf(
                    "Lune Bridge" to { openUrl(Links.LUNE_BRIDGE) },
                    "SonoLune" to { openUrl(Links.SONOLUNE) },
                ),
            ),
        )
    }

    private fun featureCard(descriptor: FeatureDescriptor) = Ui.card(this) {
        startActivity(
            Intent(this, FeatureActivity::class.java)
                .putExtra(FeatureActivity.EXTRA_FEATURE, descriptor.id.value),
        )
    }.apply {
        val status = FeatureStatus.of(descriptor.id)
        val colour = colourFor(status.state)

        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    Ui.body(this@MainActivity, descriptor.name).apply {
                        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    },
                )
                status.state.badge?.let {
                    addView(Ui.badge(this@MainActivity, it, colour))
                }
            },
        )
        addView(Ui.spacer(this@MainActivity, 6))
        addView(Ui.caption(this@MainActivity, descriptor.description))
        // One short line, in the ordinary caption colour. The badge beside the name already
        // carries the signal; colouring the sentence as well makes every unfinished feature
        // shout, and there are five of them.
        if (status.short.isNotEmpty()) {
            addView(Ui.spacer(this@MainActivity, 6))
            addView(Ui.caption(this@MainActivity, status.short))
        }
        addView(Ui.spacer(this@MainActivity, 6))
        addView(
            Ui.caption(
                this@MainActivity,
                "${descriptor.category} · ${descriptor.stability} · ${descriptor.restartHint}" +
                    if (descriptor.settings.isEmpty()) " · no settings" else "",
            ),
        )
    }

    private fun colourFor(state: FeatureStatus.State) = when (state) {
        FeatureStatus.State.VERIFIED -> Ui.ok(this)
        FeatureStatus.State.UNCONFIRMED -> Ui.warn(this)
        FeatureStatus.State.LOGIC_TESTED -> Ui.accent(this)
        FeatureStatus.State.UNTESTED -> Ui.muted(this)
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                ),
            )
        }.onFailure {
            Toast.makeText(this, "Nothing here can open that link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ago(millis: Long): String {
        val seconds = (System.currentTimeMillis() - millis) / 1000
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60} min ago"
            else -> "${seconds / 3600} h ago"
        }
    }

    private companion object {
        /**
         * Deliberately not sorted by verification state. That put the newly verified features
         * at the top every time one crossed the line, which reshuffled the list out from under
         * anyone who had learned where a feature sat. This order is picked once: the status
         * bar clock first, since it is what most people came for, then the two just-verified
         * system_server features, then the two still asking for their first real test, then
         * the read-only counter last, since it is a tool for the other six rather than a
         * feature on its own.
         */
        val CARD_ORDER = listOf(
            "core.smartstatusbar",
            "core.hardwarekeys",
            "core.notificationrules",
            "core.displayprofiles",
            "core.textengine",
            "core.powerinspector",
        )
    }
}
