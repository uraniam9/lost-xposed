# Licensing Analysis

**Project licence: GPL-3.0-or-later** (changed 2026-09-23; Apache-2.0 from 2026-09-20 until
then, with no third-party code incorporated in that window, so nothing needs re-licensing).
**Policy: clean-room reimplementation only. No GPL or proprietary source is copied or
incorporated. Third-party notices preserved. Incompatible components isolated.**

### What changed, and what did not

The licence changed because Apache-2.0 permits a closed-source fork: anyone could take this,
add whatever they liked, and ship it without publishing a line. GPL-3.0 keeps the source
readable — which matters more than usual for a module that hooks `system_server` and reads
notifications, where nobody sensible installs a binary they cannot audit — while requiring
that anything built on it stays readable too.

**The clean-room policy is unchanged, but its status has.** Under Apache-2.0 it was partly a
legal necessity: GPL-3.0 source could not be incorporated at all. Under GPL-3.0-or-later,
GPL-3.0 projects are licence-compatible, so reading and reusing them would now be *lawful*.
The policy stands anyway, as a deliberate choice about provenance — it is what lets this
project say where every line came from. The tables below therefore keep two distinct columns:
what the licence permits, and what the policy allows.

Status: research finding, not legal advice. Have a lawyer confirm before distribution.
All licences below verified against `LICENSE`/`NOTICE` files and the GitHub licence API on
2026-09-20. **Source-code facts are marked ✅ VERIFIED. Inference and documentation claims are
marked ⚠️.**

---

## 1. Correction to the previous draft

> **The earlier claim that GravityBox has no licence and is "all rights reserved" was WRONG.
> GravityBox is Apache-2.0.**

GitHub's licence detector returns 404 for the repo because the grant lives in `NOTICE`, not
`LICENSE` — the detector only scans conventional filenames. Verified two ways:

- `NOTICE` carries an Apache License 2.0 grant, © 2021 Peter Gregus (xgravitybox@gmail.com),
  referencing `apache.org/licenses/LICENSE-2.0` with the standard "AS IS" disclaimer. ✅
- A sampled source file carries the same Apache-2.0 header, © 2021 Peter Gregus for GravityBox
  Project (C3C076@xda) — confirming the grant is per-file and repo-wide, not a stray notice. ✅

**This reverses the finding and materially improves the project's position.** GravityBox is
Apache-2.0, therefore combinable into this GPL-3.0 work (Apache-2.0 is one-way compatible with
GPL-3.0), therefore readable and reusable with attribution. It is the richest historical reference for SystemUI and status-bar
work, and it is now the *only* major historical project we may learn from directly.

Lesson recorded: **a 404 from GitHub's licence API means "no conventional licence file", not
"no licence".** Always check `NOTICE` and per-file headers before concluding a repo is
unlicensed.

---

## 2. Verified licence table

Two columns, because they now answer different questions. **Licence permits** is the law;
**policy allows** is this project's own clean-room rule, which is stricter.

| Project | Repo | Licence | Verified via | Licence permits | Policy allows |
|---|---|---|---|---|---|
| **GravityBox** | `GravityBox/GravityBox` | **Apache-2.0** ✅ | `NOTICE` + per-file header | ✅ combinable | ✅ **May read and reuse** with attribution |
| **libxposed/api** | `libxposed/api` | **Apache-2.0** ✅ | GitHub API; `master`; 730★; active 2026-08-27 | ✅ combinable | ✅ Compile dependency |
| `libxposed/service`, `example`, `helper` | `libxposed/*` | Apache-2.0 ✅ | GitHub org listing | ✅ combinable | ✅ May read and reuse |
| `libxposed/lint` | `libxposed/lint` | **None declared** ✅ | GitHub org listing | ❌ no grant | ⚠️ Tool only — do not vendor |
| **Shizuku** | `RikkaApps/Shizuku` | **Apache-2.0** ✅ | `LICENSE` | ✅ combinable | ✅ May depend on |
| **SwiftKeyExi (Exi)** | `Nordskog/SwiftKeyExi` | **GPL-3.0** ✅ | `LICENSE.md` | ✅ now compatible | ❌ **Do not read** — clean-room |
| **XPrivacy** | `M66B/XPrivacy` | **GPL-3.0** ✅ | `LICENSE.txt` | ✅ now compatible | ❌ **Do not read** — clean-room |
| **XPrivacyLua** | `M66B/XPrivacyLua` | **GPL-3.0** ✅ | `LICENSE`; archived | ✅ now compatible | ❌ **Do not read** — clean-room |
| **AppOpsXposed** | `jclehner/AppOpsXposed` | **GPL-3.0** ✅ | `LICENSE`; **archived 2025-11-10** | ✅ now compatible | ❌ **Do not read** — clean-room |
| **Iconify** | `Mahmud0808/Iconify` | **GPL-3.0** ✅ | `LICENSE` | ✅ now compatible | ❌ Do not read — clean-room |
| **LSPosed** | `LSPosed/LSPosed` | GPL-3.0 ✅ | GitHub API | ✅ now compatible | Runtime host — see §3 |
| **Vector** | `JingMatrix/Vector` | GPL-3.0 ✅ | GitHub API | ✅ now compatible | Runtime host — see §3 |
| **Xposed Additions** | `SpazeDog/xposed-additions` | **No detected licence** ⚠️ | 404 on licence API; `NOTICE`/headers **not yet checked** | ❓ unknown | ⚠️ **Do not read pending check** |
| MinMinGuard / MinMinGuard Next | — | Unverified ⚠️ | — | ❓ unknown | ⚠️ Do not read (⛔ excluded feature anyway) |
| Amplify | — | Likely proprietary ⚠️ | Documentation only | ❌ no grant | ❌ Do not read |
| Greenify | — | Proprietary ⚠️ | Documentation only | ❌ no grant | ❌ Do not read |
| BootManager, NotifyClean, Xposed Edge | — | Unverified ⚠️ | — | ❓ unknown | ⚠️ Do not read pending check |

### Corrections to prior attributions

| Previously stated | Verified | Note |
|---|---|---|
| AppOpsXposed by **d4rken** | **jclehner** ✅ | Wrong author. Repo archived 2025-11-10. |
| Exi at `TheRoughy/SwiftKeyExi` | `Nordskog/SwiftKeyExi` ✅ | Your brief's URL is wrong |
| Xposed Additions at `dk-zero-cool/XposedAdditions` | `SpazeDog/xposed-additions` ✅ | Author handle is `dk_zero-cool`; repo is under SpazeDog |

---

## 3. Compiling against Apache-2.0, loaded by GPL

The module compiles against `libxposed/api` (Apache-2.0) and is **loaded by** LSPosed/Vector
(GPL-3.0) at runtime. We do not link framework code.

Settled ecosystem practice — including closed-source modules distributed for years — treats
the guest as unaffected by the host's licence. ⚠️ **This is the standard reading, not a
court-tested one.** With Apache-2.0 chosen, we accept this position; it is the same position
every non-GPL Xposed module has relied on.

---

## 4. Read / do-not-read tiers

Binding policy. Clean-room only works if the barrier is real.

**Tier A — read freely, any project, any licence.** `LICENSE`, `NOTICE`, README, CHANGELOG,
manifests, build files, directory listings, release notes, issue trackers, per-file licence
headers. This is metadata and documentation; it conveys no implementation and creates no
derivative-work risk. **Everything in §2 was established from Tier A.**

**Tier B — read fully.** Apache-2.0 / MIT / BSD source: `libxposed/*`, Shizuku, **GravityBox**.
May be reused with attribution. These are one-way compatible with GPL-3.0: their code may be
combined into this project, but this project's code may not be taken back under their terms.

**Tier C — DO NOT READ.** Implementation source of GPL-3.0, unlicensed, or proprietary
projects: SwiftKeyExi, XPrivacy, XPrivacyLua, AppOpsXposed, Iconify, Xposed Additions
(pending), Amplify, Greenify, MinMinGuard.

For Tier C the permitted inputs are **behaviour, not code**: screenshots, changelogs, XDA
threads, user documentation, public API docs, and the observable behaviour of a running build.

> **The GPL-3.0 entries in Tier C are there by choice, not by law.** Since 2026-09-23 this
> project is GPL-3.0-or-later, so their source could lawfully be read and reused with
> attribution. Keeping them in Tier C is what lets this project say where every line came
> from, and it is what makes "rebuilt, not ported" a claim rather than a slogan. Moving a
> project from Tier C to Tier B is now a decision someone can make deliberately — and must
> record here, with the attribution requirements that come with it.

If a contributor has already read a Tier C project's source, they must not implement the
corresponding feature. Record that separation in writing per feature.

### Practical consequence

| Feature | Historical reference | Tier | Effect |
|---|---|---|---|
| Smart Status Bar, Statusbar Download Progress | GravityBox | **B** ✅ | **Reference implementation available.** Significantly cheaper than assumed. |
| Text Engine | Exi (GPL-3.0) | **C** | Clean-room from feature lists and XDA threads. Already the plan — our `InputConnection` design is architecturally unlike Exi's SwiftKey hooks. |
| Spoof Profiles | XPrivacy/Lua (GPL-3.0) | **C** | Clean-room. Take the *principle* (fake, don't deny) from docs, not the hook set from code. |
| AppOps Manager | AppOpsXposed (GPL-3.0) | **C** | Clean-room. AppOps is public platform surface; reconstruct from AOSP docs. |
| Hardware Key Actions | Xposed Additions (unverified) | **C** pending | Check `NOTICE`/headers before deciding. |

---

## 5. Attribution

Apache-2.0 §4 requires retaining notices and stating changes. Ship a repo `NOTICE` and an
in-app Credits screen. Credit inspiration even where no code is reused.

```
Lost Xposed — Copyright (C) 2026 <holder>. Licensed under Apache-2.0.

Contains code derived from GravityBox, Copyright (C) Peter Gregus
(C3C076@xda), licensed under Apache-2.0. Modified.
https://github.com/GravityBox/GravityBox

Framework API: libxposed, Apache-2.0. https://github.com/libxposed/api
Shizuku API: RikkaApps, Apache-2.0. https://github.com/RikkaApps/Shizuku

Inspired by, with NO code reused:
  Exi for SwiftKey (Nordskog, GPL-3.0) — Text Engine is a clean-room
    design at the InputConnection layer, not a SwiftKey hook.
  XPrivacy / XPrivacyLua (Marcel Bokhorst, GPL-3.0) — the fake-don't-deny
    principle only.
  AppOpsXposed (jclehner, GPL-3.0) — AppOps surfacing concept only.
```

Where GravityBox code **is** reused, add a per-file header naming origin project, author,
licence, commit hash and the nature of the modification — **at the moment of copying.**

---

## 6. Naming and trademark

- **Do not ship anything called "Exi."** The name belongs to a GPL-3.0 project we are not
  continuing and whose code we are not using. Use **Text Engine**.
- "Xposed" is Rovo89's project name. "Lost Xposed" describes ecosystem compatibility
  (ordinarily acceptable nominative use) but anchors the product to a decade-old brand.
  Consider a distinct name with "for LSPosed/Vector" as a descriptor.
- SwiftKey, Gboard, Pixel, One UI, HyperOS, Nothing OS are third-party trademarks. Reference
  factually for compatibility; never in branding or store listings.

---

## 7. Distribution

Google Play is not viable (root required, modifies other apps). Expected channels: GitHub
Releases, the LSPosed/Vector module repository, XDA.

This removes Play policy constraints but not legal ones. Certain things stay excluded
regardless of channel: DRM defeat, signature-verification bypass, IMEI/location spoofing,
in-app ad blocking.

---

## 8. Open actions

| # | Action | Blocks | Status |
|---|---|---|---|
| 1 | Apply Apache-2.0 `LICENSE` + `NOTICE` at repo init | Everything | ⬜ |
| 2 | Check `SpazeDog/xposed-additions` `NOTICE` + per-file headers | Hardware Key Actions tier | ⬜ |
| 3 | Record per-feature clean-room separation statements | First Tier C feature | ⬜ |
| 4 | Verify MinMinGuard, BootManager, NotifyClean, Xposed Edge licences | Only if those features return | ⬜ |
| 5 | In-app Credits screen + repo `NOTICE` | First public release | ⬜ |
| 6 | ~~Ask C3C0 for a GravityBox licence grant~~ | — | ✅ **Moot — already Apache-2.0** |
