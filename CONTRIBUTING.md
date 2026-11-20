# Contributing to Picket

## Build

Requirements: JDK 21, the Android SDK with platform `android-37.1` (Gradle downloads it when the
licences are accepted). Then:

```sh
./gradlew assembleDebug                      # app/build/outputs/apk/debug/
./gradlew spotlessApply                      # format (ktlint via Spotless)
```

The full check CI runs on every PR:

```sh
./gradlew spotlessCheck lint testDebugUnitTest :core:test :core:koverVerify \
          verifyRoborazziDebug assembleDebug verifyNoInternetPermission
```

GitHub Actions is the source of truth; a PR is ready when CI is green.

## Modules

| Module | What lives there |
|---|---|
| `:core` | Pure Kotlin: models, parsers for `dumpsys`/`cmd`/`/proc` output, argument validation, command builder, risk and protection rules. ≥ 90 % line coverage, enforced by Kover. |
| `:privileged` | The Shizuku UserService (runs as shell uid or root), its AIDL interface and the client that binds it. |
| `:app` | Compose UI, Room journal, DataStore settings, manual DI (`AppContainer`). |
| `:testtarget` | A harmless app with a dummy notification listener, accessibility service, device admin and foreground service. Only used by `scripts/device-checks.sh`. |

## Rules that are not up for debate

- **No `INTERNET` permission.** The build fails if the merged manifest requests it.
- **No arbitrary command execution.** The UserService exposes typed operations only. Every argument
  is validated inside the service and commands are argv arrays, never strings passed to `sh -c`.
- **No command is "verified" without a device result.** See below.
- English only, but every user-facing string lives in `strings.xml`.
- Experimental Material 3 Expressive APIs are used from `app/.../ui/expressive/` only.

## Device verification

Picket parses text that Android prints for humans, and that text differs between Android versions
and OEM builds. Two scripts collect what the code needs from a real device (USB or wireless adb,
Shizuku running is not required for them):

```sh
scripts/capture-fixtures.sh            # records command output into core/src/test/resources/fixtures/<device>/<api>/
scripts/device-checks.sh               # runs every action against :testtarget, then its inverse, and reports before/after
```

Commit the fixtures in a PR and paste the `device-checks.sh` summary into an issue using the
"Device results" link on the issue chooser. Results end up in `docs/COMMANDS.md`.

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org/) in English (`feat:`, `fix:`, `chore:`,
  `docs:`, `test:`, `refactor:`, `ci:`, `build:`).
- One topic per PR. Label it with exactly one of `feature`, `fix` or `maintenance`; the release notes
  are generated from these labels.
- UI changes: re-record screenshots with `./gradlew recordRoborazziDebug` and commit the images in
  `docs/screenshots/`.
