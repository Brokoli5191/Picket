package app.brokoli5191.picket.core.command

import kotlinx.serialization.Serializable

/**
 * One command line, as an argv array. The privileged service executes these with ProcessBuilder:
 * no shell is involved, so no element can be interpreted as shell syntax.
 *
 * Instances come only from the builders in this package; every variable element has passed
 * [app.brokoli5191.picket.core.validation.Validators] or is a constant.
 */
@Serializable
data class ShellCommand(
    val argv: List<String>,
) {
    init {
        require(argv.isNotEmpty()) { "Empty command" }
        require(argv[0] in ALLOWED_BINARIES) { "Binary not allowed: ${argv[0]}" }
        require(argv.none { it.isEmpty() || it.any { c -> c.code < 0x20 || c.code == 0x7f } }) {
            "Command contains an empty argument or a control character"
        }
    }

    /** What the user sees in the command preview: exactly [argv], quoted where a shell would need it. */
    fun display(): String = argv.joinToString(" ") { quote(it) }

    companion object {
        const val BIN = "/system/bin/"

        /** The only executables Picket ever starts. */
        val ALLOWED_BINARIES: Set<String> =
            setOf("am", "pm", "cmd", "dumpsys", "settings", "ime", "dpm").mapTo(HashSet()) { BIN + it }

        fun of(vararg argv: String): ShellCommand = ShellCommand(argv.toList())

        private val safe = Regex("^[A-Za-z0-9_@%+=:,./-]+$")

        internal fun quote(arg: String): String = if (safe.matches(arg)) arg else "'" + arg.replace("'", "'\\''") + "'"
    }
}
