# Contributing

Bug reports and device profiles are the most useful thing you can send. The app builds a
diagnostics report for you — **Diagnostics → Share report**, or **About → Report a bug**, which
fills it in already.

## Before you open a pull request

Please open an issue first for anything beyond a typo. This project refuses to install a hook
on an Android version or OEM skin it has not been checked against, so a change usually needs a
compatibility row and a note about what was actually observed on a device, and that is worth
agreeing on before you write it.

Two rules that are not style preferences:

- **Clean room.** Nothing here is copied from another module, including ones whose licence
  would permit it. If you have read the implementation source of a GPL or proprietary project,
  do not write the corresponding feature here. See [LICENSES.md](LICENSES.md) §4.
- **Nothing fails silently.** A hook that cannot install says which step failed and why. A
  setting that cannot be read says so. "It returned without throwing" is not success.

## The contributor agreement, and why there is one

**By opening a pull request you agree that your contribution may be licensed by the project
owner under GPL-3.0-or-later *and* under other terms, including commercial ones.** You keep
the copyright in what you wrote. You are granting permission, not handing anything over.

Here is the honest reason, because it is a fair thing to want explained.

This project is GPL-3.0-or-later, which means anybody can read it, change it and share it, and
anything built on it stays free the same way. That is the point and it is not changing.

Separately, the copyright holder can license the same code to somebody else on different
terms — say, to a company that wants it inside a closed product. That is only possible if the
copyright holder has the right to do it for **every line**. Your contribution is yours; merging
it under GPL-3.0 alone would mean the project could never offer those other terms again
without finding you and asking. One merged pull request from somebody unreachable, and the
option is gone for good.

So this exists to keep a door open, not because there is a queue at it. Realistically an
Android module has few commercial licensees. It costs one paragraph to preserve and cannot be
added back later, which is the only reason it is here at the start rather than never.

A **DCO** sign-off — certifying you wrote it and have the right to submit it — is a different
thing and does not grant relicensing rights. If you would rather sign off than agree to the
above, say so in the pull request and the change can be looked at on that basis; it just
cannot be merged into a part of the code that might be dual-licensed.

## Supporters

New features reach [supporters](https://buymeacoffee.com/uraniam9) a release early. Nothing is
locked, nothing checks whether anybody paid, and every feature lands for everyone — supporters
just see it first. Fixes are the exception: anything that repairs a bootloop, a crash or a
battery problem ships to everyone at once, because holding a repair back to protect a perk
would be indefensible. A licence like this one could not enforce a lock anyway, since anybody could
remove it and rebuild, so pretending otherwise would be a lie with extra steps.
