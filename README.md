# Lost Xposed

[![Licence: GPL-3.0-or-later](https://img.shields.io/badge/licence-GPL--3.0--or--later-blue)](LICENSE)
![Android 13+](https://img.shields.io/badge/android-13%2B-3DDC84)
![Status: beta](https://img.shields.io/badge/status-beta-blueviolet)

The settings Android keeps to itself.

Your phone can already do far more than its settings screen admits. Density is one value for
every app. The clock is a clock. Notifications arrive or they do not, and "or they do not" is a
per-app switch and nothing finer. None of that is a technical limit. It is a decision somebody
made on your behalf.

This reopens those decisions. A clock you compose yourself, out of the pieces you want, in the
order you want them, at the size and weight you choose. Density, font scale and refresh rate
per app instead of once for everything. Notification rules that read the text and stop a
message before it is ever posted, which no notification listener can do, because by the time
one sees a notification it has already arrived.

Every line here was written for this project. Where an older module had an idea first it is
credited by name, with its licence, in [NOTICE](NOTICE); nothing is copied from one.

---

## Read this before you install it

**Everything here has been seen working on exactly one phone:** a Nothing AIN065 on
Nothing OS 4, Android 16 (SDK 36), under Vector 2.2 with libxposed API 102. LSPosed itself is
untested. So is every other device.

The table below says which feature is which, and the state it reports is what somebody
actually watched happen on a phone, not what the code is supposed to do.

| Feature | What it does | State |
|---|---|---|
| Clock Studio | A status bar clock you compose: mix time, fuzzy time, date, day and battery, then set size, colour, weight and font | working |
| Self check | Reports whether the module is loaded in a given process | working |
| No-op reference | Counts clock repaints and changes nothing. Proves the engine and `unhook()` | working |
| Notification rules | Block per app, or by keyword, before the notification is posted | working |
| Hardware keys | Volume keys skip tracks while the screen is off | working |
| Power inspector | Counts wakelock and alarm requests per app. Read-only | working |
| Per-app display | Density, font scale and refresh rate per app, with presets that scale to your device | partly tested |
| Text engine | Two-finger cursor and selection gestures, any keyboard, any app | partly tested |

Not built yet: clipboard history, AppOps and spoof profiles (wants a Shizuku backend), and the
motion stabiliser, which is still a measurement spike rather than a feature.

## Scope, which is the thing that will bite you

A feature whose process is not ticked in your framework manager's scope for this module
installs nothing and reports nothing. It does not fail loudly. During development that
accounted for several long, confusing silences where the answer was always "you didn't tick
the box".

The manifest suggests `com.android.systemui`, `android` and `io.github.uraniam9.lostxposed`. That list cannot
be complete (which keyboard you use and which apps you want per-app display for are not
knowable in advance), so the app's main screen lists the full set, resolved on your device,
with the features that need each entry and what has to restart afterwards.

Things restart differently, and the app says which applies:

- SystemUI features take effect on the next SystemUI restart.
- App and keyboard features take effect the next time that process starts.
- `system_server` features load **only at boot**, because that process never restarts on its own the way SystemUI or an app does.

**A device reboot is not reliably the same as a SystemUI restart.** On the one device this has
been checked on, hooks were absent after a genuine reboot until Restart System UI was tapped
once. Scope stayed intact throughout, so this isn't a re-setup problem.
[Tracked as issue #1](https://github.com/uraniam9/lost-xposed/issues/1); until it's resolved,
tap Restart System UI once after every boot.

## Building

```bash
cd lostxposed && ./gradlew test assembleDebug
```

`qa.ps1` does the tests, both builds and the on-device checks in one pass. `-PostReboot` covers
the `system_server` features, which cannot be checked without one.

Release signing reads `lostxposed/keystore.properties`:

```properties
storeFile=lostxposed-release.jks
storePassword=...
keyAlias=lostxposed
keyPassword=...
```

That file and `*.jks` are gitignored and must stay that way. Without them `assembleRelease`
still succeeds and produces an unsigned apk that cannot be installed. It logs a warning
saying so, so you find out at build time instead of at install time.

Back the keystore up somewhere you will still have in two years. Lose it and Android will
refuse every future update to an installed build, because the signature will not match. Debug
and release use different keys, so switching between them needs an uninstall, which clears the
module's settings and drops it from the framework's scope list.

## What it will not do

Nothing here bypasses payment, defeats DRM, intercepts credentials, or collects anything
about you. The privacy features are for the person holding the phone, not for someone else
checking up on them.

The app makes **one** network request: a GET of `update.json` on GitHub to see whether a newer
version is out, only when you open it, cached for half a day, and switchable off. Nothing is
sent with it: no identifier, no version, no device details.

It declares two permissions. `INTERNET`, for that. And one of its own, at signature level: the
module's restart receiver runs inside SystemUI and has to be exported, so without that guard
any installed app could ask SystemUI to restart. Only a build signed with the same key can
send it.

Settings reach hooked apps over a local binder call. The provider identifies each caller by
uid and hands it only the settings that apply to it, so an unrelated app cannot enumerate which
words you filter notifications on.

The bar for adding a feature is the same one applied while planning this: it has to do something Android still will not do for
you, and it has to be worth running code inside your system processes. Plenty of clever old
modules fail that now, because Android grew a setting for them. Those are not coming back, and
saying so is more useful than a wishlist that never moves.

## How it is put together

Six core modules and one Gradle module per feature, so adding a feature is one line in a
registry and nothing else in the spine changes.

The part worth reading, if you only read one, is how settings get from the app into a hooked
process. Both channels the framework documents arrive empty on Vector 2.2, and reading the
module's own file by path from SystemUI returns **ENOENT, not EACCES**: per-app mount
namespaces, which no file mode or SELinux label can do anything about. What works is a binder
call to a ContentProvider, and making that work needed a Context built from the `LoadedApk`
that `ActivityThread` already bound, because the system context is package `android` and the
platform rejects a package name that does not match the calling uid.

That paragraph is the short version of a longer investigation; those measurements are what
decided it, not a guess that happened to work.

Every historical module this project drew on is credited, with its licence, in [NOTICE](NOTICE).

A JVM test suite covers the logic that is painful to reach from a device: fuzzy time
phrasing across every minute of the day, notification keyword matching, the boot guard's
fail-closed path, compatibility resolution and the settings key space.

## Where this is going

More of them, steadily. 62 things people used to be able to do with their phones got looked
at while planning this project, and the ones still worth
having get built one at a time, properly, with the compatibility work and the diagnostics,
rather than as a pile of hooks that breaks on the next Android release.

That is a lot of evenings, and nobody is funding it.
[Supporters](https://buymeacoffee.com/uraniam9) get the build before everyone else, a say in
what gets built next, and can ask me directly when something breaks. Feature requests from
supporters go to the top of the list.

It is not a paywall and it cannot be one. The source is public and the licence keeps it that
way, so nothing is locked and nothing checks whether you paid; every feature reaches everyone.
A licence like this one could not enforce a lock anyway, since anybody could remove it and
rebuild. What you are buying is first, and the time to keep going.

[CONTRIBUTING.md](CONTRIBUTING.md) covers pull requests, the clean-room rule and the
contributor agreement.

## Licence

GPL-3.0-or-later. Read it, change it, share it; anything built on it stays free the same way.
This is code that hooks `system_server` and reads notifications, and nobody should have to
trust a binary they cannot audit.

Clean-room throughout: nothing is copied from the old modules, including the ones whose
licences would now permit it. Every one of them is credited, with its own licence, in
[NOTICE](NOTICE).

---

From the maker of [Lune Bridge](https://github.com/uraniam9/lune-bridge), a root module for
warmer and darker screens, and [SonoLune](https://sonolune.app), a calm-first sleep and focus
app. Published as [uraniam9](https://github.com/uraniam9).
