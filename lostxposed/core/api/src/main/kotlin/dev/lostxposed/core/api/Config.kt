package dev.lostxposed.core.api

/**
 * Read-only config as seen from inside a hooked process.
 *
 * Config is resolved once per process and treated as immutable for that process's lifetime.
 * Re-reading mid-process invites races inside system_server, so changes apply at the next
 * process start unless a feature explicitly opts into live reconfiguration.
 */
interface ConfigSource {
    fun contains(key: String): Boolean
    fun string(key: String, default: String? = null): String?
    fun int(key: String, default: Int): Int
    fun float(key: String, default: Float): Float
    fun boolean(key: String, default: Boolean): Boolean

    /** True when nothing is configured here at all, so a feature can cheaply skip. */
    fun isEmpty(): Boolean
}

/**
 * Resolves config for one feature in one package. Implementations layer a per-package value
 * over a per-feature default, so "120 Hz everywhere except Maps" is expressible.
 */
fun interface ConfigProvider {
    fun forFeature(feature: FeatureId, packageName: String): ConfigSource
}

object EmptyConfigSource : ConfigSource {
    override fun contains(key: String) = false
    override fun string(key: String, default: String?) = default
    override fun int(key: String, default: Int) = default
    override fun float(key: String, default: Float) = default
    override fun boolean(key: String, default: Boolean) = default
    override fun isEmpty() = true
}

val NoConfig = ConfigProvider { _, _ -> EmptyConfigSource }
