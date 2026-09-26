package dev.lostxposed.core.config

/**
 * The Binder channel between the settings app and hooked processes.
 *
 * Every file-based channel has now been measured as unusable on this device: both framework
 * channels arrive empty, and a hooked app process reading the snapshot by path gets ENOENT
 * even with the whole directory path open and the file demonstrably present: the signature
 * of per-app mount namespaces rather than of permissions. Binder crosses both mount
 * namespaces and SELinux by design, which is why this exists.
 *
 * The provider is necessarily exported: the processes that need to read settings are
 * SystemUI, keyboards and ordinary apps, none of which share this module's signature, so no
 * signature-level permission can gate them. [visibleTo] is the compensation: a caller is
 * served only the settings that apply to it, so an unrelated app cannot enumerate, for
 * example, which words the user filters their notifications on.
 */
object ConfigContract {

    const val AUTHORITY = "io.github.uraniam9.lostxposed.config"
    const val URI = "content://$AUTHORITY"

    const val METHOD_READ = "read"

    /**
     * A hooked process telling the settings app that something went wrong badly enough to
     * matter after the fact. Today that means the boot guard tripped and turned features off.
     *
     * It travels the same way settings do, because it has the same problem: the reporter is
     * `system_server`, the reader is this app, and at the moment it has something to say the
     * app is probably not running. Accepted only from a platform uid, so an ordinary app
     * cannot invent a bootloop that never happened.
     */
    const val METHOD_REPORT_INCIDENT = "reportIncident"

    const val KEY_INCIDENT_REASON = "reason"
    const val KEY_INCIDENT_FEATURES = "features"
    const val KEY_INCIDENT_FAILURES = "failures"

    /** Below this, a uid is a platform uid rather than an installed app. */
    const val FIRST_APPLICATION_UID = 10000

    /**
     * Sent by the app after a change to a system_server feature, as an ordered broadcast. The
     * module there reads its settings again and answers with [RESULT_RELOADED], so the app can
     * say whether the change took or has to wait for a reboot.
     */
    const val ACTION_RELOAD = "dev.lostxposed.RELOAD_SETTINGS"
    const val RESULT_RELOADED = 1

    /**
     * Sent by the app to read Power inspector's tally directly, rather than through logcat.
     * Answered with [android.content.BroadcastReceiver.setResultData]: one line per uid,
     * tab separated as `uid\twakelocks\talarms`, so the app can resolve each uid to an app
     * name itself rather than system_server guessing at how to present one.
     */
    const val ACTION_POWER_REPORT = "dev.lostxposed.POWER_REPORT"

    /**
     * Narrow a full config to what [packages] is entitled to see: its own per-package
     * settings, plus the `*` defaults that would apply to it anyway, plus the schema version
     * so the reader can refuse a config it is too old to understand.
     *
     * [privileged] covers `system_server`, which hosts features acting across every package
     * and so legitimately needs the whole set.
     */
    fun visibleTo(
        all: Map<String, String>,
        packages: Set<String>,
        privileged: Boolean,
    ): Map<String, String> {
        if (privileged) return all
        return all.filterKeys { key ->
            key == ConfigSchema.KEY_VERSION || packageOf(key)?.let {
                it == ConfigSchema.ANY_PACKAGE || it in packages
            } == true
        }
    }

    /** The package segment of `feature|package|key`, or null if the key is not in that shape. */
    private fun packageOf(key: String): String? =
        key.split('|').takeIf { it.size == 3 }?.get(1)
}
