package app.brokoli5191.picket.core.parse

import app.brokoli5191.picket.core.process.Uids

/** `u0a123` → 10123, `u10a5` → 1010005, `1000` → 1000 (the uid names dumpsys prints). */
object UidNames {
    private val app = Regex("^u(\\d+)a(\\d+)$")
    private val isolated = Regex("^u(\\d+)i(\\d+)$")

    fun parse(name: String): Int? {
        name.toIntOrNull()?.let { return it }
        app.matchEntire(name)?.let { m ->
            return m.groupValues[1].toInt() * Uids.PER_USER_RANGE + Uids.FIRST_APPLICATION + m.groupValues[2].toInt()
        }
        isolated.matchEntire(name)?.let { m ->
            return m.groupValues[1].toInt() * Uids.PER_USER_RANGE + Uids.FIRST_ISOLATED + m.groupValues[2].toInt()
        }
        return null
    }
}

/** `dumpsys alarm` (AlarmManagerService#dumpImpl). */
object AlarmDumpParser {
    data class History(
        val packageName: String,
        val userId: Int,
        /** Delivery times relative to the dump, negative milliseconds. */
        val relativeMs: List<Long>,
    )

    data class TagStat(
        val tag: String,
        val wakeups: Int,
        val alarms: Int,
        val lastRelativeMs: Long?,
    )

    data class PackageStat(
        val uid: Int,
        val packageName: String,
        val wakeups: Int,
        val tags: List<TagStat>,
    )

    private val historyLine = Regex("^\\s+([A-Za-z0-9_.]+), u(\\d+): (.*)$")
    private val statHeader = Regex("^\\s+([0-9a-z]+):([A-Za-z0-9_.]+) \\S+ running, (\\d+) wakeups:\\s*$")
    private val tagHeader = Regex("^\\s+\\S+ (\\d+) wakes (\\d+) alarms, last (\\S+?):\\s*$")

    /** "App Alarm history:" (formerly "App Wakeup history"): recent wakeup deliveries per package. */
    fun history(text: String): List<History> =
        section(text, setOf("App Alarm history:", "App Wakeup history:")).mapNotNull { line ->
            val m = historyLine.matchEntire(line) ?: return@mapNotNull null
            val times = m.groupValues[3].split(',').mapNotNull { Durations.parse(it.trim()) }
            History(m.groupValues[1], m.groupValues[2].toInt(), times)
        }

    /** "Alarm Stats:": totals since boot per package and tag, with the last delivery of each tag. */
    fun stats(text: String): List<PackageStat> {
        val result = mutableListOf<PackageStat>()
        var current: PackageStat? = null
        var pending: Triple<Int, Int, Long?>? = null
        for (line in section(text, setOf("Alarm Stats:"), siblingsIncluded = true)) {
            val header = statHeader.matchEntire(line)
            if (header != null) {
                current?.let { result += it }
                val uid = UidNames.parse(header.groupValues[1]) ?: continue
                current = PackageStat(uid, header.groupValues[2], header.groupValues[3].toInt(), emptyList())
                pending = null
                continue
            }
            val tag = tagHeader.matchEntire(line)
            if (tag != null) {
                pending =
                    Triple(tag.groupValues[1].toInt(), tag.groupValues[2].toInt(), Durations.parse(tag.groupValues[3]))
                continue
            }
            val p = pending
            val c = current
            if (p != null && c != null && line.isNotBlank()) {
                current = c.copy(tags = c.tags + TagStat(line.trim(), p.first, p.second, p.third))
                pending = null
            }
        }
        current?.let { result += it }
        return result
    }

    /**
     * Lines below one of [headers] until a blank line or, unless [siblingsIncluded] (Alarm Stats prints
     * its entries at the header's own indentation), a line indented as far or less than the header.
     */
    private fun section(
        text: String,
        headers: Set<String>,
        siblingsIncluded: Boolean = false,
    ): List<String> {
        val lines = mutableListOf<String>()
        var indent = -1
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            val lineIndent = line.length - line.trimStart().length
            if (indent < 0) {
                if (trimmed in headers) indent = lineIndent
                continue
            }
            if (trimmed.isEmpty()) {
                if (lines.isNotEmpty()) break
                continue
            }
            if (!siblingsIncluded && lineIndent <= indent) break
            lines += line
        }
        return lines
    }
}

/** "Job history:" of `dumpsys jobscheduler`: `-1h2m3s START-P: #u0a123/7 pkg/cls [reason]`. */
object JobHistoryParser {
    data class Event(
        val relativeMs: Long,
        val start: Boolean,
        val uid: Int,
        val jobId: Int,
        val component: String,
    )

    private val line = Regex("^\\s*(\\S+)\\s+(START|STOP)(?:-P)?: #([0-9a-z]+)/(-?\\d+) (\\S+)")

    fun parse(text: String): List<Event> {
        val events = mutableListOf<Event>()
        var inHistory = false
        for (raw in text.lineSequence()) {
            if (raw.trim() == "Job history:") {
                inHistory = true
                continue
            }
            if (!inHistory) continue
            if (raw.isBlank()) break
            val m = line.find(raw) ?: continue
            val rel = Durations.parse(m.groupValues[1]) ?: continue
            val uid = UidNames.parse(m.groupValues[3]) ?: continue
            events += Event(rel, m.groupValues[2] == "START", uid, m.groupValues[4].toInt(), m.groupValues[5])
        }
        return events
    }
}

/** `dumpsys batterystats --checkin`: totals since the last charge (BatteryStats#dumpCheckinLocked). */
object BatteryStatsCheckinParser {
    data class UidTotals(
        val wakelockMs: Long = 0,
        val wakelockCount: Int = 0,
        val jobMs: Long = 0,
        val jobCount: Int = 0,
        val alarmWakeups: Int = 0,
    )

    data class Checkin(
        /** Wall-clock time the statistics started (last charge), if printed. */
        val startClockMs: Long?,
        val packagesByUid: Map<Int, List<String>>,
        val totals: Map<Int, UidTotals>,
    )

    /** Fields after a wakelock name: four groups (full, partial, background partial, window) of six. */
    private const val WAKELOCK_TAIL = 24

    fun parse(text: String): Checkin {
        var start: Long? = null
        val packages = LinkedHashMap<Int, MutableList<String>>()
        val totals = HashMap<Int, UidTotals>()
        for (line in text.lineSequence()) {
            val f = splitCsv(line.trim())
            if (f.size < 4) continue
            val uid = f[1].toIntOrNull() ?: continue
            when (f[3]) {
                "uid" -> {
                    if (f[2] == "i" &&
                        f.size >= 6
                    ) {
                        packages.getOrPut(f[4].toIntOrNull() ?: continue) { mutableListOf() } += f[5]
                    }
                }

                "bt" -> {
                    if (f[2] == "l") start = f.getOrNull(9)?.toLongOrNull()
                }

                "wl" -> {
                    if (f[2] != "l" || f.size < 5 + WAKELOCK_TAIL) continue
                    val tail = f.subList(f.size - WAKELOCK_TAIL, f.size)
                    if (tail[7] != "p") continue
                    val ms = tail[6].toLongOrNull() ?: continue
                    val count = tail[8].toIntOrNull() ?: continue
                    totals.merge(uid, UidTotals(wakelockMs = ms, wakelockCount = count), ::add)
                }

                "jb" -> {
                    if (f[2] != "l" || f.size < 7) continue
                    val ms = f[5].toLongOrNull() ?: continue
                    val count = f[6].toIntOrNull() ?: continue
                    totals.merge(uid, UidTotals(jobMs = ms, jobCount = count), ::add)
                }

                "wua" -> {
                    if (f[2] != "l" || f.size < 6) continue
                    val count = f.last().toIntOrNull() ?: continue
                    totals.merge(uid, UidTotals(alarmWakeups = count), ::add)
                }
            }
        }
        return Checkin(start, packages, totals)
    }

    private fun add(
        a: UidTotals,
        b: UidTotals,
    ) = UidTotals(
        wakelockMs = a.wakelockMs + b.wakelockMs,
        wakelockCount = a.wakelockCount + b.wakelockCount,
        jobMs = a.jobMs + b.jobMs,
        jobCount = a.jobCount + b.jobCount,
        alarmWakeups = a.alarmWakeups + b.alarmWakeups,
    )

    /** Splits on commas outside double quotes and removes the quotes (job and process names are quoted). */
    internal fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        for (c in line) {
            when {
                c == '"' -> {
                    quoted = !quoted
                }

                c == ',' && !quoted -> {
                    out += cur.toString()
                    cur.setLength(0)
                }

                else -> {
                    cur.append(c)
                }
            }
        }
        out += cur.toString()
        return out
    }
}
