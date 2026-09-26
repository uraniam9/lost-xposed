package dev.lostxposed.features.hardwarekeys

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.KeyEvent
import dev.lostxposed.core.api.HookEnv
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Hold power with the screen off to switch the torch.
 *
 * Android wakes the screen the moment power goes down, so a press cannot be looked at and
 * then passed on: by then the screen is already on. It is held back instead. If it comes up
 * again within [HOLD_MS] it was an ordinary press, and the held-back down goes through the
 * normal path, the same event with its original timestamps and flags, followed by the up.
 * The camera double press and Emergency SOS count presses by those timestamps, so they see
 * what they always saw. The one visible difference is that the screen wakes when you let go
 * rather than when you press.
 *
 * Held past [HOLD_MS], the torch switches and the up goes nowhere, so the screen stays off.
 * If the torch cannot switch (the camera app has it, say), the press is treated as an
 * ordinary one after all and the phone wakes on release.
 *
 * Presses with the screen on are never touched.
 */
internal class PowerHold(private val env: HookEnv, private val method: Method) {

    private class Held(val target: Any?, val args: Array<Any?>)

    private val lock = Any()
    private var held: Held? = null
    private var fired = false

    /** Set while a held-back press goes through, so it is not held back a second time. */
    @Volatile
    private var replaying = false

    private val handler = KeyThread.handler

    private val fire = Runnable {
        synchronized(lock) {
            if (held == null || fired) return@Runnable
            fired = true
        }
        if (Torch.toggle(handler)) {
            Buzz.once()
            env.log("power held with the screen off: torch switched")
        } else {
            // Nothing happened, so let go of it as an ordinary press and wake the phone.
            synchronized(lock) { if (held != null) fired = false }
            env.log("power held with the screen off, but the torch could not be switched")
        }
    }

    init {
        // Watching from the start means the first hold already knows whether the torch is on.
        Torch.watch(handler)
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
        // Repeats while it is held belong to the press already taken.
        if (event.repeatCount > 0) return held != null
        if (ScreenState.interactive) return false
        held = Held(chain.thisObject, chain.args.toTypedArray())
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
            // The torch press: the up goes nowhere and the screen stays off.
            if (fired) return true
            taken
        }
        replay(press)
        // The down has been through, so now the up goes the normal way too.
        return false
    }

    private fun replay(press: Held) {
        replaying = true
        try {
            method.invoke(press.target, *press.args)
        } catch (t: Throwable) {
            env.log("a held-back power press could not be passed on", t)
        } finally {
            replaying = false
        }
    }

    private companion object {
        const val HOLD_MS = 500L
    }
}

/** One thread for every held-key timer in this feature: the torch's and the volume skip's. */
internal object KeyThread {
    val handler: Handler by lazy { Handler(HandlerThread("LostXposed-keys").apply { start() }.looper) }
}

/**
 * The torch, switched the way the quick settings tile switches it: through CameraManager,
 * which needs no open camera and no permission.
 */
internal object Torch {

    private val on = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var watching = false

    @Volatile
    private var cameraId: String? = null

    /** Keeps track of the torch, including when the quick settings tile switches it. */
    fun watch(handler: Handler) {
        if (watching) return
        runCatching {
            manager()?.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        on[id] = enabled
                    }

                    override fun onTorchModeUnavailable(id: String) {
                        on[id] = false
                    }
                },
                handler,
            ) ?: return
            watching = true
        }
    }

    /** @return false when it could not be switched, for instance while the camera app has it. */
    fun toggle(handler: Handler): Boolean = runCatching {
        watch(handler)
        val manager = manager() ?: return false
        val id = cameraId ?: pick(manager)?.also { cameraId = it } ?: return false
        manager.setTorchMode(id, on[id] != true)
        true
    }.getOrDefault(false)

    private fun manager(): CameraManager? =
        SystemContext.get()?.getSystemService(CameraManager::class.java)

    /** The rear camera's flash when there is one, which is what the tile uses. */
    private fun pick(manager: CameraManager): String? {
        val withFlash = manager.cameraIdList.filter {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        return withFlash.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }
}

/** One short buzz, because with the screen off there is nothing else to say it worked. */
internal object Buzz {
    fun once() {
        runCatching {
            SystemContext.get()
                ?.getSystemService(VibratorManager::class.java)
                ?.defaultVibrator
                ?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK),
                )
        }
    }
}
