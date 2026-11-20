# Commands

Every command Picket runs, exactly as the helper process runs it: an argv array, started with
`ProcessBuilder`, never through a shell. The helper runs as the shell user (uid 2000) through
Shizuku, or as root through Sui. The binaries are fixed (`ShellCommand.ALLOWED_BINARIES`:
`/system/bin/{am,pm,cmd,dumpsys,settings,ime,dpm}`); everything variable is validated in the
service before it reaches an argument.

**Status of this document: nothing is verified yet.** A command is marked verified only with a
result from [`scripts/device-checks.sh`](../scripts/device-checks.sh) on real hardware, pasted into a
GitHub issue and copied into the matrix below. Until then every entry says `pending`. The expected
behaviour comes from reading the AOSP sources (see [D18](DECISIONS.md)) and is what the parsers and
the outcome rules are written against, not a device result.

## Device matrix

| Action | Expected (AOSP) | Galaxy S23 · One UI 8 · API 36 | Galaxy S23 · One UI 7 · API 35 | Other devices |
|---|---|---|---|---|
| Force stop | works, exit 0 (also for unknown packages) | pending | pending | – |
| Kill background processes | works for cached processes only | pending | pending | – |
| Stop service | exit 255 even on success; exported services only | pending | pending | – |
| Restrict background (`RUN_ANY_IN_BACKGROUND ignore`) | works | pending | pending | – |
| Restricted standby bucket | works; the system may move the app again | pending | pending | – |
| Revoke notification listener | works; errors on stdout with exit 0 | pending | pending | – |
| Disable one accessibility service | works; other entries kept | pending | pending | – |
| Revoke runtime permission | works; the app's processes die | pending | pending | – |
| Revoke app op (overlay, usage access) | works | pending | pending | – |
| Remove battery-optimisation exemption | user exemptions only; system ones need `sys-whitelist` | pending | pending | – |
| Remove default role holder | works; browser/SMS/dialer may fall back to another app | pending | pending | – |
| Revoke DND access | works; errors on stdout with exit 0 | pending | pending | – |
| Disable app for user | works; inverse `default-state` restores the exact previous state | pending | pending | – |
| Device admin | shell removal only for `testOnly` admins → Picket opens Settings | pending | pending | – |
| Restricted settings (Android 13+) | undo via shell restores listener/accessibility for sideloaded apps | pending | pending | – |

The One UI/API columns are the releases the S23 is expected to run; the real values come from the
header of the device-checks summary and replace these headings when results arrive.

## Actions

`U` is the Android user id, `PKG` a validated package name, `COMP` a validated, flattened component
name that belongs to `PKG`. Each action is planned first: the service reads the current state
(shown as "Before" in the confirmation), decides the inverse, and returns the exact argv for the
preview. Executing re-checks that the command is identical to the one shown and that the state has
not changed since.

| Action | argv | Previous state read with | Inverse | Risk |
|---|---|---|---|---|
| Force stop | `cmd activity force-stop --user U PKG` | – | none | low |
| Kill background processes | `cmd activity kill --user U PKG` | – | none | low |
| Stop service (started services only) | `cmd activity stop-service --user U -n COMP` | – | none (fallback offered: force stop) | medium |
| Restrict background | `cmd appops set --user U PKG RUN_ANY_IN_BACKGROUND ignore` | `cmd appops get --user U PKG RUN_ANY_IN_BACKGROUND` | previous mode | low |
| Restricted standby bucket | `cmd activity set-standby-bucket --user U PKG restricted` | `am get-standby-bucket --user U PKG` | previous bucket | low |
| Revoke notification listener | `cmd notification disallow_listener COMP U` | `settings --user U get secure enabled_notification_listeners` | `allow_listener COMP U` | medium |
| Disable accessibility service | `cmd settings --user U put secure enabled_accessibility_services VALUE` (`delete` when nothing is left) | `settings --user U get secure enabled_accessibility_services` | `put` of the previous value | medium |
| Revoke runtime permission | `cmd package revoke --user U PKG PERM` | `dumpsys package PKG` | `cmd package grant --user U PKG PERM` | low |
| Revoke app op | `cmd appops set --user U PKG OP ignore` | `cmd appops get --user U PKG OP` | previous mode | low |
| Remove battery-optimisation exemption | `cmd deviceidle whitelist -PKG` | `cmd deviceidle whitelist` | `cmd deviceidle whitelist +PKG` | low |
| Remove default role holder | `cmd role remove-role-holder --user U ROLE PKG` | `cmd role get-role-holders --user U ROLE` (API 34+), else `dumpsys role` | `add-role-holder` | medium |
| Revoke DND access | `cmd notification disallow_dnd PKG U` | `dumpsys notification --zen` | `allow_dnd PKG U` | medium |
| Disable app for user | `cmd package disable-user --user U PKG` | `dumpsys package PKG` | `cmd package default-state` or `enable`, whichever restores the previous state | **high** |

How a result is judged (`ActionPolicy.outcome`): a timeout fails; `stop-service` succeeds only on
the message `Service stopped` (its exit code is 255 either way); otherwise `Exception occurred` or
`Failure` anywhere, or `Error` on stdout, fails the action even with exit 0; `disable-user` must
also report a `new state`; everything else needs exit 0. When the previous state cannot be read,
the action still runs but is journalled without an inverse, and the confirmation says so.

Never written: `accessibility_enabled` (the system recomputes it from the list of enabled services).

### Safety rules enforced in the service

- **Protected packages** (`app/src/main/assets/protected.json`, viewer in Settings → Protected apps)
  plus, resolved at plan time: the current launcher (`cmd package resolve-activity --brief -a
  android.intent.action.MAIN -c android.intent.category.HOME`), the current keyboard
  (`settings get secure default_input_method`), Shizuku and Picket itself. Stop, kill, stop service,
  background restriction, the rare/restricted buckets and disabling are refused for them. If the
  file cannot be read, these actions are refused for every package.
- **High-risk actions on system apps** need the package name typed in the confirmation.
- Samsung entries in `protected.json` are a starting point; the list is to be completed from S23
  testing.

### Settings fallbacks

Shown when an action fails, and as the only option where no shell command exists.

| Case | Opens |
|---|---|
| Notification listener | `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| Accessibility | `Settings.ACTION_ACCESSIBILITY_SETTINGS` |
| Overlay | `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` (`package:` URI) |
| Usage access | `Settings.ACTION_USAGE_ACCESS_SETTINGS` |
| Battery optimisation | `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
| Default apps | `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` |
| DND access | `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` |
| Device admin | `Settings.ACTION_SECURITY_SETTINGS` (no public intent for the admin list) |
| VPN | `Settings.ACTION_VPN_SETTINGS` |
| Keyboards | `Settings.ACTION_INPUT_METHOD_SETTINGS` |
| Everything else | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (`package:` URI), also used when a screen is missing on an OEM build |

## Reads

| What | argv | Parser | Fixture |
|---|---|---|---|
| Processes | `/proc/<pid>/{stat,status,cmdline,oom_score_adj}`, `/proc/stat`, `/proc/uptime` (files, no command) | `ProcParsers` | synthetic (linux-proc) |
| Running services | `dumpsys activity services` (per app: `… services PKG`) | `ActiveServicesParser` | synthetic (AOSP main) |
| Notification listeners | `settings get secure enabled_notification_listeners`, `dumpsys notification` | `SettingsLists`, `NotificationDumpParser` | synthetic |
| DND access | `dumpsys notification --zen` | `NotificationDumpParser.dndAccessPackages` | synthetic |
| Accessibility | `settings get secure enabled_accessibility_services`, `… accessibility_enabled` | `SettingsLists` | synthetic |
| Device admins | `dumpsys device_policy` | `DevicePolicyParser` | synthetic |
| Keyboards | `ime list -s`, `settings get secure enabled_input_methods`, `… default_input_method` | `SettingsLists` | synthetic |
| Usage access, overlay | `cmd appops query-op GET_USAGE_STATS allow`, `… SYSTEM_ALERT_WINDOW allow` | `AppOpsParser` | synthetic |
| VPN | `dumpsys vpn_management`, `settings get secure always_on_vpn_app` | `VpnParser` | synthetic |
| Default roles | `cmd role get-role-holders --user U ROLE` (API 34+), else `dumpsys role` | `RoleHolders` | synthetic |
| App detail | `dumpsys package PKG`, `cmd appops get --user U PKG`, `am get-standby-bucket --user U PKG`, `cmd deviceidle whitelist` | `PackageDumpParser`, `AppOpsParser`, `StandbyBucketParser`, `DeviceIdleParser` | synthetic |
| Wakeup totals | `dumpsys batterystats -c android` (not `--checkin`, see [D17](DECISIONS.md)) | `BatteryStatsCheckinParser` | synthetic |
| Wakeup timeline | `dumpsys alarm`, `dumpsys jobscheduler` | `AlarmDumpParser`, `JobHistoryParser` | synthetic |
| Wake locks | `dumpsys power` | `PowerParser` | synthetic |
| Memory (on demand) | `dumpsys meminfo --checkin PID` | `MeminfoParser` | synthetic |

Real recordings replace the synthetic fixtures once
[`scripts/capture-fixtures.sh`](../scripts/capture-fixtures.sh) has run on a device; the parsers
then get tests against `fixtures/<device>/<api>/`.

## How to verify

```sh
./gradlew :testtarget:assembleDebug
scripts/device-checks.sh            # needs adb and a device with USB debugging, no Shizuku
```

The script installs `:testtarget` (a dummy app with a notification listener, an accessibility
service, a device admin and a foreground service), runs each action and its inverse against it, and
writes `device-results/<device>-<api>-<time>/summary.md` with one row per action: command, exit
status, effect, inverse, state after undo and a PASS/FAIL verdict. Paste that summary into an issue;
the matrix above is then filled in from it, one column per device and release.
