package dev.lostxposed

import android.content.Context
import android.os.Build
import dev.lostxposed.core.compat.EnvironmentDetector
import dev.lostxposed.core.config.ConfigSchema
import dev.lostxposed.core.config.ConfigWriter
import dev.lostxposed.entry.xposed.Features

/**
 * A complete, shareable report of everything the app process can see.
 *
 * Separate from the screen that displays it because a bug report is worth far more with this
 * attached, and asking someone to visit another screen and copy it by hand is how you get bug
 * reports made of screenshots and recollection instead.
 *
 * What this process *cannot* see — the inside of hooked processes — is stated as such, with
 * the command that reveals it, rather than quietly omitted.
 */
object Diagnostics {

    /** Short enough for an email subject line or the top of an issue. */
    fun header(context: Context): String {
        val environment = EnvironmentDetector.detect(null)
        return "Lost Xposed ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · " +
            "Android ${environment.release} (SDK ${environment.sdkInt}) · ${environment.oem}"
    }

    fun report(context: Context): String {
        // Opening the writer is also what reveals which preference mode the framework allowed.
        val writer = ConfigWriter.open(context)
        val environment = EnvironmentDetector.detect(null)
        val active = ModuleStatus.isActive()

        return buildString {
            appendLine("LOST XPOSED DIAGNOSTICS")
            appendLine("version        ${BuildConfig.VERSION_NAME}")
            appendLine("device         ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android        ${environment.release} (SDK ${environment.sdkInt})")
            appendLine("oem            ${environment.oem}${environment.oemVersion?.let { " $it" } ?: ""}")
            appendLine("fingerprint    ${environment.fingerprint}")
            appendLine()

            appendLine("MODULE")
            appendLine("loaded here    ${if (active) "yes" else "NO"}")
            appendLine("schema         v${ConfigSchema.VERSION}")
            appendLine()

            // Every step of the write path, each reporting what actually happened rather
            // than that the call did not throw. A silent success at any one of these is how
            // "settings never arrive" stayed undiagnosed.
            appendLine("CONFIG TRANSPORT")
            appendLine("prefs mode     ${ConfigWriter.lastMode} (accepted, not necessarily applied)")
            appendLine("last write     ${ConfigWriter.lastWrite}")
            appendLine("file readable  ${ConfigWriter.lastPublished}")
            appendLine("data dir       ${ConfigWriter.lastDataDir}")
            appendLine("snapshot       ${ConfigWriter.lastSnapshot}")
            appendLine()
            appendLine("  Hooked processes try the settings provider first, then remote prefs,")
            appendLine("  then the framework's remote file, then the snapshot above read")
            appendLine("  straight off disk. Which one works is reported by the module in")
            appendLine("  logcat at process start, tagged LostXposed, as channel=...")
            if (!active) {
                appendLine()
                appendLine("  The module is not scoped to itself, so it cannot report its own")
                appendLine("  state here. Add \"Lost Xposed\" to its own scope in the framework")
                appendLine("  manager.")
            }
            appendLine()

            // The two things a bug report is usually missing: which processes are actually
            // reading settings, and whether the boot guard has already turned something off.
            val served = ServedPackages.read(context)
            appendLine("READ SETTINGS AT LEAST ONCE")
            if (served.isEmpty()) {
                appendLine("  none yet — no hooked process has asked for settings")
            } else {
                served.forEach { appendLine("  $it") }
            }
            appendLine()

            val incidents = BootIncidents.read(context)
            if (incidents.isNotEmpty()) {
                appendLine("BOOT GUARD")
                incidents.forEach { incident ->
                    incident.render().lineSequence().forEach { appendLine("  $it") }
                }
                appendLine()
            }

            appendLine("REQUIRED SCOPE")
            ScopeAdvice.required(context).forEach { entry ->
                entry.render().lineSequence().forEach { line -> appendLine("  $line") }
            }
            appendLine()

            appendLine("FEATURES (${Features.registry.registrations.size})")
            appendLine("  verification: ${FeatureStatus.summary()}")
            Features.registry.registrations.forEach { (d, _) ->
                val status = FeatureStatus.of(d.id)
                appendLine()
                appendLine("  ${d.name}  [${d.id}]  — ${status.state.label}")
                appendLine("    ${d.category} · ${d.stability} · risk ${d.riskTier}")
                appendLine("    targets ${d.injects.joinToString { it::class.simpleName ?: "?" }}")
                appendLine("    ${d.restartHint} after a change")

                val configured = writer.entriesFor(d.id)
                if (configured.isEmpty()) {
                    appendLine("    settings: none")
                } else {
                    configured.forEach { (pkg, values) -> appendLine("    $pkg -> $values") }
                }
            }
            appendLine()

            appendLine("HOOK-SIDE REPORT")
            appendLine("  adb logcat -d -s LostXposed")
            appendLine()
            appendLine("RESTARTING WITHOUT A REBOOT")
            appendLine("  SystemUI    adb shell am crash com.android.systemui")
            appendLine("              (force-stop silently does nothing to SystemUI)")
            appendLine("  an app      adb shell am force-stop <package>")
            appendLine("  keyboard    adb shell am force-stop <ime package>")
            appendLine("  system_server features load only at boot")
        }
    }
}
