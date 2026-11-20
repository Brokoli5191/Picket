package app.brokoli5191.picket.core

import app.brokoli5191.picket.core.command.CommandExecutor
import app.brokoli5191.picket.core.command.CommandOutput
import app.brokoli5191.picket.core.command.ShellCommand
import java.io.File

/**
 * Answers commands from a fixture directory. File name = argv without `/system/bin/`, joined with
 * `_` (`/` → `%`), exactly as scripts/capture-fixtures.sh names its recordings, so a directory
 * recorded on a device can be used unchanged. Missing files behave like a failing command.
 */
class FixtureExecutor(
    private val dir: File,
) : CommandExecutor {
    val executed = mutableListOf<ShellCommand>()

    override fun run(command: ShellCommand): CommandOutput {
        executed += command
        val file = File(dir, nameOf(command))
        return if (file.isFile) {
            CommandOutput(0, file.readText(Charsets.UTF_8))
        } else {
            CommandOutput(1, "", "fixture missing: ${file.name}")
        }
    }

    companion object {
        fun nameOf(command: ShellCommand): String =
            command.argv
                .mapIndexed { i, arg -> if (i == 0) arg.removePrefix(ShellCommand.BIN) else arg }
                .joinToString("_")
                .replace('/', '%') + ".txt"
    }
}
