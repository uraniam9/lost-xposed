package dev.lostxposed

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
 * — that is a property of the architecture, not a gap — so anything it cannot verify is
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

            // Working first, then partly tested, then untested. Somebody opening this
            // should meet something that works before they meet a list of caveats.
            val (features, scaffolding) = Features.registry.registrations
                .map { it.descriptor }
                .partition { it.userFacing }

            addView(Ui.heading(this@MainActivity, "Features"))
            features
                .sortedBy { FeatureStatus.of(it.id).state.ordinal }
                .forEach { addView(featureCard(it)) }

            if (scaffolding.isNotEmpty()) {
                addView(Ui.heading(this@MainActivity, "Built-in checks"))
                addView(
                    Ui.caption(
                        this@MainActivity,
                        "Not features. These exist to prove the engine is working — that " +
                            "the module is loaded where it should be, and that a hook can be " +
                            "installed and removed again without a reboot. They change " +
                            "nothing on your phone.",
                    ),
                )
                addView(Ui.spacer(this@MainActivity, 8))
                scaffolding.forEach { addView(featureCard(it)) }
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
                    "Asked System UI to restart. The status bar will blink.",
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
                        "system_server can stop a boot, so those are the only ones disabled — " +
                        "turning off the rest would be theatre.",
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
                "Lost Xposed boot failure — ${Diagnostics.header(this@MainActivity)}",
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
     */
    private fun statusCard(active: Boolean): LinearLayout {
        val served = ConfigContentProvider.lastServed
        val colour = when {
            served != null -> Ui.ok(this)
            active -> Ui.warn(this)
            else -> Ui.danger(this)
        }

        return Ui.heroCard(this, colour).apply {
            addView(
                Ui.body(
                    this@MainActivity,
                    when {
                        served != null -> "Settings are reaching hooked apps"
                        active -> "Module loaded — nothing has read settings yet"
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
                        served != null ->
                            "Last read by ${served.packages.joinToString()} " +
                                "${ago(served.atMillis)} — ${served.keys} settings."

                        active ->
                            "The module is running here, so it is installed and scoped to " +
                                "itself. Nothing has asked for settings since this app " +
                                "started, which is normal until a hooked process restarts."

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
                    "scope installs nothing and reports nothing — it does not fail loudly. " +
                    "A tick means that process has actually read its settings, which is the " +
                    "only thing this app can know for certain; no tick is not proof of " +
                    "anything, since the process may not have started since you set it up.",
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
}
