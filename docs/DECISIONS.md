# Decisions

Every non-obvious decision, with context and the alternatives that were rejected. Newest last.
"Verified" in this file means checked against primary sources (Maven metadata, AAR metadata, AOSP
source, official docs) at the date given; device behaviour is tracked in [COMMANDS.md](COMMANDS.md).

---

## D1 — Toolchain versions (2026-11-20)

**Context.** The brief names Compose BOM 2026.11.01, Material 3 1.5.0 and Shizuku 13.1.5 and asks to
verify them.

**Verified.**
- Compose BOM `2026.11.01` is the newest BOM on Google Maven. It maps to Compose UI/foundation/animation
  `1.13.0` and Material 3 `1.5.0`.
- AAR metadata of `ui-android 1.13.0` and `foundation-android 1.13.0`: `minCompileSdk=37`,
  `minCompileMinorSdk=1`, `minAndroidGradlePluginVersion=9.1.0`. Material 3 1.5.0 needs compileSdk 37.
- AGP: newest stable is `9.5.2` (9.6 is alpha). AGP 9.5 supports API levels up to 37 and needs
  Gradle ≥ 9.6. Gradle `9.9.2` is current.
- Kotlin: newest stable is `2.4.21` (2.5.0 is RC). KSP is versioned independently since 2.3: `2.3.14`.
- Shizuku `dev.rikka.shizuku:api`/`provider` `13.1.5` is still the newest release on Maven Central.

**Choice.** AGP 9.5.2, Gradle 9.9.2 (wrapper with `distributionSha256Sum`), Kotlin 2.4.21, KSP 2.3.14,
`compileSdk 37` + `compileSdkMinor 1`, `targetSdk 37`, `minSdk 29`.

**Notes.**
- AGP 9 compiles Kotlin itself ("built-in Kotlin"); its POM only carries a runtime dependency on
  KGP 2.2.10. Declaring `org.jetbrains.kotlin.jvm` (2.4.21) in the root `plugins {}` block puts the
  newer KGP on the shared build classpath, so all modules compile with 2.4.21. The
  `org.jetbrains.kotlin.android` plugin is not applied (AGP 9 rejects it).
- `targetSdk 37` because AGP 9.5 supports API 37; the brief's fallback (36) was not needed.

## D2 — minSdk 29 (2026-11-20)

**Context.** The brief proposes Android 10 because `cmd notification`, `cmd appops` and
`am set-standby-bucket` behave consistently from there.

**Choice.** `minSdk 29`. Known API-level gaps, handled per action instead of raising minSdk:
- The `restricted` standby bucket exists from API 30. On API 29 the action is offered as `rare` or hidden.
- `cmd role` exists from API 29 (RoleManager was introduced in Q).
- Everything else is verified per API level in [COMMANDS.md](COMMANDS.md).

**Rejected.** minSdk 30 (would drop Android 10 devices for one bucket name).

## D3 — Seed colour schemes: `material-color-utilities` only (2026-11-20)

**Context.** Seed-based schemes (Tonal spot, Vibrant, Expressive, Fidelity) need the Material colour
algorithms (HCT, tonal palettes, dynamic schemes); Compose only ships the wallpaper-based ones.

**Checked.** MaterialKolor (`com.materialkolor`), MIT licence, active releases (6.0.0 on
Maven Central). The Compose wrapper `material-kolor-android 5.0.1` depends on JetBrains Compose
Multiplatform artifacts (`org.jetbrains.compose.material3:material3-android 1.12.0-alpha03`,
foundation/ui 1.12.0), which would pull a second Compose build next to AndroidX Compose 1.13.
The algorithm module `material-color-utilities` (Android AAR ≈ 220 KB before R8) has no dependencies
besides the Kotlin stdlib.

**Choice.** Depend on `com.materialkolor:material-color-utilities` and map its `DynamicScheme`
roles to a Compose `ColorScheme` in a small adapter in the app.

**Rejected.** `material-kolor` (dependency conflict risk), porting the algorithms ourselves
(≈ 2 000 lines to maintain), MDC-Android's restricted `color.utilities` (pulls the View-based library).

## D4 — Enforcing "no INTERNET permission" (2026-11-20)

**Choice.** Three layers:
1. `app/src/main/AndroidManifest.xml` declares `INTERNET` with `tools:node="remove"`, so a library
   that adds it is stripped during manifest merging.
2. `verify<Variant>NoInternetPermission` (Gradle task, `app/build.gradle.kts`) parses every merged
   manifest via the AGP Variant API and fails the build if `INTERNET` survives. Every `assemble<Variant>`
   depends on it, and `check` depends on the aggregate `verifyNoInternetPermission`.
3. CI dumps the permissions of the built APK with `aapt2 dump permissions` and fails on `INTERNET`.

**Rejected.** A custom Lint check (needs a separate lint-checks module and runs on sources, not on
the merged result) and a plain unit test (cannot see the merged manifest of the variant under test).

## D5 — Release build contract (2026-11-20)

- `versionName`/`versionCode` from `-PappVersionName`/`-PappVersionCode`, fallback `0.0.0-dev`/`1`.
- Release signing comes only from `SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`,
  `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. If any is missing, `verifyReleaseSigning` fails before
  `packageRelease`/`validateSigningRelease` with a message naming the missing variables; there is
  no path that produces an unsigned release APK.
- R8 and resource shrinking on for release; `dependenciesInfo` excluded from APK and bundle.
- Debug builds use `applicationIdSuffix ".debug"` so a debug build can sit next to a release build
  (each gets its own Shizuku permission and provider authority).
- No ABI or density splits, so exactly one APK lands in `app/build/outputs/apk/release/`.

## D6 — English only, but localisable (2026-11-20)

All strings live in `strings.xml`. `androidResources.localeFilters += "en"` removes the translations
that AndroidX libraries bring along, so the UI does not mix languages until Picket itself is translated.

## D7 — No backups (2026-11-20)

`allowBackup="false"` plus `dataExtractionRules` excluding every domain for cloud backup and device
transfer. The journal records privileged changes made on this device and is meaningless (and
sensitive) on another one.

## D8 — Repository bootstrap and branches (2026-11-20)

The repository was empty, without a `main` branch. `main` was created with a minimal bootstrap
commit so that pull requests have a base and `release.yml` (dispatch from the default branch) can
work later. Each milestone then lives on its own branch with its own PR.
