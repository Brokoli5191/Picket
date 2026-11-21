package app.brokoli5191.picket.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.action.Action
import app.brokoli5191.picket.core.action.Risk
import app.brokoli5191.picket.ui.actions.ActionOption
import app.brokoli5191.picket.ui.actions.ActionRequest
import app.brokoli5191.picket.ui.actions.ConfirmActionDialog
import app.brokoli5191.picket.ui.actions.OptionGroup
import app.brokoli5191.picket.ui.actions.actionOptionsFor
import app.brokoli5191.picket.ui.actions.openSystemScreen
import app.brokoli5191.picket.ui.actions.touchesRestrictedSetting
import app.brokoli5191.picket.ui.components.SectionHeader
import app.brokoli5191.picket.ui.components.segmentedColors

/** App detail with its actions, the confirmation dialog and a snackbar offering undo or a fallback. */
@Composable
fun AppDetailEntry(
    vm: AppDetailViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val pkg = state.packageName

    fun select(option: ActionOption) {
        val request = option.request
        when (request) {
            is ActionRequest.OpenSettings -> context.openSystemScreen(request.screen, request.packageName)
            else -> vm.request(option)
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        vm.consumeMessage()
        when (message) {
            is ActionMessage.Done -> {
                val text = resources.getString(R.string.action_result_done, resources.getString(message.label))
                val undo = message.undoEntryId
                val result =
                    snackbars.showSnackbar(
                        message = text,
                        actionLabel = if (undo != null) resources.getString(R.string.action_undo) else null,
                        withDismissAction = undo == null,
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed && undo != null) vm.undo(undo)
            }

            is ActionMessage.Failed -> {
                val label = resources.getString(message.label)
                val text = resources.getString(R.string.action_result_failed, label, message.reason)
                val fallback = message.fallback
                val result =
                    snackbars.showSnackbar(
                        message = text,
                        actionLabel = resources.getString(fallback?.label ?: R.string.action_open_settings_short),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    when {
                        fallback != null -> vm.request(fallback)
                        pkg != null -> context.openSystemScreen(message.screen, pkg)
                    }
                }
            }

            is ActionMessage.Undone -> {
                snackbars.showSnackbar(
                    resources.getString(
                        if (message.restoredRestrictedSetting) {
                            R.string.action_result_undone_restricted
                        } else {
                            R.string.action_result_undone
                        },
                    ),
                    withDismissAction = true,
                )
            }

            is ActionMessage.UndoFailed -> {
                val text = resources.getString(R.string.action_result_undo_failed, message.reason)
                snackbars.showSnackbar(text, withDismissAction = true)
            }
        }
    }

    val options = actionOptionsFor(state)
    var permissionsOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        val forceStop = options.firstOrNull { (it.request as? ActionRequest.Plain)?.action is Action.ForceStop }
        AppDetailScreen(
            state = state,
            onBack = onBack,
            onRefresh = vm::refresh,
            topBarActions = {
                if (forceStop != null) {
                    IconButton(onClick = { select(forceStop) }) {
                        Icon(
                            painterResource(R.drawable.ic_stop_circle),
                            contentDescription = stringResource(R.string.action_force_stop),
                        )
                    }
                }
            },
        ) {
            if (!state.loading || state.detail != null) {
                actionItems(options, permissionsOpen, { permissionsOpen = it }, ::select)
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    state.pending?.let { pending ->
        val action = pending.plan.action
        val highRiskSystem = pending.plan.risk == Risk.HIGH && state.info?.isSystem == true
        ConfirmActionDialog(
            plan = pending.plan,
            title = stringResource(pending.option.label),
            explanation =
                stringResource(pending.option.explanation) +
                    if (highRiskSystem) "\n\n" + stringResource(R.string.warning_system_app) else "",
            typedConfirmation = if (highRiskSystem) action.packageName else null,
            note = if (touchesRestrictedSetting(action)) stringResource(R.string.note_restricted_settings) else null,
            onConfirm = vm::confirm,
            onDismiss = vm::dismissPending,
        )
    }
}

/** The actions section: main actions, a collapsible group of permission revokes, then Settings links. */
internal fun LazyListScope.actionItems(
    options: List<ActionOption>,
    permissionsOpen: Boolean,
    onPermissionsOpen: (Boolean) -> Unit,
    onSelect: (ActionOption) -> Unit,
) {
    if (options.isEmpty()) return
    val main = options.filter { it.group == OptionGroup.MAIN }
    val permissions = options.filter { it.group == OptionGroup.PERMISSION }
    val settings = options.filter { it.group == OptionGroup.SETTINGS }
    item(key = "actions-header") { SectionHeader(stringResource(R.string.detail_actions)) }
    optionRows("action", main, onSelect)
    if (permissions.isNotEmpty()) {
        item(key = "permissions-toggle") {
            SegmentedListItem(
                onClick = { onPermissionsOpen(!permissionsOpen) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = segmentedColors(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                trailingContent = {
                    Icon(
                        painterResource(if (permissionsOpen) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription =
                            stringResource(if (permissionsOpen) R.string.action_collapse else R.string.action_expand),
                    )
                },
            ) { Text(stringResource(R.string.action_group_permissions, permissions.size)) }
        }
        if (permissionsOpen) optionRows("permission", permissions, onSelect)
    }
    if (settings.isNotEmpty()) {
        item(key = "settings-header") { SectionHeader(stringResource(R.string.detail_in_settings)) }
        optionRows("settings", settings, onSelect)
    }
}

private fun LazyListScope.optionRows(
    keyPrefix: String,
    options: List<ActionOption>,
    onSelect: (ActionOption) -> Unit,
) {
    itemsIndexed(options, key = { index, o -> "$keyPrefix-$index-${o.label}-${o.argument}" }) { index, option ->
        SegmentedListItem(
            onClick = { onSelect(option) },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
            colors = segmentedColors(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
            supportingContent = option.argument?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        ) { Text(stringResource(option.label)) }
    }
}
