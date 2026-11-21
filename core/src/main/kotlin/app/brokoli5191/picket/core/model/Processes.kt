package app.brokoli5191.picket.core.model

import kotlinx.serialization.Serializable

/** One sample of every process on the device, read from /proc by the privileged service. */
@Serializable
data class ProcessSnapshot(
    /** `SystemClock.elapsedRealtime()` in the service when the sample was taken. */
    val elapsedRealtimeMs: Long,
    /** Seconds since boot from /proc/uptime, as milliseconds. */
    val uptimeMs: Long,
    val cpu: CpuTimes,
    /** `sysconf(_SC_CLK_TCK)`, the unit of all tick fields. 100 on every Android device seen so far. */
    val clockTicksPerSecond: Int,
    val pageSizeBytes: Int,
    val processes: List<ProcessSample>,
)

/** Aggregate "cpu" line of /proc/stat, in clock ticks. */
@Serializable
data class CpuTimes(
    /** user + nice + system + idle + iowait + irq + softirq + steal (guest is already part of user). */
    val totalTicks: Long,
    /** idle + iowait. */
    val idleTicks: Long,
    val onlineCpus: Int,
)

@Serializable
data class ProcessSample(
    val pid: Int,
    val ppid: Int,
    val uid: Int,
    /** Process name as the app sees it (`/proc/<pid>/cmdline` argv[0]), falls back to [comm]. */
    val name: String,
    /** Kernel task name from /proc/<pid>/stat, at most 15 characters. */
    val comm: String,
    val state: Char,
    val userTicks: Long,
    val systemTicks: Long,
    /** Start time after boot in clock ticks; together with [pid] it identifies a process instance. */
    val startTicks: Long,
    val rssPages: Long,
    val threads: Int,
    /** `/proc/<pid>/oom_score_adj`, null when unreadable. */
    val oomScoreAdj: Int? = null,
) {
    val cpuTicks: Long get() = userTicks + systemTicks

    fun rssBytes(pageSizeBytes: Int): Long = rssPages * pageSizeBytes

    fun uptimeMs(
        snapshotUptimeMs: Long,
        clockTicksPerSecond: Int,
    ): Long = (snapshotUptimeMs - startTicks * 1000 / clockTicksPerSecond).coerceAtLeast(0)
}
