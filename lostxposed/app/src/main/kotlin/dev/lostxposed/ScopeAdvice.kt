package dev.lostxposed

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dev.lostxposed.core.api.ProcessTarget
import dev.lostxposed.entry.xposed.Features

/**
 * Which packages have to be ticked in the framework manager, derived from what the features
 * actually declare rather than from a list someone maintains by hand.
 *
 * The manifest's `xposedscope` array can only ever be a first-install hint: two of the five
 * process targets cannot be written down in advance. `CurrentIme` depends on which keyboard
 * this device is using, and `App` is whichever apps the user wants the feature for. Both are
 * resolved here, at runtime, where the answer exists.
 *
 * This is not cosmetic. A feature whose process is out of scope installs nothing and reports
 * nothing, which is indistinguishable from a feature that is broken — and several confusing
 * silences during development were exactly that.
 */
object ScopeAdvice {

    data class Entry(
        /** The package to tick, or null when it depends on a choice only the user can make. */
        val packageName: String?,
        val label: String,
        val detail: String,
        val features: List<String>,
        /**
         * True when this package has actually read its settings through the provider.
         *
         * Not "is in scope" — nothing can ask the framework that. This is the stronger
         * fact: the module is loaded there and the settings channel works.
         */
        val confirmed: Boolean = false,
    ) {
        fun render(): String = buildString {
            if (confirmed) append("[reading settings] ")
            append(label)
            packageName?.let { append("  [").append(it).append("]") }
            appendLine()
            append("    ").append(detail)
            appendLine()
            append("    needed by: ").append(features.joinToString(", "))
        }
    }

    fun required(context: Context): List<Entry> {
        val byTarget = mutableMapOf<String, MutableList<String>>()
        val order = mutableListOf<String>()

        Features.registry.registrations.forEach { (descriptor, _) ->
            descriptor.injects.forEach { target ->
                val key = keyOf(target)
                if (key !in byTarget) order += key
                byTarget.getOrPut(key) { mutableListOf() } += descriptor.name
            }
        }

        val served = ServedPackages.read(context)
        return order.map { key ->
            val entry = entryFor(key, context, byTarget.getValue(key).distinct())
            entry.copy(confirmed = entry.packageName != null && entry.packageName in served)
        }
    }

    private fun keyOf(target: ProcessTarget): String = when (target) {
        ProcessTarget.Self -> SELF
        ProcessTarget.SystemUi -> SYSTEM_UI
        ProcessTarget.SystemServer -> SYSTEM_SERVER
        ProcessTarget.CurrentIme -> IME
        is ProcessTarget.App -> APPS
    }

    private fun entryFor(key: String, context: Context, features: List<String>): Entry = when (key) {
        SELF -> Entry(
            packageName = SELF,
            label = "Lost Xposed",
            detail = "This app, so it can report its own status truthfully instead of guessing.",
            features = features,
        )

        SYSTEM_UI -> Entry(
            packageName = SYSTEM_UI,
            label = "System UI",
            detail = "Takes effect on the next SystemUI restart — no reboot needed.",
            features = features,
        )

        SYSTEM_SERVER -> Entry(
            packageName = SYSTEM_SERVER,
            label = "System framework",
            detail = "Loads at boot only, so these features need a reboot before they run at all.",
            features = features,
        )

        IME -> {
            val ime = currentIme(context)
            Entry(
                packageName = ime,
                label = ime?.let { "Your keyboard — ${labelOf(context, it)}" } ?: "Your keyboard",
                detail = "Takes effect when the keyboard process restarts.",
                features = features,
            )
        }

        else -> Entry(
            packageName = null,
            label = "The apps you want this for",
            detail = "Any app, your choice. Each takes effect the next time that app starts.",
            features = features,
        )
    }

    /** `Settings.Secure.DEFAULT_INPUT_METHOD` is `package/.ServiceClass`. */
    private fun currentIme(context: Context): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun labelOf(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private const val SELF = "io.github.uraniam9.lostxposed"
    private const val SYSTEM_UI = "com.android.systemui"

    /** system_server's package name, which is what a framework manager lists it under. */
    private const val SYSTEM_SERVER = "android"
    private const val IME = "#ime"
    private const val APPS = "#apps"
}
