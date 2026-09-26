package dev.lostxposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.lostxposed.core.config.ConfigContract

/**
 * Hands a settings change to system_server without a reboot.
 *
 * The module there starts listening once its first settings are in, which is a minute or so
 * into the boot, and answers when it has taken the new ones. No answer means nothing was
 * listening: the phone has only just started, System framework is not in scope, or the module
 * in system_server predates this and needs one reboot to catch up. In every one of those cases
 * the change still applies at the next boot, because that is when settings are read anyway.
 */
object SystemSettings {

    private const val TAG = "LostXposed"

    /** system_server registers its receivers under the platform's own package name. */
    private const val SYSTEM_PACKAGE = "android"

    fun push(context: Context, done: (taken: Boolean) -> Unit) {
        val intent = Intent(ConfigContract.ACTION_RELOAD)
            .setPackage(SYSTEM_PACKAGE)
            // The foreground queue: the answer is what the toast is waiting on.
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val answer = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val taken = resultCode == ConfigContract.RESULT_RELOADED
                Log.i(TAG, "system_server " + if (taken) "took the new settings" else "did not answer")
                done(taken)
            }
        }
        // No receiver permission here, for the same reason as RestartControl: the guard is on
        // the receiving side, which only accepts senders signed with this app's key.
        runCatching {
            context.sendOrderedBroadcast(
                intent, null, answer, Handler(Looper.getMainLooper()), 0, null, null,
            )
        }.onFailure {
            Log.w(TAG, "settings push failed: ${it.javaClass.simpleName}: ${it.message}")
            done(false)
        }
    }
}
