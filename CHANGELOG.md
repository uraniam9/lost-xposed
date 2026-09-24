# Changelog

Dates are when the build was cut. Every version says what is still untested, because that is
the half that decides whether you should install it.

## 0.2.0-alpha — 2026-09-23

Verified on one phone: Nothing AIN065, Nothing OS 4, Android 16, Vector 2.2.

**Settings now reach hooked processes.** They never did before. Both channels the framework
documents arrive empty on Vector 2.2, and reading the module's own file by path from SystemUI
returns ENOENT — per-app mount namespaces, which no file mode or SELinux label can change.
What works is a binder call to a ContentProvider, and it needed a Context built from the
`LoadedApk` that `ActivityThread` already bound, because the system context is package
`android` and the platform rejects a package name that does not match the calling uid.

**Clock Studio.** Compose the status bar clock from pieces — `{fuzzy} · {battery}`,
`{day} {time}`, whatever you like — then set size, colour, weight and font. Presets sit under
the setting each one changes. A preview above the settings renders with the same code the
status bar does, so checking a change no longer costs a SystemUI restart.

**Restart System UI** as a button. The app cannot do it from outside — `force-stop` on SystemUI
is a no-op — so the module, already inside that process, ends its own pid and the system brings
it back.

**The boot guard speaks.** It has always disabled risky features after a failed boot, but it
did that in `system_server` where nobody could see it. It now reports to the app, which shows
what was disabled, why, and a button that mails it with the diagnostics attached.

**Per-app display presets** are percentages of your device's own values, so they mean the same
thing on any phone. **Text engine** lists the keyboards actually installed rather than asking
you to type a package name.

**94 tests**, up from zero two versions ago. They found a real bug before a device did: an
emptied keyboard list fell through to hooking nothing at all, silently, with every setting
still looking correct.

Renamed to `io.github.uraniam9.lostxposed`. Relicensed to GPL-3.0-or-later.

**Still untested:** per-app display and text engine have never run on a phone. Notification
rules, hardware keys and power inspector install at boot but have not been seen doing their
job. LSPosed is untested entirely — everything above is Vector 2.2.

## 0.1.0-alpha — 2026-09-22

Not released. First build with a UI, seven features, a boot guard that fails closed, and
diagnostics that name the step that broke instead of failing silently.
