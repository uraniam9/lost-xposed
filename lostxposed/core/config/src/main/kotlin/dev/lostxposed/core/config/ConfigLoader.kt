package dev.lostxposed.core.config

import android.os.ParcelFileDescriptor
import dev.lostxposed.core.api.FrameworkInfo
import io.github.libxposed.api.XposedInterface
import java.io.File

sealed interface ConfigLoad {
    data class Ready(
        val provider: StoreConfigProvider,
        val schemaVersion: Int,
        /** Which transport actually delivered the settings. */
        val channel: String,
    ) : ConfigLoad

    data class Unavailable(val detail: String) : ConfigLoad
}

/**
 * Finds a transport that can carry settings from the UI process into this hooked process.
 *
 * Four exist, and which of them works is a property of the platform and framework build
 * rather than of this module, so all four are probed and the first one holding anything wins:
 *
 *  1. **provider**: a binder call to the settings app's ContentProvider.
 *  2. **remote prefs**: `getRemotePreferences()`, the documented channel.
 *  3. **remote file**: `openRemoteFile()`, the documented file channel, served by the
 *     framework daemon out of the module's own files dir.
 *  4. **direct file**: the same file opened by path, with no framework involvement at all.
 *
 * Measured on Nothing OS / Android 16 with Vector 2.2, module self-scoped and
 * `PROP_CAP_REMOTE` granted:
 *
 * - Channels 2 and 3 arrive empty in every hooked process, while the settings app reads its
 *   own settings back correctly, so the write side works and the framework read side does
 *   not. Channel 3 reports `Cannot open remote file`, meaning the daemon declines to serve a
 *   file that exists rather than failing to find one.
 * - Channel 4 gets **ENOENT** in SystemUI with the entire directory path opened and the file
 *   demonstrably present. ENOENT rather than EACCES points at per-app mount namespaces, not
 *   permissions, so no file mode or SELinux label can rescue it in an app process. It is kept
 *   because `system_server` is not in an isolated namespace and may still be able to read it.
 *
 * Channel 1 exists because binder crosses both mount namespaces and SELinux by design.
 *
 * Every channel's outcome is recorded in [report] whether or not it won. A channel that
 * silently returns nothing is precisely how this stayed invisible for days.
 */
object ConfigLoader {

    /** What each channel contained at load, and which one was used. For diagnosing delivery. */
    @Volatile
    var report: String = "not loaded"
        private set

    fun load(xposed: XposedInterface): ConfigLoad {
        val hasRemote = xposed.frameworkProperties and FrameworkInfo.CAP_REMOTE != 0L

        val probes = listOf(
            "provider" to {
                ProviderChannel.read() ?: ProviderChannel.lastFailure?.let { error(it) }
            },
            "remote-prefs" to {
                if (!hasRemote) null else xposed.getRemotePreferences(ConfigSchema.PREFS_NAME)?.asConfigStore()
            },
            "remote-file" to {
                if (!hasRemote) null else readFrom { xposed.openRemoteFile(ConfigSchema.SNAPSHOT_NAME) }
            },
            "direct-file" to {
                // Opened rather than probed with exists(). A denied stat and a missing file
                // both make exists() return false, and those are completely different
                // problems: one needs a policy that will never come, the other needs a write.
                // The exception message tells them apart.
                MapConfigStore(Snapshot.parse(File(SNAPSHOT_PATH).readText()))
            },
        )

        // Every channel is probed even once one has won, because "which of these is broken on
        // this device" is the question this module keeps having to answer, and the probes are
        // a Binder call and a small file read.
        val outcomes = mutableListOf<String>()
        var chosen: Pair<String, ConfigStore>? = null

        probes.forEach { (name, open) ->
            val outcome = runCatching { open() }
            val store = outcome.getOrNull()

            outcomes += when {
                outcome.isFailure -> "$name=${outcome.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }}"
                store == null -> "$name=absent"
                else -> "$name=${store.size}"
            }

            if (chosen == null && store != null && store.size > 0) chosen = name to store
        }

        report = "channel=${chosen?.first ?: "none"} ${outcomes.joinToString(" ")}"

        if (chosen == null) {
            return ConfigLoad.Unavailable("no channel delivered any settings: ${outcomes.joinToString(" ")}")
        }

        val (name, store) = chosen
        val stored = store.raw(ConfigSchema.KEY_VERSION)?.toIntOrNull() ?: 0
        if (stored > ConfigSchema.VERSION) {
            // Refuse rather than guess. Old code reading a newer schema is how a config
            // change turns into a bootloop.
            return ConfigLoad.Unavailable(
                "schema v$stored is newer than this build understands (v${ConfigSchema.VERSION})",
            )
        }

        return ConfigLoad.Ready(StoreConfigProvider(store), stored, name)
    }

    private inline fun readFrom(open: () -> ParcelFileDescriptor?): ConfigStore? {
        val pfd = open() ?: return null
        val text = ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }
        return MapConfigStore(Snapshot.parse(text))
    }

    /**
     * User 0 only. A work-profile clone of the module would have its own data dir, but the
     * framework loads one module instance and the settings UI lives in the primary user, so
     * that is the file every process should be looking at.
     *
     * Device-protected storage, so the file is there before the first unlock, which is when
     * system_server and SystemUI start.
     */
    private const val SNAPSHOT_PATH =
        "/data/user_de/0/io.github.uraniam9.lostxposed/files/${ConfigSchema.SNAPSHOT_NAME}"
}
