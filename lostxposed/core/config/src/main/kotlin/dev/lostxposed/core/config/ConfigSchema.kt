package dev.lostxposed.core.config

import dev.lostxposed.core.api.FeatureId

/**
 * One flat key space shared by the UI process (which writes) and every hooked process
 * (which reads), because SharedPreferences is flat.
 *
 *     core.displayprofiles|com.example.app|densityDpi = 400
 *     core.displayprofiles|*|refreshRate            = 120
 *
 * A per-package entry wins over the `*` default, so "120 Hz everywhere except Maps" is
 * expressible without enumerating every app.
 */
object ConfigSchema {

    /**
     * Bump when the meaning of existing keys changes, not when keys are added.
     *
     * A hooked process running older code must REFUSE a newer schema rather than
     * misinterpret it. In system_server a misread is a bootloop, not a glitch.
     */
    const val VERSION = 1

    const val PREFS_NAME = "config"
    const val KEY_VERSION = "schema.version"

    /**
     * Flat mirror of the whole key space, served through `openRemoteFile()` and readable
     * directly off disk. One `key=value` line each, in [Snapshot]'s own format rather than
     * `java.util.Properties`; see that class for why.
     */
    const val SNAPSHOT_NAME = "config.properties"
    const val ANY_PACKAGE = "*"

    private const val SEP = "|"

    fun key(feature: FeatureId, packageName: String, key: String): String =
        "${prefix(feature)}$packageName$SEP$key"

    fun defaultKey(feature: FeatureId, key: String): String =
        key(feature, ANY_PACKAGE, key)

    /** Everything belonging to one feature. */
    fun prefix(feature: FeatureId): String = "${feature.value}$SEP"

    /** Everything one feature holds for one package. */
    fun prefix(feature: FeatureId, packageName: String): String =
        "${prefix(feature)}$packageName$SEP"
}
