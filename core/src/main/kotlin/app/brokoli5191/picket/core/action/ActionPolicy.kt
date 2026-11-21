package app.brokoli5191.picket.core.action

import app.brokoli5191.picket.core.command.CommandOutput
import app.brokoli5191.picket.core.command.ShellCommand
import app.brokoli5191.picket.core.model.StandbyBucket
import app.brokoli5191.picket.core.parse.Component
import app.brokoli5191.picket.core.parse.SettingsLists
import app.brokoli5191.picket.core.validation.Validators

/** Validation, risk, protection and argv construction for every [Action]. Pure; shared by app and service. */
object ActionPolicy {
    private const val CMD = ShellCommand.BIN + "cmd"
    private const val MAX_A11Y_ENTRIES = 64

    /** Throws [IllegalArgumentException] for anything that could not have come from Picket's own UI. */
    fun validate(action: Action) {
        Validators.requirePackageName(action.packageName)
        Validators.requireUserId(action.userId)
        when (action) {
            is Action.StopService -> {
                requireOwnComponent(action.component, action.packageName)
            }

            is Action.SetNotificationListener -> {
                requireOwnComponent(action.component, action.packageName)
            }

            is Action.SetPermission -> {
                Validators.requirePermissionName(action.permission)
            }

            is Action.SetAccessibilityServices -> {
                if (action.value.isNotEmpty()) {
                    val entries = action.value.split(':')
                    require(entries.size <= MAX_A11Y_ENTRIES) { "Too many accessibility services" }
                    entries.forEach { Validators.requireComponent(it) }
                }
            }

            else -> {}
        }
    }

    private fun requireOwnComponent(
        component: String,
        packageName: String,
    ) {
        Validators.requireComponent(component)
        require(component.substringBefore('/') == packageName) { "Component does not belong to $packageName" }
    }

    fun risk(action: Action): Risk =
        when (action) {
            is Action.ForceStop, is Action.KillBackground -> Risk.LOW
            is Action.StopService -> Risk.MEDIUM
            is Action.SetAppOp, is Action.SetStandbyBucket -> Risk.LOW
            is Action.SetPermission, is Action.SetIdleExemption -> Risk.LOW
            is Action.SetNotificationListener, is Action.SetAccessibilityServices, is Action.SetDndAccess -> Risk.MEDIUM
            is Action.SetRoleHolder -> Risk.MEDIUM
            is Action.SetEnabled -> if (action.state == EnabledState.DISABLED_USER) Risk.HIGH else Risk.LOW
        }

    /**
     * True for actions that stop, cripple or disable an app: these are refused for protected packages
     * (system UI, phone, launcher, keyboard, Shizuku, Picket itself, …).
     */
    fun isDisruptive(action: Action): Boolean =
        when (action) {
            is Action.ForceStop, is Action.KillBackground, is Action.StopService -> true
            is Action.SetEnabled -> action.state == EnabledState.DISABLED_USER
            is Action.SetStandbyBucket -> action.bucket >= StandbyBucket.RARE
            is Action.SetAppOp -> action.op == AppOp.RUN_ANY_IN_BACKGROUND && action.mode != AppOpMode.ALLOW
            else -> false
        }

    /** The exact argv. Calls `cmd <service>` directly: am, pm and settings are only sh wrappers around it. */
    fun command(action: Action): ShellCommand {
        validate(action)
        val user = action.userId.toString()
        val pkg = action.packageName
        val argv: List<String> =
            when (action) {
                is Action.ForceStop -> {
                    listOf(CMD, "activity", "force-stop", "--user", user, pkg)
                }

                is Action.KillBackground -> {
                    listOf(CMD, "activity", "kill", "--user", user, pkg)
                }

                is Action.StopService -> {
                    listOf(CMD, "activity", "stop-service", "--user", user, "-n", action.component)
                }

                is Action.SetAppOp -> {
                    listOf(CMD, "appops", "set", "--user", user, pkg, action.op.name, action.mode.shellName)
                }

                is Action.SetStandbyBucket -> {
                    listOf(CMD, "activity", "set-standby-bucket", "--user", user, pkg, action.bucket.shellName)
                }

                is Action.SetNotificationListener -> {
                    listOf(
                        CMD,
                        "notification",
                        if (action.allowed) "allow_listener" else "disallow_listener",
                        action.component,
                        user,
                    )
                }

                is Action.SetAccessibilityServices -> {
                    if (action.value.isEmpty()) {
                        listOf(CMD, "settings", "--user", user, "delete", "secure", A11Y_KEY)
                    } else {
                        listOf(CMD, "settings", "--user", user, "put", "secure", A11Y_KEY, action.value)
                    }
                }

                is Action.SetPermission -> {
                    listOf(
                        CMD,
                        "package",
                        if (action.granted) "grant" else "revoke",
                        "--user",
                        user,
                        pkg,
                        action.permission,
                    )
                }

                is Action.SetIdleExemption -> {
                    listOf(
                        CMD,
                        "deviceidle",
                        "whitelist",
                        (if (action.exempt) "+" else "-") + pkg,
                    )
                }

                is Action.SetRoleHolder -> {
                    listOf(
                        CMD,
                        "role",
                        if (action.holder) "add-role-holder" else "remove-role-holder",
                        "--user",
                        user,
                        action.role.roleName,
                        pkg,
                    )
                }

                is Action.SetEnabled -> {
                    when (action.state) {
                        EnabledState.DISABLED_USER -> listOf(CMD, "package", "disable-user", "--user", user, pkg)
                        EnabledState.ENABLED -> listOf(CMD, "package", "enable", "--user", user, pkg)
                        EnabledState.DEFAULT -> listOf(CMD, "package", "default-state", "--user", user, pkg)
                    }
                }

                is Action.SetDndAccess -> {
                    listOf(CMD, "notification", if (action.allowed) "allow_dnd" else "disallow_dnd", pkg, user)
                }
            }
        return ShellCommand(argv)
    }

    /**
     * Success of a finished command. Several commands lie with their exit code: `stop-service` exits
     * 255 even when it worked, `cmd notification` reports errors on stdout with exit 0.
     */
    fun outcome(
        action: Action,
        output: CommandOutput,
    ): ActionOutcome {
        val text = output.stdout + "\n" + output.stderr
        val (success, note) =
            when {
                output.timedOut -> {
                    false to "timed out"
                }

                action is Action.StopService -> {
                    when {
                        "Service stopped" in text -> {
                            true to null
                        }

                        "not running" in text -> {
                            false to "the service was not running"
                        }

                        else -> {
                            false to
                                "the service could not be stopped " +
                                "(only exported services can be stopped from the shell)"
                        }
                    }
                }

                "Exception occurred" in text || "Error" in output.stdout || "Failure" in text -> {
                    false to
                        "the command reported an error"
                }

                action is Action.SetEnabled && output.exitCode == 0 && "new state" !in output.stdout -> {
                    false to "no new state was reported"
                }

                else -> {
                    (output.exitCode == 0) to null
                }
            }
        return ActionOutcome(success, output.exitCode, output.stdout.trim(), output.stderr.trim(), note)
    }

    /** Removes [component] from the current accessibility setting, keeping every other entry as it was. */
    fun accessibilityWithout(
        current: String,
        component: Component,
    ): String = SettingsLists.withoutComponent(current, component)

    const val A11Y_KEY = "enabled_accessibility_services"
}
