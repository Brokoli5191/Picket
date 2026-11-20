# Picket

[![CI](https://github.com/Brokoli5191/Picket/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Brokoli5191/Picket/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/Brokoli5191/Picket?include_prereleases&sort=semver&display_name=tag)](https://github.com/Brokoli5191/Picket/releases)
[![Downloads](https://img.shields.io/github/downloads/Brokoli5191/Picket/total)](https://github.com/Brokoli5191/Picket/releases)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)
[![minSdk 29](https://img.shields.io/badge/minSdk-29%20(Android%2010)-informational)](gradle/libs.versions.toml)
[![Kotlin 2.4](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Requires Shizuku](https://img.shields.io/badge/requires-Shizuku-2E7D32)](https://shizuku.rikka.app/)
[![No INTERNET permission](https://img.shields.io/badge/INTERNET%20permission-none-success)](docs/DECISIONS.md)
[![Get it on Obtainium](https://img.shields.io/badge/Obtainium-add%20Picket-6438B5)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Brokoli5191/Picket)

Picket shows what is running on your Android phone, what keeps waking it up, and which apps hold the
hooks that let them read along: notification access, accessibility services, device admin, usage
access, overlays, input methods, VPN and default roles. It needs no root, only
[Shizuku](https://shizuku.rikka.app/), and every finding comes with an action you can undo.

> **Status:** under construction. There is no release yet; the sections below describe the plan for v1.

## What it does

- **Processes.** Every running app with its processes, CPU and memory use, importance and uptime.
  Per app: running services, wakelocks, alarms, standby bucket, battery-optimisation exemption,
  granted permissions and app-ops.
- **Watchers.** One screen listing every app that holds a privileged hook, with a plain explanation
  of what that hook lets it see or do. Sideloaded apps are marked.
- **Wakeups.** Who woke the device in the last 1, 6 or 24 hours: alarms, wakelocks and jobs.
- **Actions.** Force stop, restrict background work, move to the restricted standby bucket, revoke a
  notification listener or accessibility service, revoke permissions and app-ops, remove default roles.
  Each one shows the exact command before it runs, is written to a journal, and can be undone
  where Android allows it.

## Why it has no internet permission

Picket runs part of itself with shell privileges. A tool like that should not be able to send
anything anywhere, and "trust me" is not good enough: the build fails if the merged manifest requests
`INTERNET`, and CI checks the finished APK again.

## Build from source

JDK 21 and the Android SDK (platform `android-37.1`) are needed.

```sh
./gradlew assembleDebug
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full check, the module layout and how device
verification works.

## Releases

Releases are built, signed, verified, attested and published by
[`.github/workflows/release.yml`](.github/workflows/release.yml), which also creates the tag.
Versions follow `MAJOR.MINOR.PATCH[-rcN]`, and the versionCode is derived from them:

```text
versionCode = MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + (N for -rcN, 99 for the final release)
```

`0.1.0-rc1` is 10001 and `0.1.0` is 10099, so a release candidate updates to its final release and
every release installs over the previous one. MINOR and PATCH run from 0 to 99, N from 1 to 98.

`scripts/release-dry-run.sh 0.1.0-rc1` rehearses the build and the APK checks of the release workflow
locally with a throwaway key; nothing is tagged or published.

## License

Picket is free software under the [GNU General Public License v3.0 or later](LICENSE).
