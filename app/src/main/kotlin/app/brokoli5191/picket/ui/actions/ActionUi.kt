package app.brokoli5191.picket.ui.actions

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.action.Action
import app.brokoli5191.picket.core.action.ActionPlan
import app.brokoli5191.picket.core.action.ActionPolicy
import app.brokoli5191.picket.core.action.AppOp
import app.brokoli5191.picket.core.action.AppOpMode
import app.brokoli5191.picket.core.action.EnabledState
import app.brokoli5191.picket.core.action.Risk
import app.brokoli5191.picket.core.command.Role
import app.brokoli5191.picket.core.model.IdleExemption
import app.brokoli5191.picket.core.model.StandbyBucket
import app.brokoli5191.picket.core.model.WatcherKind
import app.brokoli5191.picket.core.process.Uids
import app.brokoli5191.picket.ui.detail.AppDetailUiState
import app.brokoli5191.picket.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/** What the user asked for; the service turns the first two into a plan with the exact command. */
sealed interface ActionRequest {
    data class Plain(
        val action: Action,
    ) : ActionRequest

    data class DisableAccessibility(
        val packageName: String,
        val userId: Int,
        val component: String,
    ) : ActionRequest

    /** No command: opens the matching Settings screen (device admins, VPN, keyboards). */
    data class OpenSettings(
        val screen: SystemScreen,
        val packageName: String,
    ) : ActionRequest
}

enum class OptionGroup { MAIN, PERMISSION, SETTINGS }

data class ActionOption(
    @param:StringRes val label: Int,
    val argument: String?,
    @param:StringRes val explanation: Int,
    val request: ActionRequest,
    val group: OptionGroup = OptionGroup.MAIN,
)

/** Settings screens Picket falls back to when a command is not possible or does not work. */
enum class SystemScreen {
    APP_DETAILS,
    NOTIFICATION_LISTENERS,
    ACCESSIBILITY,
    OVERLAY,
    USAGE_ACCESS,
    BATTERY_OPTIMISATION,
    DEFAULT_APPS,
    DND_ACCESS,
    SECURITY,
    VPN,
    INPUT_METHODS,
    ;

    fun intent(packageName: String): Intent {
        val packageUri = "package:$packageName".toUri()
        return when (this) {
            APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            NOTIFICATION_LISTENERS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            BATTERY_OPTIMISATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            DEFAULT_APPS -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            SECURITY -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            VPN -> Intent(Settings.ACTION_VPN_SETTINGS)
            INPUT_METHODS -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        /** Where the user can do the same thing by hand. */
        fun forAction(action: Action): SystemScreen =
            when (action) {
                is Action.SetNotificationListener -> {
                    NOTIFICATION_LISTENERS
                }

                is Action.SetAccessibilityServices -> {
                    ACCESSIBILITY
                }

                is Action.SetAppOp -> {
                    when (action.op) {
                        AppOp.SYSTEM_ALERT_WINDOW -> OVERLAY
                        AppOp.GET_USAGE_STATS -> USAGE_ACCESS
                        else -> APP_DETAILS
                    }
                }

                is Action.SetIdleExemption -> {
                    BATTERY_OPTIMISATION
                }

                is Action.SetRoleHolder -> {
                    DEFAULT_APPS
                }

                is Action.SetDndAccess -> {
                    DND_ACCESS
                }

                else -> {
                    APP_DETAILS
                }
            }
    }
}

/** Opens [screen]; OEM builds without that screen get the app's details page instead. */
fun Context.openSystemScreen(
    screen: SystemScreen,
    packageName: String,
) {
    try {
        startActivity(screen.intent(packageName))
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(SystemScreen.APP_DETAILS.intent(packageName)) }
    }
}

/**
 * Android 13+ "restricted settings": Settings refuses to turn notification or accessibility access
 * back on for sideloaded apps. The shell commands Picket uses for undo are not subject to that.
 */
fun touchesRestrictedSetting(action: Action): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        (action is Action.SetNotificationListener || action is Action.SetAccessibilityServices)

/** The actions that make sense for this app right now, most common first. */
fun actionOptionsFor(state: AppDetailUiState): List<ActionOption> {
    val pkg = state.packageName ?: return emptyList()
    val user = Uids.userId(state.uid)
    val options = mutableListOf<ActionOption>()

    fun add(
        label: Int,
        explanation: Int,
        action: Action,
        argument: String? = null,
        group: OptionGroup = OptionGroup.MAIN,
    ) {
        options += ActionOption(label, argument, explanation, ActionRequest.Plain(action), group)
    }

    fun settings(
        label: Int,
        screen: SystemScreen,
    ) {
        options +=
            ActionOption(
                label,
                null,
                R.string.explain_open_settings,
                ActionRequest.OpenSettings(screen, pkg),
                OptionGroup.SETTINGS,
            )
    }

    if (state.processes.isNotEmpty()) {
        add(R.string.action_force_stop, R.string.explain_force_stop, Action.ForceStop(pkg, user))
        add(R.string.action_kill, R.string.explain_kill, Action.KillBackground(pkg, user))
    }
    // Only started services: stop-service ends the started state, a purely bound service is unaffected.
    state.detail?.services?.filter { it.started }?.forEach { s ->
        val action = Action.StopService(pkg, user, s.component)
        add(R.string.action_stop_service, R.string.explain_stop_service, action, s.component.substringAfter('/'))
    }
    state.watchers.forEach { w ->
        val component = w.component
        when (w.kind) {
            WatcherKind.NOTIFICATION_LISTENER -> {
                if (component != null) {
                    val action = Action.SetNotificationListener(pkg, user, component, allowed = false)
                    add(R.string.action_revoke_listener, R.string.explain_revoke_listener, action, component)
                }
            }

            WatcherKind.ACCESSIBILITY_SERVICE -> {
                if (component != null) {
                    val request = ActionRequest.DisableAccessibility(pkg, user, component)
                    options +=
                        ActionOption(R.string.action_disable_a11y, component, R.string.explain_disable_a11y, request)
                }
            }

            WatcherKind.DND_ACCESS -> {
                add(
                    R.string.action_revoke_dnd,
                    R.string.explain_revoke_dnd,
                    Action.SetDndAccess(pkg, user, allowed = false),
                )
            }

            WatcherKind.DEFAULT_ROLE -> {
                Role.entries.firstOrNull { it.name.lowercase() == w.detail }?.let { role ->
                    val action = Action.SetRoleHolder(pkg, user, role, holder = false)
                    add(R.string.action_remove_role, R.string.explain_remove_role, action, role.name.lowercase())
                }
            }

            WatcherKind.OVERLAY -> {
                val action = Action.SetAppOp(pkg, user, AppOp.SYSTEM_ALERT_WINDOW, AppOpMode.IGNORE)
                add(R.string.action_revoke_overlay, R.string.explain_revoke_appop, action)
            }

            WatcherKind.USAGE_ACCESS -> {
                val action = Action.SetAppOp(pkg, user, AppOp.GET_USAGE_STATS, AppOpMode.IGNORE)
                add(R.string.action_revoke_usage, R.string.explain_revoke_appop, action)
            }

            WatcherKind.DEVICE_ADMIN -> {
                settings(R.string.action_open_admin_settings, SystemScreen.SECURITY)
            }

            WatcherKind.VPN -> {
                settings(R.string.action_open_vpn_settings, SystemScreen.VPN)
            }

            WatcherKind.INPUT_METHOD -> {
                settings(R.string.action_open_ime_settings, SystemScreen.INPUT_METHODS)
            }
        }
    }
    val ops = state.detail?.appOps.orEmpty()
    if (ops.none { it.op == AppOp.RUN_ANY_IN_BACKGROUND.name && it.mode == AppOpMode.IGNORE.shellName }) {
        val action = Action.SetAppOp(pkg, user, AppOp.RUN_ANY_IN_BACKGROUND, AppOpMode.IGNORE)
        add(R.string.action_restrict_background, R.string.explain_restrict_background, action)
    }
    if (state.detail?.standbyBucket != StandbyBucket.RESTRICTED) {
        val action = Action.SetStandbyBucket(pkg, user, StandbyBucket.RESTRICTED)
        add(R.string.action_restricted_bucket, R.string.explain_restricted_bucket, action)
    }
    if (state.detail?.idleExemption == IdleExemption.USER) {
        add(
            R.string.action_remove_exemption,
            R.string.explain_remove_exemption,
            Action.SetIdleExemption(pkg, user, exempt = false),
        )
    }
    state.detail
        ?.runtimePermissions
        ?.filter { it.granted && !it.fixed }
        ?.forEach { p ->
            val action = Action.SetPermission(pkg, user, p.name, granted = false)
            add(
                R.string.action_revoke_permission,
                R.string.explain_revoke_permission,
                action,
                p.name,
                OptionGroup.PERMISSION,
            )
        }
    add(
        R.string.action_disable_app,
        R.string.explain_disable_app,
        Action.SetEnabled(pkg, user, EnabledState.DISABLED_USER),
    )
    settings(R.string.action_open_app_settings, SystemScreen.APP_DETAILS)
    return options
}

@Composable
fun riskLabel(risk: Risk): String =
    stringResource(
        when (risk) {
            Risk.LOW -> R.string.risk_low
            Risk.MEDIUM -> R.string.risk_medium
            Risk.HIGH -> R.string.risk_high
        },
    )

/**
 * Confirmation with the exact command line and the exact undo command. High-risk actions on system
 * apps also require typing the package name ([typedConfirmation]).
 */
@Composable
fun ConfirmActionDialog(
    plan: ActionPlan,
    title: String,
    explanation: String,
    typedConfirmation: String?,
    note: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val haptics = rememberHaptics()
    val confirmed = typedConfirmation == null || typed.trim() == typedConfirmation
    val undoCommand =
        remember(plan) { plan.inverse?.let { runCatching { ActionPolicy.command(it).display() }.getOrNull() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.risk_label, riskLabel(plan.risk))) })
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
                plan.previousState?.let {
                    Text(stringResource(R.string.confirm_previous, it), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.confirm_runs), style = MaterialTheme.typography.labelLarge)
                CommandCard(plan.command.display())
                if (undoCommand != null) {
                    Text(stringResource(R.string.confirm_undo_runs), style = MaterialTheme.typography.labelLarge)
                    CommandCard(undoCommand)
                } else {
                    Text(stringResource(R.string.confirm_not_undoable), style = MaterialTheme.typography.bodySmall)
                }
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (typedConfirmation != null) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.confirm_type_package, typedConfirmation)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmed,
                onClick = {
                    haptics.confirm()
                    onConfirm()
                },
            ) { Text(stringResource(R.string.action_run)) }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.reject()
                onDismiss()
            }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** A command line in monospace, selectable, with a copy button. */
@Composable
fun CommandCard(
    command: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("command", command))) }
            }) {
                Icon(
                    painterResource(R.drawable.ic_content_copy),
                    contentDescription = stringResource(R.string.action_copy_command),
                )
            }
        }
    }
}
