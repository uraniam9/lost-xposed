package dev.lostxposed.core.compat

import android.os.Build
import dev.lostxposed.core.api.Environment
import dev.lostxposed.core.api.FrameworkInfo
import dev.lostxposed.core.api.Oem
import io.github.libxposed.api.XposedInterface

object EnvironmentDetector {

    fun detect(xposed: XposedInterface?): Environment {
        val (oem, oemVersion) = detectOem()
        return Environment(
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE,
            oem = oem,
            oemVersion = oemVersion,
            fingerprint = Build.FINGERPRINT,
            framework = xposed?.let(::frameworkInfo) ?: FrameworkInfo.UNKNOWN,
        )
    }

    private fun frameworkInfo(x: XposedInterface) = runCatching {
        FrameworkInfo(
            name = x.frameworkName,
            version = x.frameworkVersion,
            versionCode = x.frameworkVersionCode,
            apiVersion = x.apiVersion,
            capabilities = x.frameworkProperties,
        )
    }.getOrDefault(FrameworkInfo.UNKNOWN)

    /**
     * OEM skin, not just manufacturer, because behaviour follows the skin. Checked most specific
     * first because several of these ship on hardware from more than one brand.
     */
    private fun detectOem(): Pair<Oem, String?> {
        prop("ro.build.version.oneui")?.let { return Oem.ONE_UI to formatOneUi(it) }
        prop("ro.mi.os.version.name")?.let { return Oem.HYPER_OS to it }
        prop("ro.miui.ui.version.name")?.let { return Oem.HYPER_OS to it }

        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer == "nothing" -> Oem.NOTHING to prop("ro.build.nothing.version")
            manufacturer == "google" -> Oem.PIXEL to null
            manufacturer == "samsung" -> Oem.ONE_UI to null
            manufacturer == "xiaomi" -> Oem.HYPER_OS to null
            isAosp() -> Oem.AOSP to null
            else -> Oem.OTHER to null
        }
    }

    /** One UI reports e.g. 90000 for 9.0. */
    private fun formatOneUi(raw: String): String =
        raw.toIntOrNull()?.let { "%d.%d".format(it / 10000, (it % 10000) / 100) } ?: raw

    private fun isAosp(): Boolean =
        Build.FINGERPRINT.contains("aosp", true) || Build.FINGERPRINT.contains("generic", true)

    private fun prop(key: String): String? = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
