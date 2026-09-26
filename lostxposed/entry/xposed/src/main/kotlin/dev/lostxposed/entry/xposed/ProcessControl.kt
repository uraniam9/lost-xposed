package dev.lostxposed.entry.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import dev.lostxposed.core.engine.LOG_TAG

/**
 * Lets the settings app restart a process it has just reconfigured.
 *
 * Settings are read once, when a process starts, so changing the clock means restarting
 * SystemUI. Doing that by hand means `adb shell am crash com.android.systemui`, which is not
 * something to ask of somebody who just wants a different clock. The module is already inside
 * that process, so it can stand up and end itself; the system brings SystemUI straight back.
 *
 * `force-stop` on SystemUI is a no-op, measured: the system refuses it. Killing our own pid
 * from inside is the thing that actually works.
 *
 * **Guarded by a signature-level permission.** The receiver has to be exported, because the
 * sender is a different app, and an exported receiver that kills SystemUI on request would
 * otherwise be a nuisance any installed app could trigger. The permission is declared by the
 * settings app, so only something signed with the same key can send this.
 */
internal object ProcessControl {

    const val ACTION_RESTART = "dev.lostxposed.RESTART_PROCESS"
    const val PERMISSION = "io.github.uraniam9.lostxposed.permission.CONTROL"

    /** The Application does not exist when a package is handed over, so wait for it. */
    private const val CONTEXT_DELAY_MS = 6_000L

    /** Long enough for the broadcast to finish and the caller to see its toast. */
    private const val KILL_DELAY_MS = 600L

    fun install(packageName: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            val context = currentApplication() ?: return@postDelayed
            runCatching {
                context.registerReceiver(
                    receiver(packageName),
                    IntentFilter(ACTION_RESTART),
                    PERMISSION,
                    null,
                    Context.RECEIVER_EXPORTED,
                )
                Log.i(LOG_TAG, "[$packageName] restart channel ready")
            }.onFailure { Log.w(LOG_TAG, "[$packageName] restart channel failed: $it") }
        }, CONTEXT_DELAY_MS)
    }

    private fun receiver(packageName: String) = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val wanted = intent?.getStringExtra(EXTRA_TARGET)
            if (wanted != null && wanted != packageName) return

            Log.i(LOG_TAG, "[$packageName] restart requested, ending this process")
            Handler(Looper.getMainLooper()).postDelayed(
                { Process.killProcess(Process.myPid()) },
                KILL_DELAY_MS,
            )
        }
    }

    const val EXTRA_TARGET = "target"

    fun currentApplication(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()
}
