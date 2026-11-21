# Security policy

Picket runs part of itself with shell privileges (through Shizuku, or as root through Sui). A bug in
that part is a bug in something that can stop apps, revoke permissions and change system settings,
so please report vulnerabilities privately.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting:
**[Report a vulnerability](https://github.com/Brokoli5191/Picket/security/advisories/new)**.

Please include:

- the Picket version and how it was installed (release APK, Obtainium, built from source),
- device, Android version and OEM build, Shizuku or Sui,
- what an attacker needs (another app installed? physical access? a crafted package name?),
- a proof of concept if you have one.

This is a one-person project. Expect a first answer within a week. Fixes for confirmed issues ship
as a patch release; you will be credited in the advisory unless you prefer otherwise.

## What is in scope

- The privileged UserService (`:privileged`) and its binder interface: argument validation, command
  construction, caller checks. The service must never run anything that is not one of its typed
  operations, and no input may change the shape of a command line.
- The action journal and its JSON export (it records what was changed on the device).
- The protected-package list and the risk rules that guard destructive actions.
- The release pipeline: signing, checksums and build provenance attestations.
- The "no INTERNET permission" guarantee.

## What is not

- Vulnerabilities in Shizuku or Sui themselves: report them to
  [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) and [RikkaApps/Sui](https://github.com/RikkaApps/Sui).
- Android framework or OEM bugs that Picket merely displays.
- Damage caused by actions a user confirmed deliberately (for example disabling a system package
  after typing its name). Missing or misleading warnings are in scope, though.

## Supported versions

Only the latest release receives security fixes.
