# Synthetic AOSP `main` fixtures

Written by hand from the printing code in AOSP `main` (November 2026), not recorded on a device.
They exist so parsers can be written before real recordings arrive; real fixtures from
`scripts/capture-fixtures.sh` replace or complement them. File names follow the capture script's
rule: argv without `/system/bin/`, joined with `_`.

| Fixture | Printed by |
|---|---|
| `settings_get_secure_*` | `SettingsProvider` (`cmd settings get`); listeners mirrored by `NotificationManagerService.NotificationListeners#shouldReflectToSettings` |
| `dumpsys_notification` | `ManagedServices#dump` ("Allowed …", "All … enabled for current profiles", "Live …") |
| `dumpsys_device_policy` | `OwnersData#dump`, `DevicePolicyData#dump`, `ActiveAdmin#dump` |
| `cmd_appops_*` | `AppOpsService.Shell` (`query-op`, `get`) |
| `cmd_role_get-role-holders_*` | `RoleShellCommand` (holders joined with `;`) |
| `dumpsys_vpn_management` | `VpnManagerService#dump`, `Vpn#dump` |
| `dumpsys_activity_services_*` | `ActiveServices`, `ServiceRecord#dump` |
| `dumpsys_package_*` | `PackageManagerService` dump (`runtime permissions:` per user) |
| `cmd_deviceidle_whitelist`, `dumpsys_power`, `am_get-standby-bucket_*` | `DeviceIdleController`, `PowerManagerService`, `ActivityManagerShellCommand` |
| `dumpsys_alarm`, `dumpsys_jobscheduler`, `dumpsys_batterystats_-c_android` | `AlarmManagerService#dumpImpl`, `JobSchedulerService` (Job history), `BatteryStats#dumpCheckinLocked`; generated with Python ports of the AOSP formatters |

Added from the AOSP research reports: `dumpsys_activity_services.txt` (users 0 and 10, API 35
header with ` c:`, `startCommandResult` line), `dumpsys_meminfo_--checkin_12345.txt`,
`dumpsys_role.txt` and `dumpsys_notification_--zen.txt` (condition providers section only).
