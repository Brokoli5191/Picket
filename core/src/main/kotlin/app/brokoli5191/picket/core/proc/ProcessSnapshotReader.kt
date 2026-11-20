package app.brokoli5191.picket.core.proc

import app.brokoli5191.picket.core.model.ProcessSample
import app.brokoli5191.picket.core.model.ProcessSnapshot
import app.brokoli5191.picket.core.parse.proc.ProcParsers

/** Minimal file access to /proc, so the snapshot logic can run against fixtures in tests. */
interface ProcFs {
    /** Numeric entries of /proc, i.e. the pids that exist right now. */
    fun pids(): IntArray

    /** Whole file content, or null if it vanished or cannot be read (permission, SELinux). */
    fun read(path: String): ByteArray?
}

/**
 * Builds [ProcessSnapshot]s from /proc. Keeps a small cache so that per-process files that do not
 * change for the life of a process (cmdline, uid) are not read on every sample.
 *
 * Not thread-safe; the privileged service serialises calls.
 */
class ProcessSnapshotReader(
    private val fs: ProcFs,
    private val clockTicksPerSecond: Int,
    private val pageSizeBytes: Int,
    private val elapsedRealtimeMs: () -> Long,
    /** Re-read cached identity of every process after this many samples (catches late renames). */
    private val identityRefreshInterval: Int = REFRESH_INTERVAL,
) {
    private data class Identity(
        val startTicks: Long,
        val uid: Int,
        val name: String?,
        val readAtSample: Long,
    )

    private val identities = HashMap<Int, Identity>()
    private var sampleCount = 0L

    /** Number of files read during the last [snapshot] call (shown in the debug overlay). */
    var lastFilesRead: Int = 0
        private set

    fun snapshot(): ProcessSnapshot {
        sampleCount++
        var filesRead = 0

        fun readText(path: String): String? = fs.read(path)?.also { filesRead++ }?.toString(Charsets.UTF_8)

        val cpu = readText("/proc/stat")?.let(ProcParsers::parseCpuTimes) ?: error("/proc/stat unreadable")
        val uptimeMs = readText("/proc/uptime")?.let(ProcParsers::parseUptimeMs) ?: error("/proc/uptime unreadable")

        val pids = fs.pids()
        val samples = ArrayList<ProcessSample>(pids.size)
        val seen = HashSet<Int>(pids.size * 2)
        for (pid in pids) {
            val stat = readText("/proc/$pid/stat")?.let(ProcParsers::parseStat) ?: continue
            seen += pid
            val identity = identityOf(pid, stat.startTicks) { path -> fs.read(path)?.also { filesRead++ } }
            val oom = readText("/proc/$pid/oom_score_adj")?.let(ProcParsers::parseOomScoreAdj)
            samples +=
                ProcessSample(
                    pid = pid,
                    ppid = stat.ppid,
                    uid = identity?.uid ?: UNKNOWN_UID,
                    name = identity?.name ?: stat.comm,
                    comm = stat.comm,
                    state = stat.state,
                    userTicks = stat.userTicks,
                    systemTicks = stat.systemTicks,
                    startTicks = stat.startTicks,
                    rssPages = stat.rssPages,
                    threads = stat.threads,
                    oomScoreAdj = oom,
                )
        }
        identities.keys.retainAll(seen)
        lastFilesRead = filesRead

        return ProcessSnapshot(
            elapsedRealtimeMs = elapsedRealtimeMs(),
            uptimeMs = uptimeMs,
            cpu = cpu,
            clockTicksPerSecond = clockTicksPerSecond,
            pageSizeBytes = pageSizeBytes,
            processes = samples,
        )
    }

    private inline fun identityOf(
        pid: Int,
        startTicks: Long,
        read: (String) -> ByteArray?,
    ): Identity? {
        val cached = identities[pid]
        val fresh =
            cached != null &&
                cached.startTicks == startTicks &&
                sampleCount - cached.readAtSample < identityRefreshInterval
        if (fresh) return cached
        val uid =
            read("/proc/$pid/status")?.toString(Charsets.UTF_8)?.let(ProcParsers::parseStatusUid)
                ?: return null.also { identities.remove(pid) }
        val name = read("/proc/$pid/cmdline")?.let(ProcParsers::parseCmdline)
        return Identity(startTicks, uid, name, sampleCount).also { identities[pid] = it }
    }

    companion object {
        const val UNKNOWN_UID = -1
        const val REFRESH_INTERVAL = 15
    }
}
