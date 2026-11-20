package app.brokoli5191.picket.core.collect

import app.brokoli5191.picket.core.command.CommandExecutor
import app.brokoli5191.picket.core.command.CommandOutput
import app.brokoli5191.picket.core.command.ReadCommands
import app.brokoli5191.picket.core.command.Role
import app.brokoli5191.picket.core.command.SecureKey
import app.brokoli5191.picket.core.command.ShellCommand
import app.brokoli5191.picket.core.model.AppDetail
import app.brokoli5191.picket.core.model.AppWakeups
import app.brokoli5191.picket.core.model.IdleExemption
import app.brokoli5191.picket.core.model.SourceStatus
import app.brokoli5191.picket.core.model.WakeupEvent
import app.brokoli5191.picket.core.model.WakeupKind
import app.brokoli5191.picket.core.model.WakeupReport
import app.brokoli5191.picket.core.model.WatcherEntry
import app.brokoli5191.picket.core.model.WatcherKind
import app.brokoli5191.picket.core.model.WatchersSnapshot
import app.brokoli5191.picket.core.parse.ActiveServicesParser
import app.brokoli5191.picket.core.parse.AlarmDumpParser
import app.brokoli5191.picket.core.parse.AppOpsParser
import app.brokoli5191.picket.core.parse.BatteryStatsCheckinParser
import app.brokoli5191.picket.core.parse.DeviceIdleParser
import app.brokoli5191.picket.core.parse.DevicePolicyParser
import app.brokoli5191.picket.core.parse.JobHistoryParser
import app.brokoli5191.picket.core.parse.NotificationDumpParser
import app.brokoli5191.picket.core.parse.PackageDumpParser
import app.brokoli5191.picket.core.parse.RoleHolders
import app.brokoli5191.picket.core.parse.SettingsLists
import app.brokoli5191.picket.core.parse.StandbyBucketParser
import app.brokoli5191.picket.core.parse.VpnParser
import app.brokoli5191.picket.core.process.Uids

/** Runs commands and records, per source, whether it worked; one failing source never hides the rest. */
private const val MAX_ERROR = 200

internal class SourceTracker(
    private val executor: CommandExecutor,
) {
    val statuses = mutableListOf<SourceStatus>()
    var commandsRun = 0
        private set

    /** Runs [command] and hands stdout to [parse]; failures become a [SourceStatus] instead of an exception. */
    fun <T> source(
        name: String,
        command: ShellCommand,
        parse: (String) -> T,
    ): T? {
        commandsRun++
        val output: CommandOutput =
            try {
                executor.run(command)
            } catch (e: Exception) {
                statuses += SourceStatus(name, ok = false, message = e.message ?: e.javaClass.simpleName)
                return null
            }
        if (!output.ok) {
            val reason =
                if (output.timedOut) "timed out" else "exit ${output.exitCode}: ${output.stderr.trim().take(MAX_ERROR)}"
            statuses += SourceStatus(name, ok = false, message = reason)
            return null
        }
        return try {
            parse(output.stdout).also { statuses += SourceStatus(name, ok = true) }
        } catch (e: RuntimeException) {
            statuses += SourceStatus(name, ok = false, message = "unexpected output: ${e.message}")
            null
        }
    }
}

/** Who holds which privileged hook, from the sources documented in docs/DECISIONS.md (D16). */
class WatchersCollector(
    private val executor: CommandExecutor,
    private val userId: Int = 0,
) {
    var commandsRun: Int = 0
        private set

    fun collect(): WatchersSnapshot {
        val s = SourceTracker(executor)
        val entries = mutableListOf<WatcherEntry>()

        fun add(
            kind: WatcherKind,
            pkg: String,
            component: String? = null,
            user: Int = userId,
            detail: String? = null,
            active: Boolean = true,
        ) {
            entries += WatcherEntry(kind, pkg, component, user, detail, active)
        }

        fun <T> secure(
            name: String,
            key: SecureKey,
            parse: (String) -> T,
        ): T? = s.source(name, ReadCommands.secure(key), parse)

        // Notification listeners: approvals are mirrored to Settings.Secure; "live" ones are bound now.
        val approved =
            secure("notification listeners", SecureKey.ENABLED_NOTIFICATION_LISTENERS, SettingsLists::components)
        val live =
            s.source(
                "live notification listeners",
                ReadCommands.notification,
                NotificationDumpParser::liveListeners,
            )
        for (c in approved.orEmpty()) {
            val bound = live?.any { it.first == c } ?: true
            add(WatcherKind.NOTIFICATION_LISTENER, c.packageName, c.short, active = bound)
        }

        val a11yOn = secure("accessibility switch", SecureKey.ACCESSIBILITY_ENABLED, SettingsLists::flag) ?: true
        val a11y = secure("accessibility services", SecureKey.ENABLED_ACCESSIBILITY_SERVICES, SettingsLists::components)
        for (c in a11y.orEmpty()) add(WatcherKind.ACCESSIBILITY_SERVICE, c.packageName, c.short, active = a11yOn)

        val admins = s.source("device admins", ReadCommands.devicePolicy, DevicePolicyParser::parse)
        for (a in admins.orEmpty()) {
            add(WatcherKind.DEVICE_ADMIN, a.component.packageName, a.component.short, a.userId, a.role)
        }

        val current = secure("current keyboard", SecureKey.DEFAULT_INPUT_METHOD, SettingsLists::singleComponent)
        val keyboards = secure("keyboards", SecureKey.ENABLED_INPUT_METHODS, SettingsLists::inputMethods)
        for (c in keyboards.orEmpty()) {
            add(WatcherKind.INPUT_METHOD, c.packageName, c.short, detail = if (c == current) CURRENT else null)
        }

        val usage = s.source("usage access", ReadCommands.appOpsHolders(OP_USAGE), AppOpsParser::parseQueryOp)
        for (pkg in usage.orEmpty()) add(WatcherKind.USAGE_ACCESS, pkg)
        val overlays = s.source("overlays", ReadCommands.appOpsHolders(OP_OVERLAY), AppOpsParser::parseQueryOp)
        for (pkg in overlays.orEmpty()) add(WatcherKind.OVERLAY, pkg)

        val alwaysOn =
            secure("always-on VPN", SecureKey.ALWAYS_ON_VPN_APP) { it.trim().takeUnless(SettingsLists::isUnset) }
        val vpns = s.source("active VPN", ReadCommands.vpnManagement, VpnParser::activeVpnPackages)
        for (pkg in vpns.orEmpty()) add(WatcherKind.VPN, pkg, detail = if (pkg == alwaysOn) ALWAYS_ON else null)
        if (alwaysOn != null && vpns.orEmpty().none { it == alwaysOn }) {
            add(WatcherKind.VPN, alwaysOn, detail = ALWAYS_ON, active = false)
        }

        for (role in Role.entries) {
            val name = role.name.lowercase()
            val holders = s.source("role $name", ReadCommands.roleHolders(role, userId), RoleHolders::parse)
            for (pkg in holders.orEmpty()) add(WatcherKind.DEFAULT_ROLE, pkg, detail = name)
        }

        val dnd =
            secure(
                "Do Not Disturb access",
                SecureKey.ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES,
                SettingsLists::packages,
            )
        for (pkg in dnd.orEmpty()) add(WatcherKind.DND_ACCESS, pkg)

        commandsRun = s.commandsRun
        return WatchersSnapshot(entries.distinct(), s.statuses)
    }

    companion object {
        const val CURRENT = "current"
        const val ALWAYS_ON = "always-on"
        const val OP_USAGE = "GET_USAGE_STATS"
        const val OP_OVERLAY = "SYSTEM_ALERT_WINDOW"
    }
}

/** Everything about one package that needs shell privileges. */
class AppDetailCollector(
    private val executor: CommandExecutor,
) {
    var commandsRun: Int = 0
        private set

    fun collect(
        packageName: String,
        userId: Int,
    ): AppDetail {
        val s = SourceTracker(executor)
        val services = s.source("services", ReadCommands.activityServices(packageName), ActiveServicesParser::parse)
        val bucket =
            s.source(
                "standby bucket",
                ReadCommands.standbyBucket(packageName, userId),
                StandbyBucketParser::parseSingle,
            )
        val idle = s.source("battery optimisation", ReadCommands.idleWhitelist, DeviceIdleParser::parse)
        val dump =
            s.source(
                "package",
                ReadCommands.packageDump(packageName),
            ) { PackageDumpParser.parse(it, packageName) }
        val ops = s.source("app-ops", ReadCommands.appOps(packageName, userId), AppOpsParser::parseGet)
        commandsRun = s.commandsRun
        return AppDetail(
            packageName = packageName,
            userId = userId,
            uid = dump?.appId?.let { userId * 100_000 + it % 100_000 },
            services = services.orEmpty().filter { it.packageName == packageName && it.userId == userId },
            standbyBucket = bucket,
            idleExemption = idle?.let { it[packageName] ?: IdleExemption.NONE },
            runtimePermissions = dump?.runtimePermissions?.get(userId).orEmpty(),
            appOps = ops.orEmpty(),
            installer = dump?.installer,
            enabledState = dump?.enabledState?.get(userId)?.let(::enabledName),
            sources = s.statuses,
        )
    }

    private fun enabledName(state: Int): String =
        when (state) {
            0 -> "default"
            1 -> "enabled"
            2 -> "disabled"
            3 -> "disabled-user"
            4 -> "disabled-until-used"
            else -> state.toString()
        }
}

/**
 * Who woke the device. [WakeupReport.apps] are totals since the last charge (batterystats); the
 * timeline holds the events Android still remembers with a time: alarm deliveries (App Alarm
 * history, about the last hour, plus the last delivery per alarm tag) and job runs (Job history).
 */
class WakeupsCollector(
    private val executor: CommandExecutor,
    private val nowMs: () -> Long,
) {
    var commandsRun: Int = 0
        private set

    fun collect(windowMs: Long): WakeupReport {
        require(windowMs in MIN_WINDOW_MS..MAX_WINDOW_MS) { "Invalid window: $windowMs" }
        val s = SourceTracker(executor)
        val now = nowMs()
        val from = now - windowMs

        val checkin = s.source("battery statistics", ReadCommands.batteryStatsCheckin, BatteryStatsCheckinParser::parse)
        val alarms = s.source("alarms", ReadCommands.alarm) { AlarmDumpParser.history(it) to AlarmDumpParser.stats(it) }
        val jobs = s.source("jobs", ReadCommands.jobScheduler, JobHistoryParser::parse)
        commandsRun = s.commandsRun

        val uidByPackage = HashMap<Pair<String, Int>, Int>()
        checkin?.packagesByUid?.forEach { (uid, pkgs) -> pkgs.forEach { uidByPackage[it to Uids.userId(uid)] = uid } }
        alarms?.second?.forEach { uidByPackage[it.packageName to Uids.userId(it.uid)] = it.uid }

        val events = mutableListOf<WakeupEvent>()
        alarms?.first?.forEach { h ->
            val uid = uidByPackage[h.packageName to h.userId] ?: return@forEach
            h.relativeMs.forEach { rel ->
                events +=
                    WakeupEvent(WakeupKind.ALARM, uid, h.packageName, ALARM_TAG, now + rel)
            }
        }
        alarms?.second?.forEach { stat ->
            stat.tags.filter { it.wakeups > 0 && it.lastRelativeMs != null }.forEach { tag ->
                val at = now + tag.lastRelativeMs!!
                val duplicate =
                    events.any {
                        it.uid == stat.uid && it.kind == WakeupKind.ALARM &&
                            kotlin.math.abs(it.atMs - at) < DEDUP_MS
                    }
                if (!duplicate) events += WakeupEvent(WakeupKind.ALARM, stat.uid, stat.packageName, tag.tag, at)
            }
        }
        jobs?.let { list ->
            list.filter { it.start }.forEach { start ->
                val stop =
                    list.firstOrNull {
                        !it.start && it.uid == start.uid && it.jobId == start.jobId &&
                            it.relativeMs >= start.relativeMs
                    }
                events +=
                    WakeupEvent(
                        kind = WakeupKind.JOB,
                        uid = start.uid,
                        packageName = start.component.substringBefore('/'),
                        tag = start.component,
                        atMs = now + start.relativeMs,
                        durationMs = stop?.let { it.relativeMs - start.relativeMs },
                    )
            }
        }

        val apps =
            checkin
                ?.totals
                ?.map { (uid, t) ->
                    AppWakeups(
                        uid,
                        checkin.packagesByUid[uid]?.firstOrNull(),
                        t.alarmWakeups,
                        t.wakelockCount,
                        t.wakelockMs,
                        t.jobCount,
                        t.jobMs,
                    )
                }
                ?: alarms?.second?.map { AppWakeups(it.uid, it.packageName, alarmWakeups = it.wakeups) }.orEmpty()

        return WakeupReport(
            windowMs = windowMs,
            untilMs = now,
            apps = apps.filter { it.total > 0 }.sortedByDescending { it.total },
            events = events.filter { it.atMs in from..now }.sortedByDescending { it.atMs }.take(MAX_EVENTS),
            sources = s.statuses,
            totalsSinceMs = checkin?.startClockMs,
        )
    }

    companion object {
        const val MIN_WINDOW_MS = 60_000L
        const val MAX_WINDOW_MS = 7 * 24 * 3_600_000L
        const val MAX_EVENTS = 2_000
        const val ALARM_TAG = "wakeup alarm"
        private const val DEDUP_MS = 1_000L
    }
}
