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

## D9 — How the privileged service gets its data (2026-11-20)

**Context.** The brief asks to weigh binder calls through `ShizukuBinderWrapper` (hidden APIs,
HiddenApiRefinePlugin) against shell commands and to prefer machine-readable output.

**Evaluation.**
- *Hidden-API binder calls* (`IActivityManager`, `IAppOpsService`, `INotificationManager`, ...) give typed
  results, but their signatures change between API levels and OEM builds (Samsung patches several of
  these services), each call needs compile-time stubs plus a reflection fallback, and what the
  call returns for the shell uid is not documented anywhere. Every one of them would need its own
  device matrix.
- *Shell commands* (`dumpsys`, `cmd`, `settings`, `pm`) are what Android's own tooling and adb users
  rely on, are reachable from the shell uid by design, and can be recorded as fixtures with adb
  alone. Their text output is the weak point; the fixture tree is the regression net.
- */proc* is a kernel ABI, stable and cheap, and the shell uid can read it for every process (it is
  what `ps` does).

**Choice for v1.**
- Processes: read /proc directly inside the UserService (no fork per sample).
- Everything else: fixed shell commands executed inside the UserService as argv arrays
  (`ProcessBuilder`, absolute `/system/bin/...` paths, no `sh -c`), parsed there by the `:core` parsers.
  `--checkin`/`--proto` variants are used where they exist and carry the needed fields (decided per
  data source in D16+ once the AOSP research and device fixtures are in).
- No hidden-API binder calls and no HiddenApiRefinePlugin in v1. Revisit per data source if a shell
  command turns out to be unusable on One UI.

**Rejected.** `Shizuku.newProcess` (deprecated, and it would put arbitrary command execution on the
app side); running parsers in the app (raw `dumpsys` output can exceed the binder buffer and contains
notification text that the app does not need to see).

## D10 — Binder payload format (2026-11-20)

Every AIDL method returns `byte[]` = gzip(JSON(`Reply<T>`)) built by `PayloadCodec` in `:core`.
`Reply` carries either a value or a typed `ServiceError` plus the call cost for the debug overlay.

- Typed per method: the result type is fixed by the method (documented in `IPicketService.aidl`)
  and decoded with that type's serializer on the client.
- Small: a 700-process snapshot is ≈ 30 KB compressed; payloads above 512 KB compressed are refused
  (binder transactions share a 1 MB buffer per process). Decompression is capped at 32 MB.
- Strict: unknown JSON keys are rejected; `PROTOCOL_VERSION` is checked on connect.

**Rejected.** Hand-written Parcelables mirroring every `:core` model (duplication, and models must stay
Android-free); `ParcelFileDescriptor` pipes (fd passing from the shell domain to the app domain adds
SELinux surface for no gain once results are parsed service-side).

## D11 — CPU and memory numbers (2026-11-20)

- CPU % is the share of the whole machine between two samples: Δ(utime+stime) of the process divided
  by Δ(total ticks of the aggregate `cpu` line of /proc/stat). 100 % = every online core busy
  (Sysinternals convention). `top` would show the same process as up to `cores × 100 %`.
- guest/guest_nice are not added to the total; the kernel already counts them in user/nice.
- RSS comes from field 24 of `/proc/<pid>/stat` (pages × page size). It is the same counter as `VmRSS`
  in `/proc/<pid>/status` but saves a read per process per sample; status is read only for the uid
  (cached per pid + start time together with cmdline, refreshed every 15 samples).
- A process instance is `(pid, start time)`, so recycled pids never inherit CPU history.
- PSS is not sampled; it is fetched on demand only (shell cannot read other apps' `smaps_rollup`).

## D12 — Importance classes (2026-11-20)

Derived from `oom_score_adj` in bands (`<0` system, `<100` foreground, `<200` visible, `<300`
perceptible, `<900` service/home/previous, `≤1000` cached). The constants inside the bands moved
between releases, the band edges did not. **To verify on device:** that the shell uid can read
`/proc/<pid>/oom_score_adj` of app processes; the fallback is the adj column of
`dumpsys activity lru`.

## D13 — Service lifecycle and caller checks (2026-11-20)

- `UserServiceArgs.daemon(false)`: the privileged process dies with the app; nothing privileged stays
  resident. `processNameSuffix("privileged")`.
- `version` = APK `lastUpdateTime` in seconds, so every install or update restarts a running
  service (versionCode is constant for local builds).
- The service takes the `(Context)` constructor; `context.applicationInfo.uid` is Picket's uid and
  every method rejects other callers (the Shizuku server's own uid is allowed for `destroy()`).
- Every operation runs under one lock (the snapshot reader keeps a cache), every exception becomes a
  typed `ServiceError`, and only `SecurityException` from the caller check crosses the binder as such.
- Sui is detected with `Sui.isSui()`; `ShizukuProvider` initialises Sui automatically.

## D14 — Polling instead of a callback stream (2026-11-20)

The process list polls (`listProcesses()` every 2 s, pausable) and diffs consecutive snapshots in the
app. A callback interface from the service would need a second AIDL interface, death handling in
both directions and back-pressure, for no gain at a 2 s cadence. The sampler flow only runs while
the screen collects it, and stops when Shizuku disconnects.

## D15 — Fixtures and the capture script share one naming rule (2026-11-20)

Fixture file name = the command's argv without `/system/bin/`, joined with `_` (`/` → `%`).
`scripts/capture-fixtures.sh` derives names the same way and records exactly the argv that
`ReadCommands` in `:core` runs (with `com.android.systemui` as the sample package and user 0).
`CaptureScriptTest` fails when a read command is added without being captured. A directory recorded
on a device therefore drives the collectors unchanged in tests (`FixtureExecutor`).
Until real recordings exist, `fixtures/synthetic/aosp-main/` holds hand-written output derived from
the AOSP `main` printing code (provenance in its `SOURCE.md`).

Privacy: the capture script redacts e-mail, MAC/IP addresses, Wi-Fi names, intent data URIs and
phone numbers, and maps user-installed package names to `com.example.userN` (opt-out flag), because
fixtures from a personal phone land in a public repository.

## D16 — Watcher sources (2026-11-20)

| Hook | Source | Notes |
|---|---|---|
| Notification listeners | `settings get secure enabled_notification_listeners` + "Live notification listeners" of `dumpsys notification` | Approvals are still mirrored to Settings (`NotificationListeners#shouldReflectToSettings` returns true because androidx reads it). Live = bound now. |
| Accessibility | `enabled_accessibility_services` + `accessibility_enabled` | Inactive when the master switch is off. |
| Device admins | `dumpsys device_policy` | "Enabled Device Admins (User N…)" blocks; device/profile owner from the owner blocks. |
| Keyboards | `enabled_input_methods`, `default_input_method` | Subtype hashes after `;` are dropped. |
| Usage access, overlay | `cmd appops query-op <OP> allow` | One package per line or `No operations.` (AppOpsService.Shell). Apps whose access comes from a pre-granted permission with mode `default` are not listed; noted as a known gap. |
| VPN | `dumpsys vpn_management` (Android 12+) + `always_on_vpn_app` | `Active vpn type: -1` = not connected; `[Legacy VPN]` = no app. Below Android 12 only the always-on setting is shown. |
| Default roles | `cmd role get-role-holders --user N ROLE` (API 34+), else `dumpsys role` | Holders joined with `;`. Older releases print `Unknown command` on stdout with exit 0, which must not be read as "no holders"; the collector probes once and falls back to the role dump. |
| DND access | "Allowed condition providers" of `dumpsys notification --zen`, `isPrimary: true` | `enabled_notification_policy_access_packages` is only maintained up to Android 11; `isPrimary: false` is the implicit grant every approved listener gets. |

Each source fails independently; the UI lists failed sources instead of hiding the rest.

## D17 — Wakeup sources, and a side effect avoided (2026-11-20)

- **Totals ("top offenders")**: `dumpsys batterystats -c android` — the aggregates since the last
  charge for *all* uids (`wl` partial wakelock time/count, `jb` jobs, `wua` wakeup alarms), without
  history. **Not `--checkin`**: that variant may print the *previous* cycle and deletes the
  system's saved checkin file (BatteryStatsService, main ~3495–3520), which would be a side effect
  on the user's device. Fallback when batterystats fails: "Alarm Stats" of `dumpsys alarm` (since boot).
- **Timeline**: events Android still keeps with a time — "App Alarm history" of `dumpsys alarm`
  (wakeup deliveries, about the last hour), the last delivery of each alarm tag ("Alarm Stats"), and
  "Job history" of `dumpsys jobscheduler` (last 100 start/stop events). Relative times are anchored
  to the service's clock when the dump is taken. The UI says that totals are since the last charge.
- **Not in v1**: decoding the battery history (`dumpsys batterystats -c --history-start N`), which
  would give every kernel wake with uid and alarm tag for 6/24 h windows. It needs incremental
  polling and re-anchoring on `TIME:` records; planned for a later version.
- `am get-standby-buckets` does not exist; `am get-standby-bucket` without a package lists `pkg: N`.

## D18 — Output differences between releases that the parsers handle (2026-11-20)

From the AOSP research (android10 … android17 branches); each case has a fixture or test:

- `dumpsys activity services`: no FGS type before API 34, `types=%08X` on API 34, `types=0x%08X`
  from API 35; from API 35 the record header carries ` c:<callingPackage>`; from API 34 a
  ` startCommandResult=` line is printed with a single leading space inside the block.
- `dumpsys package`: `userId=` up to API 33, `appId=` from API 34.
- `dumpsys meminfo --checkin PID`: checkin version 4 everywhere; total PSS is field 19 (kB).
  (`-c` is a different, compact format.)
- `cmd role get-role-holders`: API 34+ only (see D16).
- `dumpsys vpn_management`: connection state (`Active vpn type`) only from API 14; on 12/13 the
  listed package is merely the consented app and is not reported as an active VPN.

Behaviour that matters for actions (milestone 5): `am stop-service` exits 255 even on success (the
message decides), `am force-stop`/`am kill` exit 0 even for unknown packages, `cmd notification`
reports errors on stdout with exit 0, `accessibility_enabled` is recomputed by the system and is
never written, `pm default-state` is the exact inverse of `pm disable-user`, and system doze
exemptions need `sys-whitelist` (not offered in v1).

## D19 — UI structure and the motion budget (2026-11-20)

- **Navigation 3** (`navigation3` 1.2.2) with a single back stack; the four top-level destinations
  sit in a `NavigationSuiteScaffold` (bar on phones, rail on wider windows). Processes, Watchers and
  Wakeups are list panes and the app detail is a detail pane of the Material 3 adaptive
  `ListDetailSceneStrategy`, so wide windows show list and detail side by side. Predictive back comes
  from `NavDisplay`.
- **Experimental APIs** live in `ui/expressive/` only: `MaterialExpressiveTheme`, `MotionScheme`
  factories, `LoadingIndicator`, `MaterialShapes` and the adaptive list-detail strategy. Flexible top
  app bars, `SegmentedListItem`, `ButtonGroup`, `FloatingToolbar` and the collapsing scroll behaviour
  are stable in Material 3 1.5.0 and are used directly.
- **Motion**: Full = expressive motion scheme; Reduced = standard scheme and no digit rolls; Off = a
  `MotionScheme` whose specs all snap. "Follow system" maps an animator duration scale of 0
  ("Remove animations") to Reduced. Live numbers use tabular figures and roll vertically on the
  scheme's spatial and effects springs. Lists re-sort at most every 5 s and never while a finger is on
  the list or it scrolls; items animate with `animateItem()`. The loading indicator appears only after
  300 ms.
- **Colour**: dynamic colour follows the platform roles (which already follow the system contrast);
  an explicit contrast with dynamic colour rebuilds the scheme from the wallpaper's primary colour.
- **Screenshots**: Roborazzi renders every main screen from fake data in light, dark, high contrast
  and pure black, plus Processes and Watchers at 200 % font scale (`docs/screenshots/`).
- **Not done yet** (tracked for a follow-up): the shared-bounds transition from a list row to the
  app detail, `MorphPolygonShape` press morphs on custom containers, and `CapturedAnimatedVisibility`
  for appear/disappear. `ButtonGroup` already morphs its items on press.
