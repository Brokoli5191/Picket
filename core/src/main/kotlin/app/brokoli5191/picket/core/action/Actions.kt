package app.brokoli5191.picket.core.action

import app.brokoli5191.picket.core.command.Role
import app.brokoli5191.picket.core.command.ShellCommand
import app.brokoli5191.picket.core.model.StandbyBucket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** App-ops Picket can change. A closed set: nothing else can reach `cmd appops set`. */
@Serializable
enum class AppOp {
    RUN_ANY_IN_BACKGROUND,
    RUN_IN_BACKGROUND,
    SYSTEM_ALERT_WINDOW,
    GET_USAGE_STATS,
}

@Serializable
enum class AppOpMode(
    val shellName: String,
) {
    ALLOW("allow"),
    IGNORE("ignore"),
    DENY("deny"),
    DEFAULT("default"),
    FOREGROUND("foreground"),
    ;

    companion object {
        fun fromShell(name: String): AppOpMode? = entries.firstOrNull { it.shellName == name.trim() }
    }
}

/** Package states Picket switches between (`pm default-state` / `enable` / `disable-user`). */
@Serializable
enum class EnabledState { DEFAULT, ENABLED, DISABLED_USER }

@Serializable
enum class Risk { LOW, MEDIUM, HIGH }

/** One change Picket can make. Every field is validated by [ActionPolicy] before a command is built. */
@Serializable
sealed interface Action {
    val packageName: String
    val userId: Int

    @Serializable
    @SerialName("force_stop")
    data class ForceStop(
        override val packageName: String,
        override val userId: Int,
    ) : Action

    @Serializable
    @SerialName("kill_background")
    data class KillBackground(
        override val packageName: String,
        override val userId: Int,
    ) : Action

    @Serializable
    @SerialName("stop_service")
    data class StopService(
        override val packageName: String,
        override val userId: Int,
        /** Flattened component of the service; must belong to [packageName]. */
        val component: String,
    ) : Action

    @Serializable
    @SerialName("set_app_op")
    data class SetAppOp(
        override val packageName: String,
        override val userId: Int,
        val op: AppOp,
        val mode: AppOpMode,
    ) : Action

    @Serializable
    @SerialName("set_standby_bucket")
    data class SetStandbyBucket(
        override val packageName: String,
        override val userId: Int,
        val bucket: StandbyBucket,
    ) : Action

    @Serializable
    @SerialName("set_notification_listener")
    data class SetNotificationListener(
        override val packageName: String,
        override val userId: Int,
        val component: String,
        val allowed: Boolean,
    ) : Action

    /**
     * Replaces `enabled_accessibility_services` with [value]. The planner computes the value from the
     * current setting (one entry removed, every other entry kept); the inverse restores the old value.
     */
    @Serializable
    @SerialName("set_accessibility_services")
    data class SetAccessibilityServices(
        override val packageName: String,
        override val userId: Int,
        val value: String,
    ) : Action

    @Serializable
    @SerialName("set_permission")
    data class SetPermission(
        override val packageName: String,
        override val userId: Int,
        val permission: String,
        val granted: Boolean,
    ) : Action

    @Serializable
    @SerialName("set_idle_exemption")
    data class SetIdleExemption(
        override val packageName: String,
        override val userId: Int,
        val exempt: Boolean,
    ) : Action

    @Serializable
    @SerialName("set_role_holder")
    data class SetRoleHolder(
        override val packageName: String,
        override val userId: Int,
        val role: Role,
        val holder: Boolean,
    ) : Action

    @Serializable
    @SerialName("set_enabled")
    data class SetEnabled(
        override val packageName: String,
        override val userId: Int,
        val state: EnabledState,
    ) : Action

    @Serializable
    @SerialName("set_dnd_access")
    data class SetDndAccess(
        override val packageName: String,
        override val userId: Int,
        val allowed: Boolean,
    ) : Action
}

/** What will run, what it replaces, and how to undo it. Shown to the user before anything runs. */
@Serializable
data class ActionPlan(
    val action: Action,
    val command: ShellCommand,
    val risk: Risk,
    /** Human-readable state before the change, recorded in the journal ("RUN_ANY_IN_BACKGROUND: allow"). */
    val previousState: String?,
    /** The action that restores [previousState]; null where Android has no inverse (force stop, kill). */
    val inverse: Action?,
)

@Serializable
data class ActionOutcome(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    /** Why the outcome counts as success or failure when the exit code does not tell. */
    val note: String? = null,
)
