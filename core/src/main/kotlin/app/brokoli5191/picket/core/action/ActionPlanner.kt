package app.brokoli5191.picket.core.action

import app.brokoli5191.picket.core.command.CommandExecutor
import app.brokoli5191.picket.core.command.ReadCommands
import app.brokoli5191.picket.core.command.Role
import app.brokoli5191.picket.core.command.SecureKey
import app.brokoli5191.picket.core.command.ShellCommand
import app.brokoli5191.picket.core.model.IdleExemption
import app.brokoli5191.picket.core.parse.AppOpsParser
import app.brokoli5191.picket.core.parse.Component
import app.brokoli5191.picket.core.parse.DeviceIdleParser
import app.brokoli5191.picket.core.parse.NotificationDumpParser
import app.brokoli5191.picket.core.parse.PackageDumpParser
import app.brokoli5191.picket.core.parse.RoleHolders
import app.brokoli5191.picket.core.parse.SettingsLists
import app.brokoli5191.picket.core.parse.StandbyBucketParser
import app.brokoli5191.picket.core.validation.Validators
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Packages that may never be stopped, restricted or disabled. Loaded from `assets/protected.json`. */
@Serializable
data class ProtectedPackages(
    val packages: List<String>,
    val prefixes: List<String> = emptyList(),
) {
    fun isProtected(
        packageName: String,
        dynamic: Set<String> = emptySet(),
    ): Boolean = packageName in packages || packageName in dynamic || prefixes.any { packageName.startsWith(it) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ProtectedPackages = json.decodeFromString(serializer(), text)
    }
}

/**
 * Reads the state an action is about to change, so the preview can show it, the journal can record
 * it and the inverse can restore it exactly. [execute] re-reads the state and refuses to run when it
 * changed after the preview.
 */
class ActionPlanner(
    private val executor: CommandExecutor,
) {
    /** Plan for turning off one accessibility service while keeping every other enabled one. */
    fun disableAccessibilityService(
        packageName: String,
        userId: Int,
        component: String,
    ): ActionPlan {
        val target =
            Component.parse(Validators.requireComponent(component))
                ?: throw IllegalArgumentException("Invalid component")
        require(target.packageName == packageName) { "Component does not belong to $packageName" }
        val current = accessibilityValue(userId)
        return plan(
            Action.SetAccessibilityServices(packageName, userId, ActionPolicy.accessibilityWithout(current, target)),
        )
    }

    fun plan(action: Action): ActionPlan {
        ActionPolicy.validate(action)
        val (previous, inverse) = previousStateAndInverse(action)
        return ActionPlan(action, ActionPolicy.command(action), ActionPolicy.risk(action), previous, inverse)
    }

    /**
     * Runs a previewed plan. Refuses when the command is not the one the action produces (a tampered
     * plan) or when the state it would change is no longer what the user saw.
     */
    fun execute(plan: ActionPlan): ActionOutcome {
        val fresh = plan(plan.action)
        require(fresh.command == plan.command) { "The command does not match the action" }
        if (fresh.previousState != plan.previousState) {
            return ActionOutcome(false, -1, "", "", "The state changed since the preview; review the action again")
        }
        val output = executor.run(plan.command)
        return ActionPolicy.outcome(plan.action, output)
    }

    private fun read(command: ShellCommand): String? = executor.run(command).takeIf { it.ok }?.stdout

    private fun accessibilityValue(userId: Int): String =
        read(ReadCommands.secureForUser(SecureKey.ENABLED_ACCESSIBILITY_SERVICES, userId))
            ?.trim()
            ?.takeUnless(SettingsLists::isUnset)
            .orEmpty()

    /** `get-role-holders` from API 34; older releases print "Unknown command" (exit 0) and use the role dump. */
    private fun roleHolders(
        role: Role,
        userId: Int,
    ): List<String>? {
        val direct = read(ReadCommands.roleHolders(role, userId))
        if (direct != null && "Unknown command" !in direct) return runCatching { RoleHolders.parse(direct) }.getOrNull()
        return read(ReadCommands.roleDump)?.let { RoleHolders.parseDump(it, userId)[role.roleName].orEmpty() }
    }

    private fun previousStateAndInverse(action: Action): Pair<String?, Action?> {
        val pkg = action.packageName
        val user = action.userId
        return when (action) {
            is Action.ForceStop, is Action.KillBackground, is Action.StopService -> {
                null to null
            }

            is Action.SetAppOp -> {
                val mode =
                    read(
                        ReadCommands.appOp(pkg, user, action.op.name),
                    )?.let { AppOpsParser.modeOf(it, action.op.name) }
                val previous = mode?.let(AppOpMode::fromShell)
                "${action.op.name}: ${mode ?: "unknown"}" to previous?.let { action.copy(mode = it) }
            }

            is Action.SetStandbyBucket -> {
                val bucket = read(ReadCommands.standbyBucket(pkg, user))?.let(StandbyBucketParser::parseSingle)
                "standby bucket: ${bucket?.shellName ?: "unknown"}" to bucket?.let { action.copy(bucket = it) }
            }

            is Action.SetNotificationListener -> {
                val target = Component.parse(action.component)
                val approved =
                    read(
                        ReadCommands.secureForUser(SecureKey.ENABLED_NOTIFICATION_LISTENERS, user),
                    )?.let(SettingsLists::components)
                val was = approved?.contains(target)
                "notification listener: ${was.label("allowed", "not allowed")}" to
                    was?.let { action.copy(allowed = it) }
            }

            is Action.SetAccessibilityServices -> {
                val current = accessibilityValue(user)
                "$A11Y_PREFIX${current.ifEmpty { "(none)" }}" to action.copy(value = current)
            }

            is Action.SetPermission -> {
                val grants =
                    read(ReadCommands.packageDump(pkg))
                        ?.let {
                            PackageDumpParser.parse(it, pkg)
                        }?.runtimePermissions
                        ?.get(user)
                val was = grants?.firstOrNull { it.name == action.permission }?.granted
                "${action.permission}: ${was.label("granted", "not granted")}" to was?.let { action.copy(granted = it) }
            }

            is Action.SetIdleExemption -> {
                val exemption =
                    read(ReadCommands.idleWhitelist)?.let(DeviceIdleParser::parse)?.let {
                        it[pkg]
                            ?: IdleExemption.NONE
                    }
                val was = exemption?.let { it == IdleExemption.USER }
                "battery optimisation: ${exemption?.name?.lowercase() ?: "unknown"}" to
                    was?.let { action.copy(exempt = it) }
            }

            is Action.SetRoleHolder -> {
                val holders = roleHolders(action.role, user)
                val was = holders?.contains(pkg)
                "${action.role.roleName}: ${holders?.joinToString(";")?.ifEmpty { "(none)" } ?: "unknown"}" to
                    was?.let { action.copy(holder = it) }
            }

            is Action.SetEnabled -> {
                val state =
                    read(
                        ReadCommands.packageDump(pkg),
                    )?.let { PackageDumpParser.parse(it, pkg) }?.enabledState?.get(user)
                val previous =
                    when (state) {
                        0 -> EnabledState.DEFAULT
                        1 -> EnabledState.ENABLED
                        3 -> EnabledState.DISABLED_USER
                        else -> null
                    }
                "enabled state: ${previous?.name?.lowercase() ?: state?.toString() ?: "unknown"}" to
                    previous?.let { action.copy(state = it) }
            }

            is Action.SetDndAccess -> {
                val granted =
                    read(
                        ReadCommands.notificationZen,
                    )?.let { NotificationDumpParser.dndAccessPackages(it, user) }
                val was = granted?.contains(pkg)
                "Do Not Disturb access: ${was.label("granted", "not granted")}" to
                    was?.let { action.copy(allowed = it) }
            }
        }
    }

    private fun Boolean?.label(
        yes: String,
        no: String,
    ): String =
        when (this) {
            true -> yes
            false -> no
            null -> "unknown"
        }

    private companion object {
        const val A11Y_PREFIX = "enabled_accessibility_services: "
    }
}
