package app.brokoli5191.picket.core.parse.proc

import app.brokoli5191.picket.core.model.CpuTimes

/** Fields of `/proc/<pid>/stat` that Picket uses. See proc(5). */
data class ProcStat(
    val pid: Int,
    val comm: String,
    val state: Char,
    val ppid: Int,
    val userTicks: Long,
    val systemTicks: Long,
    val threads: Int,
    val startTicks: Long,
    val rssPages: Long,
)

/** Parsers for the kernel's /proc text formats. Pure functions; null means "not parseable". */
object ProcParsers {
    // Field positions after the closing parenthesis of comm (0 = state, i.e. proc(5) field 3).
    private const val F_STATE = 0
    private const val F_PPID = 1
    private const val F_UTIME = 11
    private const val F_STIME = 12
    private const val F_THREADS = 17
    private const val F_STARTTIME = 19
    private const val F_RSS = 21

    /**
     * Parses `/proc/<pid>/stat`. comm may contain spaces and parentheses, so it spans from the first
     * '(' to the *last* ')'.
     */
    fun parseStat(text: String): ProcStat? {
        val open = text.indexOf('(')
        val close = text.lastIndexOf(')')
        if (open <= 0 || close <= open) return null
        val pid = text.substring(0, open).trim().toIntOrNull() ?: return null
        val comm = text.substring(open + 1, close)
        val fields = text.substring(close + 1).trim().split(' ')
        if (fields.size <= F_RSS) return null
        val state = fields[F_STATE].singleOrNull() ?: return null
        return ProcStat(
            pid = pid,
            comm = comm,
            state = state,
            ppid = fields[F_PPID].toIntOrNull() ?: return null,
            userTicks = fields[F_UTIME].toLongOrNull() ?: return null,
            systemTicks = fields[F_STIME].toLongOrNull() ?: return null,
            threads = fields[F_THREADS].toIntOrNull() ?: return null,
            startTicks = fields[F_STARTTIME].toLongOrNull() ?: return null,
            rssPages = fields[F_RSS].toLongOrNull() ?: return null,
        )
    }

    /** Real uid from the `Uid:` line of `/proc/<pid>/status` (real, effective, saved, fs). */
    fun parseStatusUid(text: String): Int? =
        text
            .lineSequence()
            .firstOrNull { it.startsWith("Uid:") }
            ?.substringAfter(':')
            ?.trim()
            ?.split('\t', ' ')
            ?.firstOrNull { it.isNotEmpty() }
            ?.toIntOrNull()

    /** `VmRSS:` of `/proc/<pid>/status` in kB; absent for kernel threads. */
    fun parseStatusRssKb(text: String): Long? =
        text
            .lineSequence()
            .firstOrNull { it.startsWith("VmRSS:") }
            ?.substringAfter(':')
            ?.trim()
            ?.removeSuffix("kB")
            ?.trim()
            ?.toLongOrNull()

    /**
     * argv[0] of `/proc/<pid>/cmdline` (NUL separated). Android sets it to the process name,
     * e.g. `com.example:remote`. Empty for kernel threads and zombies.
     */
    fun parseCmdline(bytes: ByteArray): String? {
        val end = bytes.indexOf(0.toByte()).let { if (it < 0) bytes.size else it }
        if (end == 0) return null
        val first = String(bytes, 0, end, Charsets.UTF_8).trim()
        return first.ifEmpty { null }
    }

    fun parseOomScoreAdj(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in -1000..1000 }

    /**
     * The aggregate `cpu ` line of `/proc/stat`. guest and guest_nice are already included in user
     * and nice, so they are not added again.
     */
    fun parseCpuTimes(text: String): CpuTimes? {
        var total: Long? = null
        var idle = 0L
        var online = 0
        for (line in text.lineSequence()) {
            if (line.startsWith("cpu ")) {
                val v =
                    line
                        .substring(4)
                        .trim()
                        .split(Regex("\\s+"))
                        .map { it.toLongOrNull() ?: return null }
                if (v.size < 4) return null
                total = v.take(8).sum()
                idle = v[3] + (v.getOrNull(4) ?: 0L)
            } else if (line.startsWith("cpu") && line.length > 3 && line[3].isDigit()) {
                online++
            }
        }
        return total?.let { CpuTimes(totalTicks = it, idleTicks = idle, onlineCpus = online) }
    }

    /** First field of `/proc/uptime`, in milliseconds. */
    fun parseUptimeMs(text: String): Long? {
        val seconds = text.trim().substringBefore(' ').toBigDecimalOrNull() ?: return null
        return seconds.movePointRight(3).toLong()
    }
}
