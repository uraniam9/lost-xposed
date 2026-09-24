package dev.lostxposed

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast
import dev.lostxposed.entry.xposed.Features
import dev.lostxposed.ui.Ui

/**
 * What this is, why it exists, what it can and cannot do yet, and how to say something about
 * it.
 *
 * The "what works" section is generated from [FeatureStatus] rather than written out, so it
 * cannot drift from the evidence the rest of the app reports.
 */
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar?.hide()

        val column = Ui.column(this).apply {
            addView(
                Ui.screenHeader(this@AboutActivity, "About", badgeText = Release.STAGE),
            )
            addView(tagline())

            addView(Ui.heading(this@AboutActivity, "Why this exists"))
            WHY.forEach { addView(Ui.prose(this@AboutActivity, it)) }

            addView(Ui.heading(this@AboutActivity, "How it behaves"))
            BEHAVIOUR.forEach { addView(Ui.prose(this@AboutActivity, it)) }

            addView(Ui.heading(this@AboutActivity, "What works right now"))
            addView(statusCard())

            addView(Ui.heading(this@AboutActivity, "Will it work on your phone"))
            COMPATIBILITY.forEach { addView(Ui.prose(this@AboutActivity, it)) }

            addView(Ui.heading(this@AboutActivity, "Where this is going"))
            ROADMAP.forEach { addView(Ui.prose(this@AboutActivity, it)) }

            addView(Ui.heading(this@AboutActivity, "Say something"))
            addView(
                Ui.linkRow(
                    this@AboutActivity,
                    "Report a bug",
                    "Opens with the diagnostics already filled in.",
                    trailing = "✉",
                ) { reportBug() },
            )
            addView(
                Ui.linkRow(
                    this@AboutActivity,
                    "Request a feature",
                    "An old module worth reviving, or something missing here.",
                    trailing = "✉",
                ) { requestFeature() },
            )
            Links.SOURCE?.let { url ->
                addView(
                    Ui.linkRow(this@AboutActivity, "Source code", url, trailing = "↗") {
                        open(url)
                    },
                )
            }
            Links.COFFEE?.let { url ->
                addView(
                    Ui.linkRow(
                        this@AboutActivity,
                        "Buy me a coffee",
                        Links.SUPPORT_NOTE,
                        trailing = "↗",
                        colour = Ui.accent(this@AboutActivity),
                    ) { open(url) },
                )
            }

            val work = Links.visibleWork()
            if (work.isNotEmpty()) {
                addView(Ui.heading(this@AboutActivity, "The other things I build"))
                work.forEach { entry ->
                    addView(
                        Ui.linkRow(
                            this@AboutActivity,
                            entry.name,
                            entry.tagline.ifBlank { null },
                            trailing = if (entry.url != null) "↗" else " ",
                        ) { entry.url?.let { open(it) } },
                    )
                }
            }

            addView(Ui.heading(this@AboutActivity, "Licence"))
            addView(Ui.prose(this@AboutActivity, LICENCE))

            addView(Ui.spacer(this@AboutActivity, 32))
        }

        setContentView(Ui.screen(this, column, padTop = true))
    }

    // ---------------------------------------------------------------- pieces

    private fun tagline() = Ui.heroCard(this, Ui.accent(this)).apply {
        addView(
            Ui.body(
                this@AboutActivity,
                "The clever Android hacks your phone keeps to itself.",
                Ui.accent(this@AboutActivity),
            ),
        )
        addView(Ui.spacer(this@AboutActivity, 6))
        addView(
            Ui.caption(
                this@AboutActivity,
                "${BuildConfig.VERSION_NAME} · ${Features.registry.registrations.size} features · " +
                    FeatureStatus.summary(),
            ),
        )
    }

    private fun statusCard() = Ui.card(this).apply {
        addView(
            Ui.caption(
                this@AboutActivity,
                "Verified on one device and one framework only:\n${FeatureStatus.provenance()}",
            ),
        )
        addView(Ui.divider(this@AboutActivity))

        // Grouped by label, not by state: two states share one label, and showing the same
        // badge twice with two lists under it would look like a rendering bug.
        FeatureStatus.State.entries.groupBy { it.label }.forEach { (_, states) ->
            val state = states.first()
            val named = Features.registry.registrations
                .filter { FeatureStatus.of(it.descriptor.id).state in states }
                .map { it.descriptor.name }
            if (named.isEmpty()) return@forEach

            addView(
                LinearLayout(this@AboutActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, Ui.dp(this@AboutActivity, 6), 0, Ui.dp(this@AboutActivity, 2))
                    addView(
                        Ui.badge(
                            this@AboutActivity,
                            state.badge ?: "working",
                            colourFor(state),
                        ),
                    )
                },
            )
            addView(Ui.caption(this@AboutActivity, named.joinToString(", ")))
        }

        addView(Ui.divider(this@AboutActivity))
        addView(Ui.caption(this@AboutActivity, NOT_BUILT))
    }

    private fun colourFor(state: FeatureStatus.State) = when (state) {
        FeatureStatus.State.VERIFIED -> Ui.ok(this)
        FeatureStatus.State.UNCONFIRMED -> Ui.warn(this)
        FeatureStatus.State.LOGIC_TESTED -> Ui.accent(this)
        FeatureStatus.State.UNTESTED -> Ui.muted(this)
    }

    // ---------------------------------------------------------------- actions

    private fun open(url: String) = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Nothing here can open that link", Toast.LENGTH_SHORT).show()
    }

    /**
     * Prefilled, not sent. The composer opens with the diagnostics in the body and the user
     * decides whether any of it leaves the device.
     */
    private fun reportBug() {
        Links.BUG?.let { return open(it) }
        compose(
            subject = "Lost Xposed bug — ${Diagnostics.header(this)}",
            body = buildString {
                appendLine("What happened:")
                appendLine()
                appendLine("What you expected:")
                appendLine()
                appendLine("Steps:")
                appendLine()
                appendLine("----- diagnostics, edit or delete anything you would rather not send -----")
                appendLine()
                append(Diagnostics.report(this@AboutActivity))
            },
        )
    }

    private fun requestFeature() {
        Links.FEATURE?.let { return open(it) }
        compose(
            subject = "Lost Xposed feature request",
            body = buildString {
                appendLine("What you want it to do:")
                appendLine()
                appendLine("The old module it is like, if there was one:")
                appendLine()
                appendLine("Why the built-in Android setting is not enough:")
                appendLine()
                appendLine("--")
                append(Diagnostics.header(this@AboutActivity))
            },
        )
    }

    private fun compose(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(Links.CONTACT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No mail app to open", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {

        val WHY = listOf(
            "Your phone can already do far more than its settings screen admits. The density " +
                "is fixed at one value for every app. The clock is a clock. Notifications " +
                "arrive or they do not, and \"or they do not\" is a per-app switch and " +
                "nothing finer. None of that is a technical limit — it is a decision " +
                "somebody made on your behalf.",
            "Lost Xposed reopens those decisions. A clock you compose yourself out of the " +
                "pieces you want, in the order you want them, at the size and weight you " +
                "choose. Density, font scale and refresh rate set per app rather than once " +
                "for everything. Notification rules that read the text and stop a message " +
                "before it is ever posted — something no notification listener can do, " +
                "because by the time it sees one it has already arrived.",
            "The bar for adding something is simple. It has to do what Android still will " +
                "not do for you, and it has to be worth running code inside your system " +
                "processes. Plenty of ideas fail that now because Android grew a setting for " +
                "them, and those are better left alone.",
        )

        val BEHAVIOUR = listOf(
            "A feature that could put your phone in a bootloop is disabled before the boot " +
                "that would prove it, not after. If the guard cannot even write its own " +
                "marker it disables the feature rather than run unprotected.",
            "When something does not work it names the step — the class is there, the " +
                "method is missing. Silence is the failure mode that costs days, so nothing " +
                "here is allowed to fail quietly. That is also why five of the eight features " +
                "say they still need testing instead of claiming otherwise.",
            "Exactly one network request exists in this app: a GET of a static file on " +
                "GitHub, to see whether a newer version is out. Only when you open the app, " +
                "cached for half a day, and switchable off. Nothing is sent with it — no " +
                "identifier, no version, no device details.",
            "Beyond that nothing leaves the phone. Settings go to hooked apps over a local " +
                "binder call, and each caller is identified by its uid and given only the " +
                "settings that apply to it, so an unrelated app cannot read which words you " +
                "filter notifications on. Nothing here intercepts anyone else's messages, " +
                "defeats payments or DRM, or collects anything about you.",
            "Every line was written for this project. Where an older module had the idea " +
                "first it is credited by name in NOTICE, with its licence — but nothing " +
                "is copied from one, including the ones whose licences would now allow it.",
        )

        val COMPATIBILITY = listOf(
            "Honestly: not necessarily, and not forever. Anything that reaches inside " +
                "Android is reaching into code that Google and your phone's maker are " +
                "free to change, and they do, every year.",
            "Per-app display is the safest thing here. It works inside the app's own " +
                "process on parts of Android that have barely moved since 2008, so it should " +
                "keep working almost anywhere. Notification rules and hardware keys sit in " +
                "system_server, which is more stable than it sounds but is where a bad " +
                "surprise costs the most. The status bar clock is the most fragile: SystemUI " +
                "is being rewritten in Compose, and when the clock stops being a TextView " +
                "this stops working until it is rebuilt around whatever replaced it.",
            "OEM skins are the other half. Samsung, Xiaomi and the rest rewrite large parts " +
                "of SystemUI, so a feature verified on one skin is a guess on another. " +
                "Rather than guess, each feature carries a table of the versions and makers " +
                "it has been checked against, and on anything unlisted it refuses to install " +
                "and says why in Diagnostics instead of hooking something that might not be " +
                "what it expects.",
            "So the answer is: it tells you. A feature that cannot work on your phone says " +
                "so, by name, rather than failing quietly — and the ones marked untested " +
                "have not been tried anywhere but a single Nothing phone.",
        )

        val ROADMAP = listOf(
            "Slowly, more of them. The survey behind this project lists 62 modules worth " +
                "remembering, and the intention is to keep bringing them back one at a time " +
                "— properly, with the compatibility work and the diagnostics, not as a " +
                "pile of hooks that breaks on the next Android release.",
            "That is a lot of evenings, and this is not a hobby anybody is funding. A coffee " +
                "is a real help and an honest signal about which features people actually " +
                "want.",
            "Supporters get the build before everyone else, a say in what gets built next, " +
                "and can ask me directly when something does not work. Feature requests from " +
                "supporters go to the top of the list — not because everyone else is " +
                "ignored, but because somebody paying for this to continue has earned a say " +
                "in where it goes.",
            "It is not a paywall and it could not be one. The source is public and the " +
                "licence keeps it that way, so nothing here is locked and nothing checks " +
                "whether you paid; every feature reaches everyone. Anyone could strip a lock " +
                "and rebuild, so pretending otherwise would be a lie with extra steps. What " +
                "you are buying is first, and the time to keep going.",
        )

        val NOT_BUILT =
            "Not built yet: clipboard history, AppOps and spoof profiles (needs a Shizuku " +
                "backend), and the motion stabiliser, which is still a standalone measurement " +
                "spike rather than a feature."

        val LICENCE =
            "Lost Xposed is free software under the GNU General Public License, version 3 or " +
                "later. You may read it, change it and share it; anything you build on it has " +
                "to stay free the same way. That is deliberate — this is code that hooks " +
                "system_server and reads notifications, and nobody should have to trust a " +
                "binary they cannot audit.\n\n" +
                "It builds on libxposed (Apache-2.0) and runs under LSPosed or Vector, which " +
                "are GPL-3.0 and are not linked into this app. Modules that inspired features " +
                "are credited in NOTICE with their own licences."
    }
}

/** Release stage, in one place so the badge and the version string cannot disagree. */
object Release {
    const val STAGE = "alpha"
}
