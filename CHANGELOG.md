# Changelog

Dates are when the build was cut. Every version says what is still untested, because that is
the half that decides whether you should install it.

## 0.2.2-beta (2026-09-26)

Called beta from here rather than alpha. Every user-facing feature has now been seen doing
its actual job on a device, not just installing: Clock Studio and the reference checks
already were, and Notification rules, Hardware keys and Power inspector join them this
release. Per-app display and Text engine have not, and still say so on their own screens.
The badge is app-wide; the two exceptions are marked where they actually matter.

Issue #1, closed: hooks did not survive a reboot. The cause was Direct Boot. SystemUI starts
before the phone is unlocked, Android will not run an app that has not declared itself aware
of that, and this one had not, so the one lookup SystemUI makes at boot failed with "Unknown
authority" and the clock sat there plain until Restart System UI was tapped by hand. The
settings provider is Direct Boot aware now, and answers from a copy kept in device-protected
storage until the phone is unlocked, at which point it switches back to the live settings.
Measured on the test phone: SystemUI read its settings 95 seconds before the lock screen was
even dismissed, and the clock had its style the moment the screen turned on. The one thing
this does not yet cover is `system_server` itself, which still gets `EACCES` reading that
same copy directly and has to wait for the boot to finish before it can ask over the settings
provider instead; Power inspector, Notification rules and Hardware keys start with nothing
configured on a fresh boot until that catches up, usually within a minute.

Found while chasing that one: `system_server` was being handed the same package to hook
again every time something else in it loaded a class, nine times in thirteen minutes on the
test phone, and each pass installed every `system_server` feature's hooks on top of the last.
Nothing crashed. Every wakelock, alarm and notification just ran through one more copy of the
same hook than the time before, and the pile grew for as long as the phone stayed on. Each
process is only dispatched once now.

Settings changes to Notification rules and Hardware keys apply without a reboot. Saving hands
the change to `system_server` directly, and the screen says whether it was taken; if nothing
answers, it still applies at the next reboot exactly as before. A version of the module
already running in `system_server` still needs a reboot to pick up new code, same as always;
this only removes the reboot for its own settings.

Hold a volume key for half a second, screen off, something already playing, to skip a track.
A shorter press still changes the volume as it always did. This replaces the old behaviour,
where any press skipped the track and there was no way to just turn the volume down.

Hold power for half a second, screen off, to switch the torch on or off, with a short buzz to
say it worked since there is nothing to see. A shorter press still wakes or locks the phone
exactly as before; the two are told apart by how long the key is actually held, not guessed
at.

Power inspector has a screen now instead of only a log line. Open its card for the current
wakelock and alarm count, resolved to app names, fetched straight from `system_server` with a
Refresh button. A live pull during testing showed thousands of wakelocks against the system
itself and over a thousand alarms from Google Play Services, which is the ordinary shape of
that count on most phones.

A per-package rule that had actually saved correctly could look like it had reset the moment
you left the screen: "Applies to" always reopened on the `*` default, so a rule set for one
app was invisible until you retyped its exact package name. The screen now remembers the last
package you used per feature, and shows a row of chips for every package that already has
something set, so switching between them does not mean retyping anything.

The update checker was rebuilt. It used the platform's own dialog, which took its colours
from the system theme rather than anything in this app, and crammed three choices into one
row; it now uses the app's own card, the choices stacked, and every step of a check, not just
failures, is logged, which is what finally showed the old one usually was not broken, just
still inside its twelve hour cooldown.

The main screen and About were reorganised. Status and Required scope stay at the top,
Features underneath in a fixed order rather than reshuffling every time one gets verified,
and the built-in checks that exist to prove the engine itself works are now a single
collapsed row instead of always open. About leads with how to say something rather than
ending on it, and the four longer explanations are questions you open rather than paragraphs
you scroll past.

Still untested: per-app display and text engine have never been confirmed doing their job on
a phone.

## 0.2.1-alpha (2026-09-25)

Two things a real device caught that a JVM test suite never would.

A toast was showing its own storage key instead of English: leave the per-app field on a
setting's default and it read "Saved 6 setting(s) for *", the internal marker for "every
app," never meant to be seen. Both places it showed up now say "every app" instead.

The bigger one: a genuine device reboot has been observed not to bring hooks back on its own.
Scope stays intact, and tapping Restart System UI once fixes it immediately, which rules out a
re-setup problem, but nothing before this release said a reboot needed that manual step at
all. Disclosed in the README's Scope section and tracked as issue #1; not fixed, because the
cause isn't confirmed yet. The restart button's own warning was also widened, since one report
described the whole screen flashing several times, not the single status-bar blink it promised.

## 0.2.0-alpha (2026-09-23)

Verified on one phone: Nothing AIN065, Nothing OS 4, Android 16, Vector 2.2.

Settings finally reach hooked processes, which is the change everything else in this release
sits on top of. Both channels the framework documents arrive empty on Vector 2.2, and reading
the module's own file by path from SystemUI returns ENOENT, not EACCES: per-app mount
namespaces, which no file mode or SELinux label can touch. What works is a binder call to a
ContentProvider, using a Context built from the `LoadedApk` that `ActivityThread` already
bound, because the system context is package `android` and gets rejected on a uid mismatch
otherwise.

With that working, three things use it directly. Clock Studio composes the status bar clock
from pieces (`{fuzzy} · {battery}`, `{day} {time}`, whatever you like) with size, colour,
weight, font, a few presets, and a live preview so checking a change no longer costs a SystemUI
restart. Per-app display presets are now percentages of your device's own values rather than
fixed numbers, so they mean the same thing on any phone. Text Engine lists the keyboards
actually installed instead of asking for a package name typed by hand.

Two things needed fixing that weren't about settings at all: SystemUI can't be restarted from
outside (`force-stop` on it is a no-op), so there's now a button that has the module end its
own pid from inside and let the system bring it back. And the boot guard, which has always
disabled risky features after a failed boot, used to do that quietly inside `system_server`;
it now reports to the app, with what was disabled, why, and a button that mails the
diagnostics.

A JVM test suite exists now too (94 tests, up from zero two versions ago), and it already
caught a real bug before a phone did: an emptied keyboard list fell through to hooking nothing
at all, silently, with every setting still looking correct.

Renamed to `io.github.uraniam9.lostxposed`. Relicensed to GPL-3.0-or-later.

Still untested: per-app display and text engine have never run on a phone. Notification rules,
hardware keys and power inspector install at boot but haven't been seen doing their job.
LSPosed is untested entirely. Everything above is Vector 2.2.

## 0.1.0-alpha (2026-09-22)

Not released. First build with a UI, seven features, a boot guard that fails closed, and
diagnostics that name the step that broke instead of failing silently.
