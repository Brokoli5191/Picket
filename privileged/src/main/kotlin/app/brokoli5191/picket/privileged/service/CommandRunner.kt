package app.brokoli5191.picket.privileged.service

import android.os.SystemClock
import app.brokoli5191.picket.core.command.ShellCommand
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Output of one finished (or killed) command. */
internal data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val truncated: Boolean,
    val durationMs: Long,
) {
    val succeeded: Boolean get() = exitCode == 0 && !timedOut
}

/**
 * Runs [ShellCommand]s with ProcessBuilder (argv, no shell, no stdin), with a timeout and a cap on
 * the captured output. Lives in the privileged process.
 */
internal class CommandRunner(
    private val defaultTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxStdoutBytes: Int = MAX_STDOUT_BYTES,
) {
    private val pool =
        Executors.newCachedThreadPool(
            ThreadFactory { runnable -> Thread(runnable, "picket-command-io").apply { isDaemon = true } },
        )

    fun run(
        command: ShellCommand,
        timeoutMs: Long = defaultTimeoutMs,
    ): CommandResult {
        val started = SystemClock.elapsedRealtime()
        val process = ProcessBuilder(command.argv).redirectErrorStream(false).start()
        process.outputStream.close()

        val stderr = pool.submit<Capture> { capture(process.errorStream, MAX_STDERR_BYTES) }
        val killedByWatchdog = AtomicBoolean(false)
        val watchdog =
            pool.submit {
                if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    killedByWatchdog.set(true)
                    process.destroyForcibly()
                }
            }
        val stdout = capture(process.inputStream, maxStdoutBytes)
        if (stdout.truncated) process.destroyForcibly()
        if (!process.waitFor(EXIT_GRACE_MS, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        watchdog.cancel(true)
        val err = runCatching { stderr.get(EXIT_GRACE_MS, TimeUnit.MILLISECONDS) }.getOrDefault(Capture("", false))
        return CommandResult(
            exitCode = if (process.isAlive) -1 else process.exitValue(),
            stdout = stdout.text,
            stderr = err.text,
            timedOut = killedByWatchdog.get(),
            truncated = stdout.truncated,
            durationMs = SystemClock.elapsedRealtime() - started,
        )
    }

    private data class Capture(
        val text: String,
        val truncated: Boolean,
    )

    private fun capture(
        input: InputStream,
        limit: Int,
    ): Capture {
        val out = ByteArrayOutputStream(minOf(limit, INITIAL_BUFFER))
        val buffer = ByteArray(INITIAL_BUFFER)
        var truncated = false
        input.use { stream ->
            while (true) {
                val read =
                    try {
                        stream.read(buffer)
                    } catch (_: java.io.IOException) {
                        -1 // stream closed because the process was destroyed
                    }
                if (read < 0) break
                val room = limit - out.size()
                if (read > room) {
                    out.write(buffer, 0, room)
                    truncated = true
                    break
                }
                out.write(buffer, 0, read)
            }
        }
        return Capture(out.toString(Charsets.UTF_8.name()), truncated)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        const val MAX_STDOUT_BYTES = 24 * 1024 * 1024
        private const val MAX_STDERR_BYTES = 64 * 1024
        private const val EXIT_GRACE_MS = 2_000L
        private const val INITIAL_BUFFER = 64 * 1024
    }
}
