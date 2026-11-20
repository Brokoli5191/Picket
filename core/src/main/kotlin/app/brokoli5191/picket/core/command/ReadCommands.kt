package app.brokoli5191.picket.core.command

import app.brokoli5191.picket.core.validation.Validators

/** What a command produced; the privileged service fills it, collectors in :core consume it. */
data class CommandOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = "",
    val timedOut: Boolean = false,
) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/** Runs one fixed command. Implemented by the privileged service; fakes in tests. */
fun interface CommandExecutor {
    fun run(command: ShellCommand): CommandOutput
}

/** `Settings.Secure` keys Picket reads. A closed set: no caller can ask for another key. */
enum class SecureKey(
    val key: String,
) {
    ENABLED_NOTIFICATION_LISTENERS("enabled_notification_listeners"),
    ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES("enabled_notification_policy_access_packages"),
    ENABLED_ACCESSIBILITY_SERVICES("enabled_accessibility_services"),
    ACCESSIBILITY_ENABLED("accessibility_enabled"),
    ENABLED_INPUT_METHODS("enabled_input_methods"),
    DEFAULT_INPUT_METHOD("default_input_method"),
    ALWAYS_ON_VPN_APP("always_on_vpn_app"),
}

/** Default roles Picket reports and can remove holders from. */
enum class Role(
    val roleName: String,
) {
    SMS("android.app.role.SMS"),
    DIALER("android.app.role.DIALER"),
    ASSISTANT("android.app.role.ASSISTANT"),
    BROWSER("android.app.role.BROWSER"),
    HOME("android.app.role.HOME"),
}

/** Every read-only command the privileged service may run. Variable parts are validated here. */
object ReadCommands {
    private const val DUMPSYS = ShellCommand.BIN + "dumpsys"
    private const val CMD = ShellCommand.BIN + "cmd"
    private const val SETTINGS = ShellCommand.BIN + "settings"
    private const val AM = ShellCommand.BIN + "am"
    private const val IME = ShellCommand.BIN + "ime"

    val activityServices = ShellCommand.of(DUMPSYS, "activity", "services")
    val notification = ShellCommand.of(DUMPSYS, "notification")
    val devicePolicy = ShellCommand.of(DUMPSYS, "device_policy")
    val vpnManagement = ShellCommand.of(DUMPSYS, "vpn_management")
    val alarm = ShellCommand.of(DUMPSYS, "alarm")
    val jobScheduler = ShellCommand.of(DUMPSYS, "jobscheduler")
    val power = ShellCommand.of(DUMPSYS, "power")

    /**
     * Aggregates since the last charge for all uids, without history. Deliberately not `--checkin`:
     * that variant may return the previous cycle and deletes the system's saved checkin file.
     * Any installed package works as the argument; `android` always exists.
     */
    val batteryStatsCheckin = ShellCommand.of(DUMPSYS, "batterystats", "-c", "android")
    val idleWhitelist = ShellCommand.of(CMD, "deviceidle", "whitelist")
    val inputMethods = ShellCommand.of(IME, "list", "-s")
    val homeActivity =
        ShellCommand.of(
            CMD,
            "package",
            "resolve-activity",
            "--brief",
            "-a",
            "android.intent.action.MAIN",
            "-c",
            "android.intent.category.HOME",
        )

    fun activityServices(packageName: String) =
        ShellCommand.of(DUMPSYS, "activity", "services", Validators.requirePackageName(packageName))

    fun secure(key: SecureKey) = ShellCommand.of(SETTINGS, "get", "secure", key.key)

    fun appOpsHolders(op: String) = ShellCommand.of(CMD, "appops", "query-op", requireOp(op), "allow")

    fun roleHolders(
        role: Role,
        userId: Int,
    ) = ShellCommand.of(
        CMD,
        "role",
        "get-role-holders",
        "--user",
        Validators.requireUserId(userId).toString(),
        role.roleName,
    )

    fun standbyBucket(
        packageName: String,
        userId: Int,
    ) = ShellCommand.of(
        AM,
        "get-standby-bucket",
        "--user",
        Validators.requireUserId(userId).toString(),
        Validators.requirePackageName(packageName),
    )

    fun packageDump(packageName: String) =
        ShellCommand.of(DUMPSYS, "package", Validators.requirePackageName(packageName))

    fun appOps(
        packageName: String,
        userId: Int,
    ) = ShellCommand.of(
        CMD,
        "appops",
        "get",
        "--user",
        Validators.requireUserId(userId).toString(),
        Validators.requirePackageName(packageName),
    )

    fun meminfo(pid: Int): ShellCommand {
        require(pid > 0) { "Invalid pid: $pid" }
        return ShellCommand.of(DUMPSYS, "meminfo", "--checkin", pid.toString())
    }

    private val opName = Regex("^[A-Z][A-Z0-9_]{2,63}$")

    private fun requireOp(op: String): String {
        require(opName.matches(op)) { "Invalid app-op name" }
        return op
    }
}
