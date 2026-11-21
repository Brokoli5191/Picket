package app.brokoli5191.picket.privileged.service

import android.content.Context
import android.os.Binder
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import app.brokoli5191.picket.core.collect.AppDetailCollector
import app.brokoli5191.picket.core.collect.WakeupsCollector
import app.brokoli5191.picket.core.collect.WatchersCollector
import app.brokoli5191.picket.core.command.CommandExecutor
import app.brokoli5191.picket.core.command.CommandOutput
import app.brokoli5191.picket.core.command.ReadCommands
import app.brokoli5191.picket.core.command.ShellCommand
import app.brokoli5191.picket.core.ipc.CallCost
import app.brokoli5191.picket.core.ipc.PayloadCodec
import app.brokoli5191.picket.core.ipc.Reply
import app.brokoli5191.picket.core.ipc.ServiceError
import app.brokoli5191.picket.core.model.AppDetail
import app.brokoli5191.picket.core.model.ProcessSnapshot
import app.brokoli5191.picket.core.model.WakeupReport
import app.brokoli5191.picket.core.model.WatchersSnapshot
import app.brokoli5191.picket.core.parse.MeminfoParser
import app.brokoli5191.picket.core.proc.ProcessSnapshotReader
import app.brokoli5191.picket.core.validation.Validators
import app.brokoli5191.picket.privileged.IPicketService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import kotlin.system.exitProcess

/**
 * The privileged half of Picket. Shizuku starts it in its own process
 * (`app.brokoli5191.picket:privileged`) running as the shell user, or as root with Sui, and hands
 * the binder to the app.
 *
 * Rules: typed operations only, every argument validated here, commands as argv arrays, and only
 * Picket's own uid may call in.
 */
class PicketUserService(
    context: Context,
) : IPicketService.Stub() {
    /** uid of the Picket app that owns this service (package context created by Shizuku). */
    private val appUid: Int = context.applicationInfo.uid

    private val lock = Any()

    private val processReader =
        ProcessSnapshotReader(
            fs = AndroidProcFs(),
            clockTicksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK).toInt(),
            pageSizeBytes = Os.sysconf(OsConstants._SC_PAGESIZE).toInt(),
            elapsedRealtimeMs = SystemClock::elapsedRealtime,
        )

    override fun destroy() {
        enforceCaller()
        Log.i(TAG, "destroy() requested, exiting")
        exitProcess(0)
    }

    override fun protocolVersion(): Int {
        enforceCaller()
        return PayloadCodec.PROTOCOL_VERSION
    }

    override fun uid(): Int {
        enforceCaller()
        return Process.myUid()
    }

    override fun seLinuxContext(): String {
        enforceCaller()
        return runCatching { File("/proc/self/attr/current").readText().trim('\u0000', '\n', ' ') }.getOrDefault("")
    }

    private val runner = CommandRunner()

    /** Executes the fixed read commands of the :core collectors; counts them for the debug overlay. */
    private inner class Executor(
        private val scope: CallScope,
    ) : CommandExecutor {
        override fun run(command: ShellCommand): CommandOutput {
            scope.commandsRun++
            val result = runner.run(command)
            return CommandOutput(result.exitCode, result.stdout, result.stderr, result.timedOut)
        }
    }

    override fun listProcesses(): ByteArray =
        call(ProcessSnapshot.serializer()) {
            val snapshot = processReader.snapshot()
            filesRead = processReader.lastFilesRead
            snapshot
        }

    override fun listWatchers(userId: Int): ByteArray =
        call(WatchersSnapshot.serializer()) {
            WatchersCollector(Executor(this), Validators.requireUserId(userId)).collect()
        }

    override fun appDetail(
        packageName: String?,
        userId: Int,
    ): ByteArray =
        call(AppDetail.serializer()) {
            val pkg = Validators.requirePackageName(packageName.orEmpty())
            AppDetailCollector(Executor(this)).collect(pkg, Validators.requireUserId(userId))
        }

    override fun pss(pid: Int): ByteArray =
        call(Long.serializer()) {
            val command = ReadCommands.meminfo(pid)
            commandsRun++
            val result = runner.run(command)
            MeminfoParser.totalPssKb(result.stdout) ?: throw IllegalStateException("No memory info for pid $pid")
        }

    override fun wakeups(windowMs: Long): ByteArray =
        call(WakeupReport.serializer()) {
            WakeupsCollector(Executor(this), System::currentTimeMillis).collect(windowMs)
        }

    /** Mutable cost counters an operation fills in while it runs. */
    private class CallScope {
        var filesRead = 0
        var commandsRun = 0
    }

    /**
     * Runs one operation: caller check, serialisation (one operation at a time), timing, and
     * translation of every failure into a typed [ServiceError] so nothing unexpected crosses the binder.
     */
    private fun <T> call(
        serializer: KSerializer<T>,
        block: CallScope.() -> T,
    ): ByteArray {
        enforceCaller()
        val scope = CallScope()
        val wallStart = SystemClock.elapsedRealtimeNanos()
        val cpuStart = Debug.threadCpuTimeNanos()
        val reply: Reply<T> =
            try {
                Reply.ok(synchronized(lock) { scope.block() })
            } catch (e: IllegalArgumentException) {
                Reply.failed(ServiceError(ServiceError.Kind.INVALID_ARGUMENT, e.message.orEmpty()))
            } catch (e: SecurityException) {
                Reply.failed(ServiceError(ServiceError.Kind.NOT_PERMITTED, e.message.orEmpty()))
            } catch (e: UnsupportedOperationException) {
                Reply.failed(ServiceError(ServiceError.Kind.UNSUPPORTED, e.message.orEmpty()))
            } catch (e: Exception) {
                Log.w(TAG, "Operation failed", e)
                Reply.failed(ServiceError(ServiceError.Kind.INTERNAL, "${e.javaClass.simpleName}: ${e.message}"))
            }
        val cost =
            CallCost(
                wallMicros = (SystemClock.elapsedRealtimeNanos() - wallStart) / 1_000,
                cpuMicros = (Debug.threadCpuTimeNanos() - cpuStart).coerceAtLeast(0) / 1_000,
                filesRead = scope.filesRead,
                commandsRun = scope.commandsRun,
            )
        return PayloadCodec.encodeReply(serializer, reply.copy(cost = cost))
    }

    private fun enforceCaller() {
        val caller = Binder.getCallingUid()
        if (caller != appUid && caller != Process.myUid()) {
            throw SecurityException("uid $caller may not use Picket's privileged service")
        }
    }

    companion object {
        private const val TAG = "PicketUserService"
    }
}
