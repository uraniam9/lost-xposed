package dev.lostxposed.core.config

import android.content.Context
import android.content.SharedPreferences
import dev.lostxposed.core.api.FeatureId
import java.io.File

/**
 * The UI-process half. Writes the same flat key space that [ConfigLoader] reads.
 *
 * Hooked processes read config at process start, so a write here takes effect the next time
 * the target app starts — which is why the UI must say so rather than implying it is live.
 */
class ConfigWriter(
    private val prefs: SharedPreferences,
    /** The backing file, so it can be made readable by the framework daemon. */
    private val file: File? = null,
    /** The module's files dir, which is what listRemoteFiles()/openRemoteFile() serve. */
    private val filesDir: File? = null,
    /** The app's root data dir, which is 0700 at install and blocks every path below it. */
    private val dataDir: File? = null,
) {

    init {
        if (prefs.getInt(ConfigSchema.KEY_VERSION, 0) != ConfigSchema.VERSION) {
            prefs.edit().putInt(ConfigSchema.KEY_VERSION, ConfigSchema.VERSION).commit()
        }
    }

    /**
     * Run on open as well as after every write, so settings stored by an earlier build still
     * reach hooked processes: the file may have been left 0660, and the snapshot may not exist
     * at all if nothing has been edited since the snapshot channel was added.
     */
    internal fun republish() {
        publish()
        snapshot()
    }

    /**
     * Mirror the whole config into the module's own files dir, in [Snapshot]'s format.
     *
     * This file feeds the two channels that are not shared_prefs: the framework serves it
     * through `listRemoteFiles()` / `openRemoteFile()`, and a hooked process permitted to read
     * it can open it by path with no framework involvement at all.
     *
     * Measured on Vector 2.2 with the module self-scoped and MODE_WORLD_READABLE accepted:
     * `getRemotePreferences()` returns an empty object in hooked processes, and writing this
     * file did **not** make `listRemoteFiles()` non-empty either. So it is written for the
     * direct read, and the two framework channels are still probed in case a build fixes them.
     */
    private fun snapshot() {
        val dir = filesDir ?: return
        runCatching {
            val out = File(dir, ConfigSchema.SNAPSHOT_NAME)
            out.writeText(Snapshot.format(prefs.all))
            out.setReadable(true, false)
            dir.setExecutable(true, false)
            lastSnapshot = "${out.length()} bytes at ${out.path}"
        }.onFailure { lastSnapshot = "${it.javaClass.simpleName}: ${it.message}" }
    }

    fun putInt(feature: FeatureId, packageName: String, key: String, value: Int) =
        edit { putInt(ConfigSchema.key(feature, packageName, key), value) }

    fun putFloat(feature: FeatureId, packageName: String, key: String, value: Float) =
        edit { putFloat(ConfigSchema.key(feature, packageName, key), value) }

    fun putBoolean(feature: FeatureId, packageName: String, key: String, value: Boolean) =
        edit { putBoolean(ConfigSchema.key(feature, packageName, key), value) }

    fun putString(feature: FeatureId, packageName: String, key: String, value: String) =
        edit { putString(ConfigSchema.key(feature, packageName, key), value) }

    fun remove(feature: FeatureId, packageName: String, key: String) =
        edit { remove(ConfigSchema.key(feature, packageName, key)) }

    /**
     * The whole config, flattened the same way the snapshot and the provider flatten it, so
     * every channel carries exactly the same bytes.
     */
    fun values(): Map<String, String> = runCatching {
        prefs.all.mapNotNull { (k, v) -> v?.let { k to it.toString() } }.toMap()
    }.getOrDefault(emptyMap())

    /** Current stored value, or null when nothing is set. For populating an editor. */
    fun read(feature: FeatureId, packageName: String, key: String): Any? =
        prefs.all[ConfigSchema.key(feature, packageName, key)]

    /** Every key this feature holds for this package. */
    fun removeAll(feature: FeatureId, packageName: String) {
        val prefix = ConfigSchema.prefix(feature, packageName)
        val doomed = prefs.all.keys.filter { it.startsWith(prefix) }
        edit { doomed.forEach { remove(it) } }
    }

    /** Everything configured for [feature], keyed by package. For the UI listing. */
    fun entriesFor(feature: FeatureId): Map<String, Map<String, Any?>> {
        val prefix = ConfigSchema.prefix(feature)
        return prefs.all
            .filterKeys { it.startsWith(prefix) }
            .entries
            .mapNotNull { (k, v) ->
                val parts = k.removePrefix(prefix).split("|", limit = 2)
                if (parts.size == 2) Triple(parts[0], parts[1], v) else null
            }
            .groupBy { it.first }
            .mapValues { (_, rows) -> rows.associate { it.second to it.third } }
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        // commit(), not apply(): the file must exist before it can be chmod-ed, and apply()
        // writes on a background thread.
        //
        // The result is checked and recorded. A silent commit() failure is exactly how this
        // went undiagnosed: the caller logged a successful write while the file on disk never
        // changed size or mtime.
        val committed = runCatching { prefs.edit().apply(block).commit() }
        lastWrite = committed.fold(
            onSuccess = { ok -> if (ok) "committed" else "commit() returned false at ${file?.path}" },
            onFailure = { "${it.javaClass.simpleName}: ${it.message}" },
        )
        publish()
        snapshot()
    }

    /**
     * Make the file readable by the framework daemon.
     *
     * Measured on Nothing OS / Android 16 with Vector 2.2: `vectord` runs as **system**,
     * while SharedPreferences writes the file 0660 owned by the app's own uid — so the
     * daemon serving `getRemotePreferences()` cannot open it, and every hooked process reads
     * an empty set with no error anywhere. MODE_WORLD_READABLE was accepted without throwing
     * and did not change the mode.
     *
     * Re-applied after every write because SharedPreferences replaces the file on commit,
     * which resets the permissions.
     */
    private fun publish() {
        // The root data dir is created 0700 by installd, and nothing below it can be reached
        // without it -- measured: with files/ at 0771 and the snapshot at 0644, an outside
        // process still could not traverse in. Re-applied on every open because a reinstall
        // restores 0700.
        runCatching {
            dataDir?.setExecutable(true, false)
            lastDataDir = dataDir?.let { if (it.canExecute()) "traversable" else "0700, not traversable" }
                ?: "unknown"
        }

        val target = file ?: return
        runCatching {
            target.setReadable(true, false)
            target.parentFile?.setExecutable(true, false)
            lastPublished = "${target.canRead()}"
        }
    }

    companion object {
        /**
         * Which mode the open CALL accepted — not proof the mode was applied.
         *
         * Measured on Nothing OS / Android 16: MODE_WORLD_READABLE was accepted without
         * throwing, yet the file on disk stayed 0660. So this value says only that the call
         * did not fail, and must never be presented as "settings will reach hooks". The
         * authoritative signal for that is whether the module is injected into this process
         * at all, which the self-check reports.
         */
        @Volatile
        var lastMode: String = "not opened"
            private set

        /** Whether the settings file is currently readable outside this app. */
        @Volatile
        var lastPublished: String = "not published"
            private set

        /**
         * Whether the app's root data dir can be traversed from outside.
         *
         * Opening it is necessary but nowhere near sufficient: SELinux labels app data with
         * per-app MLS categories, and measured on this device SystemUI still reads nothing
         * with the whole path open. See [ConfigLoader].
         */
        @Volatile
        var lastDataDir: String = "not opened"
            private set

        /** Outcome of the most recent write. Surfaced so a failure cannot stay silent. */
        @Volatile
        var lastWrite: String = "no write yet"
            private set

        /** Size of the flat snapshot served via openRemoteFile(). */
        @Volatile
        var lastSnapshot: String = "no snapshot"
            private set

        /**
         * MODE_PRIVATE, then chmod. Deliberately NOT MODE_WORLD_READABLE.
         *
         * Measured on Nothing OS / Android 16: `getSharedPreferences(.., MODE_WORLD_READABLE)`
         * neither threw nor produced a world-readable file — and worse, writes through the
         * returned instance were silently dropped. The receiver logged a successful write
         * while the file on disk never changed size or mtime.
         *
         * So the mode is useless here in both directions. Write through a normal private
         * instance, which persists correctly, and make the file readable afterwards via
         * [publish] so the framework daemon (running as `system`) can serve it.
         */
        @Suppress("DEPRECATION")
        fun open(context: Context): ConfigWriter {
            // MODE_WORLD_READABLE first: LSPosed-family frameworks intercept this call inside
            // a self-scoped module's process and register the file for remote access. It only
            // works when the module is injected here, which is why an earlier test of it —
            // run while the module was NOT self-scoped — dropped writes and looked broken.
            val prefs = runCatching {
                context.getSharedPreferences(ConfigSchema.PREFS_NAME, Context.MODE_WORLD_READABLE)
                    .also { lastMode = "MODE_WORLD_READABLE" }
            }.getOrElse {
                lastMode = "MODE_PRIVATE (${it.javaClass.simpleName})"
                context.getSharedPreferences(ConfigSchema.PREFS_NAME, Context.MODE_PRIVATE)
            }

            val file = runCatching {
                File(File(context.dataDir, "shared_prefs"), "${ConfigSchema.PREFS_NAME}.xml")
            }.getOrNull()

            return ConfigWriter(
                prefs = prefs,
                file = file,
                filesDir = runCatching { context.filesDir }.getOrNull(),
                dataDir = runCatching { context.dataDir }.getOrNull(),
            ).also { it.republish() }
        }
    }
}
