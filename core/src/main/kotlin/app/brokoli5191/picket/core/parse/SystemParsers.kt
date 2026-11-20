package app.brokoli5191.picket.core.parse

import app.brokoli5191.picket.core.model.IdleExemption
import app.brokoli5191.picket.core.model.PermissionGrant
import app.brokoli5191.picket.core.model.RunningService
import app.brokoli5191.picket.core.model.StandbyBucket
import app.brokoli5191.picket.core.validation.Validators

/** `cmd deviceidle whitelist`: `system-excidle,pkg,uid`, `system,pkg,uid`, `user,pkg,uid` lines. */
object DeviceIdleParser {
    fun parse(text: String): Map<String, IdleExemption> {
        val result = HashMap<String, IdleExemption>()
        for (line in text.lineSequence()) {
            val parts = line.trim().split(',')
            if (parts.size < 2 || !Validators.isValidPackageName(parts[1])) continue
            val kind =
                if (parts[0] ==
                    "user"
                ) {
                    IdleExemption.USER
                } else if (parts[0].startsWith("system")) {
                    IdleExemption.SYSTEM
                } else {
                    continue
                }
            val previous = result[parts[1]]
            // A system exemption cannot be removed by the user; it wins over a user entry.
            if (previous != IdleExemption.SYSTEM) result[parts[1]] = kind
        }
        return result
    }
}

/** `am get-standby-bucket [--user N] PKG` prints the numeric bucket; `get-standby-buckets` prints `pkg: N`. */
object StandbyBucketParser {
    fun parseSingle(text: String): StandbyBucket? =
        text.lineSequence().firstOrNull { it.isNotBlank() }?.let(StandbyBucket::fromShell)

    fun parseAll(text: String): Map<String, StandbyBucket> =
        text
            .lineSequence()
            .mapNotNull { line ->
                val pkg = line.substringBefore(':', "").trim()
                val bucket = StandbyBucket.fromShell(line.substringAfter(':', ""))
                if (Validators.isValidPackageName(pkg) && bucket != null) pkg to bucket else null
            }.toMap()
}

/** Wake locks held right now, from the "Wake Locks: size=N" section of `dumpsys power`. */
object PowerParser {
    data class HeldWakeLock(
        val type: String,
        val tag: String,
        val ownerUid: Int,
        val pid: Int?,
        /** Uids the work is attributed to (WorkSource), empty if none. */
        val workSourceUids: List<Int>,
        val acquiredAgoMs: Long?,
        val disabled: Boolean,
    )

    private val lock =
        Regex(
            "^\\s*([A-Z_]+_WAKE_LOCK)\\s+'(.*)' (.*)" +
                "\\(uid=(\\d+)(?: pid=(\\d+))?(?: ws=WorkSource\\{([^}]*)})?.*\\)\\s*$",
        )
    private val acquired = Regex("ACQ=(\\S+)")

    fun wakeLocks(text: String): List<HeldWakeLock> {
        val result = mutableListOf<HeldWakeLock>()
        var inSection = false
        for (line in text.lineSequence()) {
            if (line.trimStart().startsWith("Wake Locks: size=")) {
                inSection = true
                continue
            }
            if (!inSection) continue
            if (line.isBlank()) break
            val m = lock.matchEntire(line) ?: continue
            val middle = m.groupValues[3]
            result +=
                HeldWakeLock(
                    type = m.groupValues[1],
                    tag = m.groupValues[2],
                    ownerUid = m.groupValues[4].toInt(),
                    pid = m.groupValues[5].toIntOrNull(),
                    workSourceUids = m.groupValues[6].split(' ', ',').mapNotNull { it.trim().toIntOrNull() },
                    acquiredAgoMs =
                        acquired
                            .find(middle)
                            ?.groupValues
                            ?.get(1)
                            ?.let(Durations::parse)
                            ?.let { -it },
                    disabled = "DISABLED" in middle,
                )
        }
        return result
    }
}

/** The parts of `dumpsys package PKG` Picket shows. */
object PackageDumpParser {
    data class PackageDump(
        val appId: Int?,
        val installer: String?,
        /** Per user `enabled=` state: 0 default, 1 enabled, 2 disabled, 3 disabled-user, 4 until used. */
        val enabledState: Map<Int, Int>,
        val runtimePermissions: Map<Int, List<PermissionGrant>>,
    )

    private val packageHeader = Regex("^\\s*Package \\[([^\\]]+)]")
    private val userLine = Regex("^\\s*User (\\d+):(.*)$")
    private val enabled = Regex("\\benabled=(\\d+)")
    private val permission = Regex("^\\s*([A-Za-z0-9_.]+): granted=(true|false)(?:, flags=\\[\\s*([^\\]]*)])?")

    fun parse(
        text: String,
        packageName: String,
    ): PackageDump? {
        var inPackage = false
        var found = false
        var appId: Int? = null
        var installer: String? = null
        val enabledState = HashMap<Int, Int>()
        val permissions = HashMap<Int, MutableList<PermissionGrant>>()
        var user: Int? = null
        var inRuntime = false
        for (line in text.lineSequence()) {
            packageHeader.find(line)?.let { m ->
                inPackage = m.groupValues[1] == packageName
                found = found || inPackage
                user = null
                inRuntime = false
            }
            if (!inPackage) continue
            val t = line.trim()
            when {
                t.startsWith("appId=") -> {
                    appId = t.removePrefix("appId=").substringBefore(' ').toIntOrNull()
                }

                t.startsWith("userId=") && appId == null -> {
                    appId =
                        t.removePrefix("userId=").substringBefore(' ').toIntOrNull()
                }

                t.startsWith("installerPackageName=") -> {
                    installer =
                        t
                            .removePrefix(
                                "installerPackageName=",
                            ).substringBefore(' ')
                            .takeIf(Validators::isValidPackageName)
                }

                userLine.matches(line) -> {
                    val m = userLine.matchEntire(line)!!
                    val id = m.groupValues[1].toInt()
                    user = id
                    inRuntime = false
                    enabled.find(m.groupValues[2])?.let { enabledState[id] = it.groupValues[1].toInt() }
                }

                t == "runtime permissions:" -> {
                    inRuntime = user != null
                }

                t.endsWith("permissions:") -> {
                    inRuntime = false
                }

                inRuntime -> {
                    val m = permission.find(line) ?: continue
                    permissions.getOrPut(user!!) { mutableListOf() } +=
                        PermissionGrant(
                            name = m.groupValues[1],
                            granted = m.groupValues[2] == "true",
                            flags =
                                m.groupValues[3]
                                    .split('|')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() },
                        )
                }
            }
        }
        if (!found) return null
        return PackageDump(appId, installer, enabledState, permissions)
    }
}

/** `dumpsys activity services`: one block per `* ServiceRecord{hash uN pkg/cls}`. */
object ActiveServicesParser {
    // API 35+ appends " c:<callingPackage>" inside the braces.
    private val header = Regex("^\\s*\\* ServiceRecord\\{[0-9a-f]+ u(\\d+) ([^ }]+)[^}]*}")
    private val app = Regex("app=ProcessRecord\\{[0-9a-f]+ (\\d+):([^/ ]+)/")

    // API 34 prints types=%08X, API 35+ types=0x%08X; older releases print no type at all.
    private val types = Regex("\\btypes=(?:0x)?([0-9a-fA-F]+)")
    private val fgType = Regex("\\bforegroundServiceType=0x([0-9a-fA-F]+)")

    fun parse(text: String): List<RunningService> {
        val services = mutableListOf<RunningService>()
        var current: RunningService? = null
        var blockIndent = -1

        fun flush() {
            current?.let { services += it }
            current = null
        }
        for (line in text.lineSequence()) {
            val indent = line.length - line.trimStart().length
            val h = header.find(line)
            if (h != null) {
                flush()
                val component = Component.parse(h.groupValues[2]) ?: continue
                current =
                    RunningService(
                        component = component.short,
                        packageName = component.packageName,
                        userId = h.groupValues[1].toInt(),
                    )
                blockIndent = indent
                continue
            }
            val s = current ?: continue
            val t = line.trim()
            // API 34+ prints " startCommandResult=" with a single leading space inside the block.
            if (line.isNotBlank() && indent <= blockIndent && !t.startsWith("startCommandResult=")) {
                flush()
                continue
            }
            current =
                when {
                    t.startsWith("processName=") -> {
                        s.copy(processName = t.removePrefix("processName="))
                    }

                    t.startsWith("app=ProcessRecord{") -> {
                        app.find(t)?.let { m ->
                            s.copy(
                                pid = m.groupValues[1].toIntOrNull(),
                                processName =
                                    s.processName ?: m.groupValues[2],
                            )
                        }
                            ?: s
                    }

                    t.startsWith("isForeground=") -> {
                        val fg = t.startsWith("isForeground=true")
                        val bits =
                            (types.find(t) ?: fgType.find(t))
                                ?.groupValues
                                ?.get(1)
                                ?.toLong(16)
                                ?.toInt()
                        s.copy(foreground = fg, foregroundTypes = if (fg) bits else null)
                    }

                    t.startsWith("startRequested=") -> {
                        s.copy(started = t.startsWith("startRequested=true"))
                    }

                    t.startsWith("createTime=") -> {
                        s.copy(
                            createdAgoMs =
                                Durations
                                    .parse(
                                        t.removePrefix("createTime=").substringBefore(' '),
                                    )?.let { -it },
                        )
                    }

                    t.startsWith("lastActivity=") -> {
                        s.copy(
                            lastActivityAgoMs =
                                Durations
                                    .parse(
                                        t.removePrefix("lastActivity=").substringBefore(' '),
                                    )?.let { -it },
                        )
                    }

                    t == "Bindings:" || t.startsWith("All Connections:") || t.startsWith("Connections:") -> {
                        s.copy(bound = true)
                    }

                    else -> {
                        s
                    }
                }
        }
        flush()
        return services
    }
}

/** `dumpsys meminfo --checkin PID` (checkin version 4 on every release): total PSS is field 19, in kB. */
object MeminfoParser {
    private const val TOTAL_PSS = 18

    fun totalPssKb(text: String): Long? =
        text
            .lineSequence()
            .map { it.trim().split(',') }
            .firstOrNull { it.size > TOTAL_PSS && it[0] == "4" }
            ?.get(TOTAL_PSS)
            ?.toLongOrNull()
}
