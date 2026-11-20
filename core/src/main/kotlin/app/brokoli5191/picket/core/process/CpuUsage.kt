package app.brokoli5191.picket.core.process

import app.brokoli5191.picket.core.model.ProcessSample
import app.brokoli5191.picket.core.model.ProcessSnapshot

/** A process instance: pids are recycled, the start time tells two holders of one pid apart. */
data class ProcessKey(
    val pid: Int,
    val startTicks: Long,
)

val ProcessSample.key: ProcessKey get() = ProcessKey(pid, startTicks)

/**
 * CPU usage between two snapshots, as a share of the whole machine: 100 % means every online core
 * was busy for the entire interval (Sysinternals convention, unlike `top`, where one busy core is 100 %).
 */
class CpuUsage private constructor(
    private val byProcess: Map<ProcessKey, Double>,
    /** Busy share of all cores, 0..100. */
    val totalPercent: Double,
    val intervalMs: Long,
) {
    operator fun get(key: ProcessKey): Double? = byProcess[key]

    fun percentOf(sample: ProcessSample): Double = byProcess[sample.key] ?: 0.0

    companion object {
        val NONE = CpuUsage(emptyMap(), 0.0, 0)

        fun between(
            previous: ProcessSnapshot?,
            current: ProcessSnapshot,
        ): CpuUsage {
            if (previous == null) return NONE
            val totalDelta = current.cpu.totalTicks - previous.cpu.totalTicks
            if (totalDelta <= 0) return NONE
            val idleDelta = (current.cpu.idleTicks - previous.cpu.idleTicks).coerceIn(0, totalDelta)
            val before = previous.processes.associateBy { it.key }
            val result = HashMap<ProcessKey, Double>(current.processes.size * 2)
            for (sample in current.processes) {
                val old = before[sample.key] ?: continue
                val delta = sample.cpuTicks - old.cpuTicks
                if (delta > 0) result[sample.key] = (100.0 * delta / totalDelta).coerceAtMost(100.0)
            }
            return CpuUsage(
                byProcess = result,
                totalPercent = 100.0 * (totalDelta - idleDelta) / totalDelta,
                intervalMs = (current.elapsedRealtimeMs - previous.elapsedRealtimeMs).coerceAtLeast(0),
            )
        }
    }
}
