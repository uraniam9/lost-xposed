# Changelog

Dates are when the build was cut. Every version says what is still untested, because that is
the half that decides whether you should install it.

## 0.2.0-alpha — 2026-09-23

Verified on one phone: Nothing AIN065, Nothing OS 4, Android 16, Vector 2.2.

Settings finally reach hooked processes, which is the change everything else in this release
sits on top of. Both channels the framework documents arrive empty on Vector 2.2, and reading
the module's own file by path from SystemUI returns ENOENT, not EACCES — per-app mount
namespaces, which no file mode or SELinux label can touch. What works is a binder call to a
ContentProvider, using a Context built from the `LoadedApk` that `ActivityThread` already
bound, because the system context is package `android` and gets rejected on a uid mismatch
otherwise.

With that working, three things use it directly. Clock Studio composes the status bar clock
from pieces — `{fuzzy} · {battery}`, `{day} {time}`, whatever you like — with size, colour,
weight, font, a few presets, and a live preview so checking a change no longer costs a SystemUI
restart. Per-app display presets are now percentages of your device's own values rather than
fixed numbers, so they mean the same thing on any phone. Text Engine lists the keyboards
actually installed instead of asking for a package name typed by hand.

Two things needed fixing that weren't about settings at all: SystemUI can't be restarted from
outside — `force-stop` on it is a no-op — so there's now a button that has the module end its
own pid from inside and let the system bring it back. And the boot guard, which has always
disabled risky features after a failed boot, used to do that quietly inside `system_server`;
it now reports to the app, with what was disabled, why, and a button that mails the
diagnostics.

A JVM test suite exists now too — 94 tests, up from zero two versions ago — and it already
caught a real bug before a phone did: an emptied keyboard list fell through to hooking nothing
at all, silently, with every setting still looking correct.

Renamed to `io.github.uraniam9.lostxposed`. Relicensed to GPL-3.0-or-later.

Still untested: per-app display and text engine have never run on a phone. Notification rules,
hardware keys and power inspector install at boot but haven't been seen doing their job.
LSPosed is untested entirely — everything above is Vector 2.2.

## 0.1.0-alpha — 2026-09-22

Not released. First build with a UI, seven features, a boot guard that fails closed, and
diagnostics that name the step that broke instead of failing silently.
