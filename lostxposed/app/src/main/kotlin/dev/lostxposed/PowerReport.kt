package dev.lostxposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.lostxposed.core.config.ConfigContract

/**
 * Asks system_server for Power inspector's tally, straight into the app instead of by adb.
 *
 * Nothing is cached here. The whole point of the feature is what is happening right now, and
 * a count from the last time this screen was open is worse than admitting there is none yet.
 */
object PowerReport {

    private const val TAG = "LostXposed"

    /** system_server registers its receivers under the platform's own package name. */
    private const val SYSTEM_PACKAGE = "android"

    /** @param done the raw tab separated report, or null if nothing answered. */
    fun fetch(context: Context, done: (report: String?) -> Unit) {
        val intent = Intent(ConfigContract.ACTION_POWER_REPORT)
            .setPackage(SYSTEM_PACKAGE)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val answer = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = done(resultData)
        }
        // No receiver permission here, for the same reason as SystemSettings: the guard is on
        // the receiving side, which only accepts senders signed with this app's key.
        runCatching {
            context.sendOrderedBroadcast(
                intent, null, answer, Handler(Looper.getMainLooper()), 0, null, null,
            )
        }.onFailure {
            Log.w(TAG, "power report request failed: ${it.javaClass.simpleName}: ${it.message}")
            done(null)
        }
    }
}
