package app.brokoli5191.picket.data

import app.brokoli5191.picket.core.ipc.ServiceError
import app.brokoli5191.picket.core.model.ProcessSnapshot
import app.brokoli5191.picket.core.process.CpuUsage
import app.brokoli5191.picket.privileged.client.CallTiming
import app.brokoli5191.picket.privileged.client.PrivilegedClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One tick of the process sampler. */
data class ProcessFrame(
    val snapshot: ProcessSnapshot?,
    val cpu: CpuUsage,
    val error: ServiceError?,
    val timing: CallTiming?,
)

/**
 * Polls the privileged service. Sampling happens in the service (one /proc pass per tick); the app
 * only diffs consecutive snapshots for CPU usage. Runs only while collected.
 */
class ProcessSampler(
    private val client: PrivilegedClient,
) {
    fun frames(intervalMs: () -> Long): Flow<ProcessFrame> =
        flow {
            var previous: ProcessSnapshot? = null
            while (true) {
                val reply = client.listProcesses()
                val snapshot = reply.value
                if (snapshot != null) {
                    emit(ProcessFrame(snapshot, CpuUsage.between(previous, snapshot), null, client.lastCall.value))
                    previous = snapshot
                } else {
                    emit(ProcessFrame(previous, CpuUsage.NONE, reply.error, client.lastCall.value))
                }
                delay(intervalMs().coerceAtLeast(MIN_INTERVAL_MS))
            }
        }

    companion object {
        const val DEFAULT_INTERVAL_MS = 2_000L
        const val MIN_INTERVAL_MS = 500L
    }
}
