package dev.lostxposed.features.hardwarekeys

import android.view.KeyEvent
import dev.lostxposed.core.api.HookEnv
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * Hold a volume key past [HOLD_MS], with the screen off and something already playing, to
 * skip a track. A shorter press changes the volume exactly as it always did.
 *
 * The same trick as [PowerHold], for the same reason: a press cannot be judged and then passed
 * on, because passing it on is exactly what an ordinary volume tap needs to do. It is held
 * back instead. Released within [HOLD_MS] it was a tap, and the down that was held goes
 * through with its original timestamps and flags, followed by the up, so the volume changes
 * exactly as if this feature were not installed. Held past it, the track skips and the up
 * goes nowhere, so the volume never moves.
 *
 * Screen on, or nothing playing: [handle] returns false straight away, with nothing held back
 * and no delay added. Only the narrow case this feature is actually for pays for the pause a
 * hold gesture needs in order to tell itself apart from a tap.
 */
internal class VolumeHold(private val env: HookEnv, private val method: Method) {

    private class Held(val target: Any?, val args: Array<Any?>, val media: Int)

    private val lock = Any()
    private var held: Held? = null
    private var fired = false

    /** Set while a held-back press goes through, so it is not held back a second time. */
    @Volatile
    private var replaying = false

    private val handler = KeyThread.handler

    private val fire = Runnable {
        val media = synchronized(lock) {
            val h = held ?: return@Runnable
            if (fired) return@Runnable
            fired = true
            h.media
        }
        MediaControl.send(media)
        env.log("volume key held -> ${KeyEvent.keyCodeToString(media)}")
    }

    /** @return true when the event was taken and must not reach the rest of the system. */
    fun handle(chain: XposedInterface.Chain, event: KeyEvent): Boolean {
        if (replaying) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> down(chain, event)
            KeyEvent.ACTION_UP -> up()
            else -> false
        }
    }

    private fun down(chain: XposedInterface.Chain, event: KeyEvent): Boolean = synchronized(lock) {
        // A repeat while already held belongs to the press already taken. A repeat with
        // nothing held is a key that went down before this feature was installed.
        if (event.repeatCount > 0) return held != null
        if (ScreenState.interactive) return false

        val media = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> KeyEvent.KEYCODE_MEDIA_NEXT
            KeyEvent.KEYCODE_VOLUME_DOWN -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return false
        }
        if (!MediaControl.isPlaying()) return false

        held = Held(chain.thisObject, chain.args.toTypedArray(), media)
        fired = false
        handler.removeCallbacks(fire)
        handler.postDelayed(fire, HOLD_MS)
        true
    }

    private fun up(): Boolean {
        val press = synchronized(lock) {
            val taken = held ?: return false
            held = null
            handler.removeCallbacks(fire)
            // The hold already fired: the up goes nowhere, and the volume never moves.
            if (fired) return true
            taken
        }
        replay(press)
        // The down has been through, so the up goes the normal way too.
        return false
    }

    private fun replay(press: Held) {
        replaying = true
        try {
            method.invoke(press.target, *press.args)
        } catch (t: Throwable) {
            env.log("a held-back volume press could not be passed on", t)
        } finally {
            replaying = false
        }
    }

    companion object {
        const val HOLD_MS = 500L
    }
}
