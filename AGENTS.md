# Agent guide for Picket

Picket is an Android app (Kotlin, Jetpack Compose, Material 3 Expressive) that shows running
processes, wakeups and privileged hooks ("watchers"), using Shizuku for shell privileges.
Application id and root package: `app.brokoli5191.picket` (permanent, never change it).

## Build commands

```sh
./gradlew assembleDebug                         # debug APK (applicationId suffix .debug)
./gradlew :core:test :core:koverVerify          # parsers, models, validation; >= 90 % line coverage
./gradlew testDebugUnitTest                     # Android unit + Robolectric tests
./gradlew recordRoborazziDebug                  # re-record screenshots into docs/screenshots/
./gradlew verifyRoborazziDebug                  # compare screenshots
./gradlew spotlessApply                         # format; spotlessCheck in CI
./gradlew verifyNoInternetPermission            # merged manifests must not request INTERNET
scripts/release-dry-run.sh 0.1.0-rc1            # release build + checks with a throwaway key
./gradlew :app:generateBaselineProfile          # needs KVM (managed emulator); CI: baseline-profile.yml
```

Full CI set (run before pushing):

```sh
./gradlew spotlessCheck lint testDebugUnitTest :core:test :core:koverVerify \
          verifyRoborazziDebug assembleDebug verifyNoInternetPermission \
          :baselineprofile:compileBenchmarkReleaseKotlin
```

Long Gradle runs: start them in the background and poll. `org.gradle.jvmargs=-Xmx4g`.

## Module map

- `:core` — pure Kotlin/JVM. Models (`model/`), parsers (`parse/`), validation (`validation/`),
  command construction and risk rules (`action/`), binder payload codec (`ipc/`). No Android imports.
- `:privileged` — AIDL (`IPicketService`), the Shizuku UserService (`service/`) that runs as
  shell/root, and the client side (`client/`) used by the app to bind it.
- `:app` — UI (`ui/`, experimental Expressive APIs only in `ui/expressive/`), data layer, Room
  journal, DataStore settings, `AppContainer` (manual DI).
- `:testtarget` — dummy app used by `scripts/device-checks.sh` only.
- `:baselineprofile` — Baseline Profile generator and Macrobenchmarks (startup, list scrolling) for
  the `benchmarkRelease`/`nonMinifiedRelease` builds, which are debug-signed with the id
  `app.brokoli5191.picket.benchmark`.

## Hard rules

1. **Verify commands on a device.** Never trust command syntax, flags or output formats from memory.
   A command is listed as verified in `docs/COMMANDS.md` only with a result from
   `scripts/device-checks.sh` on real hardware (primary: Galaxy S23 / One UI).
2. Parsers are tested against recorded fixtures in `core/src/test/resources/fixtures/<device>/<api>/`.
   Fixtures under `synthetic/` are reconstructed from AOSP source and are a stopgap until real
   recordings exist; a `SOURCE.md` next to them names the AOSP code they were derived from.
3. The UserService exposes typed operations only. Validate every argument inside the service
   (`Validators`), build commands as argv arrays, never use `sh -c`.
4. No `INTERNET` permission, ever.
5. All user-facing text in `strings.xml`, English only.
6. Record non-obvious decisions in `docs/DECISIONS.md` (context, choice, rejected alternatives).

## Fixture workflow

1. `scripts/capture-fixtures.sh` runs on a computer with `adb` and writes outputs into the fixture tree.
2. The maintainer commits them; parsers get tests against them.
3. `scripts/device-checks.sh` runs each action against `:testtarget` and its inverse; the summary is
   pasted into a GitHub issue and copied into `docs/COMMANDS.md`.

## Git and releases

- Conventional Commits in English; one PR per topic, labelled `feature`, `fix` or `maintenance`.
- Cloud sessions cannot push tags or delete branches. Releases are created by
  `.github/workflows/release.yml` (dispatch with a version input); it creates the tag itself.
  Do not modify that workflow's logic.
