package dev.lostxposed

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import dev.lostxposed.ui.Ui
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
        if (Links.SOURCE == null) return note("not checking: repository not published")
        if (!isEnabled(activity)) return note("not checking: turned off")

        val prefs = prefs(activity)
        val since = System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0)
        if (since < CACHE_MS) return note("not checking: last check was ${since / 60_000} min ago")

        note("checking $MANIFEST")
        Thread {
            val release = fetch() ?: return@Thread
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            if (release.versionCode <= BuildConfig.VERSION_CODE) {
                return@Thread note(
                    "up to date: newest is ${release.versionCode}, " +
                        "this is ${BuildConfig.VERSION_CODE}",
                )
            }
            if (release.versionCode == prefs.getInt(KEY_SKIPPED, -1)) {
                return@Thread note("${release.version} is out but was skipped")
            }

            Handler(Looper.getMainLooper()).post {
                if (activity.isFinishing || activity.isDestroyed) {
                    note("${release.version} is out, but the screen closed before it could say so")
                } else {
                    note("offering ${release.version}")
                    offer(activity, release)
                }
            }
        }.start()
    }

    // Every way out of check() says which one it took. For a while only a failed fetch said
    // anything, so "nothing happened" looked the same whether it had worked, been switched
    // off, been waiting out the cooldown or never reached the network at all.
    private fun note(message: String) {
        Log.i(TAG, "update check: $message")
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
    }.onFailure { note("fetch failed: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()

    /**
     * Built from the app's own pieces. A platform AlertDialog takes its colours from the system
     * theme, which on Material You follows the wallpaper, so it matches nothing else in the app,
     * and it fits three buttons into one row.
     *
     * Three choices, stacked, most useful first:
     *  - View release opens the release page, which is both the changelog and the download.
     *  - Remind me later only closes this. The next check, half a day on, asks again.
     *  - Skip this version stays quiet about this one for good, but still offers the next.
     *
     * A leading "v", the tag's spelling, is dropped so the two version lines read alike.
     */
    private fun offer(activity: Activity, release: Release) {
        val newest = release.version.removePrefix("v")
        val dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }

        val card = Ui.column(activity, pad = 24).apply {
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(activity, 24).toFloat()
                setColor(Ui.surface(activity))
            }
            addView(
                Ui.title(activity, "$newest is out").apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                },
            )
            addView(Ui.spacer(activity, 6))
            addView(Ui.body(activity, "You have ${BuildConfig.VERSION_NAME}."))
            addView(Ui.spacer(activity, 10))
            addView(
                Ui.caption(
                    activity,
                    "Nothing updates on its own. This replaces a module that hooks your system " +
                        "processes, so it's worth reading what changed before you install it.",
                ).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) },
            )
            addView(Ui.spacer(activity, 22))
            addView(
                Ui.primaryButton(activity, "View release") {
                    dialog.dismiss()
                    runCatching {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
                    }
                },
            )
            addView(Ui.spacer(activity, 6))
            addView(Ui.textButton(activity, "Remind me later", Ui.accent(activity)) { dialog.dismiss() })
            addView(
                Ui.textButton(activity, "Skip this version", Ui.muted(activity)) {
                    prefs(activity).edit().putInt(KEY_SKIPPED, release.versionCode).apply()
                    dialog.dismiss()
                },
            )
        }

        dialog.setContentView(
            FrameLayout(activity).apply {
                val side = Ui.dp(activity, 20)
                setPadding(side, 0, side, 0)
                addView(card)
            },
        )
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
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
