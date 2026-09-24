package dev.lostxposed.core.config

import android.content.SharedPreferences
import dev.lostxposed.core.api.ConfigProvider
import dev.lostxposed.core.api.ConfigSource
import dev.lostxposed.core.api.FeatureId

/**
 * A flat key → string view of the config, whatever channel it arrived through.
 *
 * Every transport is normalised to this one shape — remote preferences, a file served by the
 * framework, a file read straight off disk — so the resolution rules (per-package over
 * per-feature default, type coercion, empty check) exist once and are the same regardless of
 * which channel happened to work on a given device.
 */
interface ConfigStore {
    fun keys(): Set<String>
    fun raw(key: String): String?
    val size: Int get() = keys().size
}

class MapConfigStore(private val values: Map<String, String>) : ConfigStore {
    override fun keys(): Set<String> = values.keys
    override fun raw(key: String): String? = values[key]
    override val size: Int get() = values.size
    override fun toString(): String = "${values.size} keys"
}

/**
 * Read once and cached, because [ConfigSource] is documented as immutable for the life of the
 * process — and because `SharedPreferences.all` copies the whole map on every call, which over
 * a Binder to another process is not something to do per lookup.
 */
fun SharedPreferences.asConfigStore(): ConfigStore = MapConfigStore(
    runCatching {
        all.mapNotNull { (k, v) -> v?.let { k to it.toString() } }.toMap()
    }.getOrDefault(emptyMap()),
)

class StoreConfigProvider(private val store: ConfigStore) : ConfigProvider {
    override fun forFeature(feature: FeatureId, packageName: String): ConfigSource =
        StoreConfigSource(store, feature, packageName)
}

/**
 * Values arrive as strings because the file channels have no type information, so every
 * accessor parses. A value that will not parse falls back to the caller's default rather than
 * throwing: a malformed setting should make one feature behave as if unconfigured, not take
 * down the process it was read in.
 */
class StoreConfigSource(
    private val store: ConfigStore,
    private val feature: FeatureId,
    private val packageName: String,
) : ConfigSource {

    /** Per-package first, then the `*` default. */
    private fun resolve(key: String): String? =
        store.raw(ConfigSchema.key(feature, packageName, key))
            ?: store.raw(ConfigSchema.defaultKey(feature, key))

    override fun contains(key: String): Boolean = resolve(key) != null

    override fun string(key: String, default: String?): String? = resolve(key) ?: default

    override fun int(key: String, default: Int): Int = resolve(key)?.toIntOrNull() ?: default

    override fun float(key: String, default: Float): Float = resolve(key)?.toFloatOrNull() ?: default

    override fun boolean(key: String, default: Boolean): Boolean =
        when (resolve(key)?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }

    /** Cheap skip for the common case: a feature enabled globally but configured for nothing. */
    override fun isEmpty(): Boolean =
        store.keys().none { it.startsWith(ConfigSchema.prefix(feature)) }
}
