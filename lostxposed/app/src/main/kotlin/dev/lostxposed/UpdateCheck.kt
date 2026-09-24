package dev.lostxposed

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Checks whether a newer build exists, on the same terms Lune Bridge uses.
 *
 * Only when the app is opened and never in the background. The answer is cached on the device
 * for twelve hours, a version you skip is not mentioned again, and it can be turned off for
 * good. Something that argues for staying out of your way should not be talking to the
 * internet while you are asleep.
 *
 * **Nothing is sent.** It is a GET of one static file; no identifier, no version, no device
 * details leave the phone. The request does not happen at all until the repository is public,
 * because until then there is nothing to ask and a network call with no possible answer is
 * just a network call.
 */
object UpdateCheck {

    private const val TAG = "LostXposed"
    private const val PREFS = "updates"
    private const val KEY_LAST_CHECK = "lastCheck"
    private const val KEY_SKIPPED = "skippedVersionCode"
    private const val KEY_ENABLED = "enabled"

    private const val CACHE_MS = 12 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 8_000

    /** Raw, not the API: no rate limit, no token, and it is a static file. */
    private const val MANIFEST = "https://github.com/uraniam9/lost-xposed/raw/main/update.json"

    data class Release(val version: String, val versionCode: Int, val url: String)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Fetches on a background thread and calls back on the main one, or not at all.
     *
     * Silent on every failure. No network, no DNS, a rewritten manifest, an unpublished
     * repository: none of that is the user's problem and none of it is worth an error dialog
     * about a check they did not ask for.
     */
    fun check(activity: Activity) {
        if (Links.SOURCE == null) return
        if (!isEnabled(activity)) return

        val prefs = prefs(activity)
        val since = System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0)
        if (since < CACHE_MS) return

        Thread {
            val release = fetch() ?: return@Thread
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            if (release.versionCode <= BuildConfig.VERSION_CODE) return@Thread
            if (release.versionCode == prefs.getInt(KEY_SKIPPED, -1)) return@Thread

            Handler(Looper.getMainLooper()).post {
                if (!activity.isFinishing && !activity.isDestroyed) offer(activity, release)
            }
        }.start()
    }

    private fun fetch(): Release? = runCatching {
        val connection = (URL(MANIFEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        val body = connection.use { it.inputStream.bufferedReader().readText() }
        val json = JSONObject(body)
        Release(
            version = json.getString("version"),
            versionCode = json.getInt("versionCode"),
            url = json.optString("releaseUrl").ifEmpty { json.getString("url") },
        )
    }.onFailure { Log.i(TAG, "update check skipped: ${it.javaClass.simpleName}") }.getOrNull()

    private fun offer(activity: Activity, release: Release) {
        AlertDialog.Builder(activity)
            .setTitle("${release.version} is out")
            .setMessage(
                "You are on ${BuildConfig.VERSION_NAME}.\n\n" +
                    "Updates are not automatic and never will be — this replaces a module " +
                    "that hooks your system processes, so it is worth reading what changed " +
                    "before you install it.",
            )
            .setPositiveButton("What changed") { _, _ ->
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
                }
            }
            .setNegativeButton("Skip this one") { _, _ ->
                prefs(activity).edit().putInt(KEY_SKIPPED, release.versionCode).apply()
            }
            .setNeutralButton("Later", null)
            .show()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
