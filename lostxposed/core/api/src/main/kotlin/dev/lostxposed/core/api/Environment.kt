package dev.lostxposed.core.api

/**
 * Everything a compatibility decision is allowed to depend on. Detected once per process.
 *
 * Lives in core:api rather than core:compat because [HookEnv] exposes it; putting it in
 * compat would make api depend on compat, which already depends on api.
 */
data class Environment(
    val sdkInt: Int,
    val release: String,
    val oem: Oem,
    val oemVersion: String?,
    val fingerprint: String,
    val framework: FrameworkInfo,
) {
    override fun toString(): String =
        "$oem${oemVersion?.let { " $it" } ?: ""} / Android $release (SDK $sdkInt) / $framework"
}

enum class Oem { AOSP, PIXEL, ONE_UI, HYPER_OS, NOTHING, OTHER }

/**
 * Which framework actually loaded us, and what it granted. Populated from
 * `getFrameworkName/Version/VersionCode/Properties` and `getApiVersion`, all confirmed
 * present on Vector 2.2 / API 102.
 */
data class FrameworkInfo(
    val name: String,
    val version: String,
    val versionCode: Long,
    val apiVersion: Int,
    val capabilities: Long,
) {
    val canHookSystem: Boolean get() = capabilities and CAP_SYSTEM != 0L

    /** Remote preferences and remote files — the cross-process config channel. */
    val hasRemoteChannel: Boolean get() = capabilities and CAP_REMOTE != 0L

    val hasApiProtection: Boolean get() = capabilities and RT_API_PROTECTION != 0L

    fun supportsApi(required: Int): Boolean = apiVersion >= required

    fun capabilityNames(): List<String> = buildList {
        if (canHookSystem) add("SYSTEM")
        if (hasRemoteChannel) add("REMOTE")
        if (hasApiProtection) add("RT_API_PROTECTION")
    }

    override fun toString(): String = "$name $version (api $apiVersion)"

    companion object {
        // Mirrors XposedInterface.PROP_*, verified with javap against api:102.0.0.
        // Duplicated as plain constants so callers that do not compile against libxposed
        // can still read Environment.
        const val CAP_SYSTEM = 1L
        const val CAP_REMOTE = 2L
        const val RT_API_PROTECTION = 4L

        const val API_101 = 101
        const val API_102 = 102

        val UNKNOWN = FrameworkInfo("unknown", "?", 0, 0, 0)
    }
}
