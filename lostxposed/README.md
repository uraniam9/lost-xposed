# lostxposed

The product build — the module itself, as opposed to the exploratory prototyping that came
before it.

**Status: builds clean (debug + minified release). On-device exit criterion not yet run —
needs the device reconnected.**

---

## What is here

```
lostxposed/
├─ core/api           contracts loaded into EVERY process — keep tiny
├─ core/compat        environment detection + declarative compat tables
├─ core/diagnostics   probe framework and report model
├─ core/engine        registry, target matching, injection, handle tracking
├─ core/config        remote-preferences config channel
├─ core/safety        boot guard — required before ANY system_server hook
├─ features/noop           reference feature that proves the spine
├─ features/displayprofiles per-app density, font scale, refresh rate
├─ features/textengine      two-finger cursor/selection gestures, any keyboard
├─ features/powerinspector    read-only wakelock/alarm attribution (system_server)
├─ features/notificationrules suppress before display, per app or by keyword (system_server)
├─ features/hardwarekeys      volume keys skip tracks, screen off (system_server)
├─ features/smartstatusbar    clock from pluggable providers incl. fuzzy time (SystemUI)
├─ entry/xposed       libxposed entry point + feature registry + self check
└─ app                the APK: manifest, Xposed declarations, diagnostics UI
```

## QA in one command

```bash
cd C:\LostXposed\lostxposed && .\qa.ps1
```

Builds, installs, and checks injection, config delivery, detach, per-app display and Text
Engine, then prints a pass/fail table.

Most of it needs no reboot at all — only a process restart:

| Feature | Restart |
|---|---|
| Smart Status Bar, No-op | `am crash com.android.systemui` — **`force-stop` is a no-op here** |
| Per-App Display | `am force-stop <target app>` |
| Text Engine | `am force-stop <keyboard package>` |

**Exactly one reboot is unavoidable**, and only for the `system_server` features — Power
Inspector, Notification Rules, Hardware Keys — because `onSystemServerStarting` fires at boot
and nowhere else. That same reboot is the only way to exercise the **boot guard**, which is
itself the thing that stops a bad hook costing you many more:

```bash
.\qa.ps1                            # everything that needs no reboot
# reboot once
.\qa.ps1 -SkipBuild -PostReboot     # the system_server half, plus the guard
```

Dependency direction is strictly one-way: `app → entry → engine → {compat, diagnostics} → api`.
Nothing depends on `app`, which is why `SelfCheckFeature` hooks `dev.lostxposed.MainActivity`
**by name** rather than by type — a compile-time reference would be a cycle.

## Built on measured facts, not the design sketch

Four things changed from the original design sketch because early prototyping produced
evidence:

| Design said | Built as | Why |
|---|---|---|
| Hook multiplexer to stop features clobbering each other | **No multiplexer.** Features call `xposed.hook()` directly | spike-01: two hooks on one method both installed and both ran, ordered by `setPriority()` |
| `enable()/disable()` might need "reboot to apply" | **Real `disable()`** — `InjectionEngine.disable()` calls `HookHandle.unhook()` | spike-01: `unhook()` removed a live hook, verified by a counter that logged 8 invocations first |
| `Environment` in `core:compat` | **`Environment` in `core:api`** | `HookEnv` exposes it; leaving it in compat makes api depend on compat, which already depends on api |
| `isSupported(): Boolean` | **`Support` sealed type carrying `Reason`** | a boolean throws away exactly what diagnostics exists to display |

Capability bit values (`PROP_CAP_SYSTEM=1`, `PROP_CAP_REMOTE=2`, `PROP_RT_API_PROTECTION=4`)
were read out of `api:102.0.0` with `javap`, not assumed.

## Hard requirement: the module must be scoped to itself

Measured on Vector 2.2, 2026-09-20. This is not optional and it fails silently:

> **Settings written by the UI never reach hooked processes unless the module is in its own
> scope.**

The chain is:

1. The framework publishes a module's settings file by intercepting
   `getSharedPreferences(..., MODE_WORLD_READABLE)` **inside the module's own process**.
2. That interception only happens if the module is injected there — i.e. scoped to itself.
3. Without it, Android's own `SecurityException` for world-readable preferences propagates
   (API 24+ removed the mode), the writer silently falls back to `MODE_PRIVATE`, and the file
   is never registered.
4. `getRemotePreferences()` in the hooked process still returns a perfectly valid object — it
   is simply **empty**. No error is raised anywhere.

Observed symptoms when this is wrong: `config: remote prefs, schema v0` in the hook log while
the file on disk plainly reads `schema.version = 1`, and every feature reporting
`no settings for this package`.

`ConfigWriter.lastMode` records which mode succeeded, and the app now says so on its front
screen rather than leaving a silent misconfiguration. That is the diagnostics engine earning
its 69/75 — this took an hour to find by hand and should take one glance.

## The boot guard

`core:safety` is what makes a `system_server` feature acceptable at all. A crash there is not
a bug report, it is a factory reset.

It drops a marker before installing `BOOTLOOP`-tier hooks and clears it 90 seconds later. A
marker still present at the next `system_server` start means the previous boot never got that
far, so those features are disabled for this boot; two consecutive failures disables them
outright.

**It fails closed.** If the marker cannot be written — SELinux denial, read-only filesystem —
risky features are disabled rather than run unprotected. A crash detector that cannot detect
crashes is worse than no feature, because it invites the risk while providing none of the
protection.

Survival is the success signal, deliberately. The internals that would report "boot complete"
move between Android releases; a device that bootloops simply never reaches the timer, and
that is true on every version.

## Deliberately not built yet

- **`core:config`** — the cross-process channel. `getRemotePreferences()` /
  `openRemoteFile()` are confirmed present, but schema versioning needs design before hooks in
  `system_server` start reading it.
- **`core:safety`** — the boot guard. Not needed until something targets `system_server`, and
  nothing does yet. **Do not add a `system_server` feature before this exists.**
- **`ProcessTarget.CurrentIme`** — matches nothing today. Resolving the selected IME needs a
  `Settings.Secure` lookup the hooked process may not be able to make; it belongs with the IME
  backend, alongside Text Engine.

Each of these is a stub with a reason, not an oversight.

## Build

```bash
cd C:\LostXposed\lostxposed && .\gradlew.bat :app:assembleDebug
```

`JAVA_HOME` should point at Android Studio's JDK:
`C:\Program Files\Android\Android Studio\jbr`

## Phase 3 exit criterion

> A no-op feature installs, detaches, and reports diagnostics on 2 devices.

Procedure:

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

1. Enable **Lost Xposed** in the framework manager and scope it to **System UI** *and*
   **Lost Xposed** itself.
   On a device running Vector, the live manager is the parasitic one — not any installed
   `org.lsposed.manager` APK, which may be an orphan reporting "not installed". Launch it with:
   ```bash
   adb shell am start -a android.intent.action.MAIN \
     -c org.matrix.vector.manager.LAUNCH_MANAGER \
     -n com.android.shell/.BugreportWarningActivity -f 0x10000000
   ```
2. Restart SystemUI — **`force-stop` does not work**, it returns success and leaves the pid
   unchanged:
   ```bash
   adb shell am crash com.android.systemui
   adb shell pidof com.android.systemui   # confirm the pid changed
   ```
3. Read the report:
   ```bash
   adb logcat -d -s LostXposed
   ```

Expected: an entry per process, `Self check` passing in `dev.lostxposed`, and `No-op reference`
installing one hook in `com.android.systemui`. Opening the app should then say
**Module active in this process: yes**.

### Results — 2026-09-20

Nothing AIN065 / Android 16 / Vector 2.2 (API 102). Raw log:
[results/2026-09-20-nothing-ain065.txt](results/2026-09-20-nothing-ain065.txt).

| Check | Result |
|---|---|
| Injects into SystemUI | ✅ `installed 1 hook(s): noop.counter` |
| Hook actually executes | ✅ counter reached 4 |
| **Detaches without reboot** | ✅ `removed 1 hook(s); active now: none` |
| **Detach is real, not cosmetic** | ✅ counter still 4 **95 s later** |
| Reports diagnostics | ✅ full report per process |
| Declines non-targets | ✅ package `android` correctly skipped |
| Environment detection | ✅ `NOTHING Nothing OS (4) / Android 16 / Vector 2.2 (api 102)`, caps `SYSTEM, REMOTE, RT_API_PROTECTION` |
| Self check | ⬜ not run — module was scoped to System UI only |
| Two devices | ⬜ only one device available |

**The 95-second re-check is the part that matters.** `unhook()` returning success proves
nothing on its own; a hook that stayed live would have kept counting, because the clock
repaints at least once a minute. It did not. The detach is genuine, so `enable()`/`disable()`
can be built on it.

The engine also correctly declined the `android` package, which loads inside the SystemUI
process — target filtering works rather than injecting into whatever appears.

**Exit criterion: met on one device, for everything except the self check.** To finish it,
scope the module to **Lost Xposed** as well as System UI, and run it on a second device.
