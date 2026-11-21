package app.brokoli5191.picket.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.action.Action
import app.brokoli5191.picket.core.action.ActionPlan
import app.brokoli5191.picket.core.model.AppDetail
import app.brokoli5191.picket.core.model.AppWakeups
import app.brokoli5191.picket.core.model.WatcherEntry
import app.brokoli5191.picket.core.process.CpuUsage
import app.brokoli5191.picket.core.process.GroupKey
import app.brokoli5191.picket.core.process.GroupLabel
import app.brokoli5191.picket.core.process.ProcessGrouping
import app.brokoli5191.picket.core.process.ProcessRow
import app.brokoli5191.picket.core.process.ProcessTable
import app.brokoli5191.picket.core.process.Uids
import app.brokoli5191.picket.data.ActionsRepository
import app.brokoli5191.picket.data.AppDetailRepository
import app.brokoli5191.picket.data.AppInfo
import app.brokoli5191.picket.data.PackageCatalog
import app.brokoli5191.picket.data.WakeupsRepository
import app.brokoli5191.picket.data.WatchersRepository
import app.brokoli5191.picket.privileged.client.PrivilegedClient
import app.brokoli5191.picket.ui.actions.ActionOption
import app.brokoli5191.picket.ui.actions.ActionRequest
import app.brokoli5191.picket.ui.actions.SystemScreen
import app.brokoli5191.picket.ui.actions.touchesRestrictedSetting
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppDetailUiState(
    val loading: Boolean = true,
    val uid: Int,
    val packageName: String?,
    val info: AppInfo? = null,
    val processes: List<ProcessRow> = emptyList(),
    val detail: AppDetail? = null,
    val watchers: List<WatcherEntry> = emptyList(),
    val wakeups: AppWakeups? = null,
    val errors: List<String> = emptyList(),
    /** A plan waiting for confirmation. */
    val pending: PendingAction? = null,
    /** Result of the last action, shown once. */
    val message: ActionMessage? = null,
)

data class PendingAction(
    val plan: ActionPlan,
    val option: ActionOption,
)

/** Result of the last action, shown once in a snackbar. */
sealed interface ActionMessage {
    data class Done(
        @param:StringRes val label: Int,
        /** Journal id when the action can be undone from the snackbar. */
        val undoEntryId: Long?,
    ) : ActionMessage

    data class Failed(
        @param:StringRes val label: Int,
        val reason: String,
        /** What to offer instead: force stop after a failed service stop, otherwise a Settings screen. */
        val fallback: ActionOption?,
        val screen: SystemScreen,
    ) : ActionMessage

    data class Undone(
        val restoredRestrictedSetting: Boolean,
    ) : ActionMessage

    data class UndoFailed(
        val reason: String,
    ) : ActionMessage
}

class AppDetailViewModel(
    private val uid: Int,
    private val packageName: String?,
    private val client: PrivilegedClient,
    private val catalog: PackageCatalog,
    private val details: AppDetailRepository,
    private val watchers: WatchersRepository,
    private val wakeups: WakeupsRepository,
    private val actions: ActionsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppDetailUiState(uid = uid, packageName = packageName))
    val state: StateFlow<AppDetailUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true)
            val userId = Uids.userId(uid)
            val processesJob = async { client.listProcesses() }
            val detailJob = async { packageName?.let { details.load(it, userId) } }
            val watchersJob = async { watchers.load() }
            val wakeupsJob = async { wakeups.load(DAY_MS) }

            val errors = mutableListOf<String>()
            val snapshot = processesJob.await().also { r -> r.error?.let { errors += it.message } }.value
            val rows =
                snapshot
                    ?.let { s ->
                        val grouping = ProcessGrouping(catalog::uidOf)
                        val mine =
                            grouping.group(s.processes).filter {
                                it.key == GroupKey.App(uid) ||
                                    it.key == GroupKey.SystemUid(uid)
                            }
                        ProcessTable
                            .build(s, CpuUsage.NONE, mine) { _, _ -> GroupLabel("", packageName, false) }
                            .flatMap { it.processes }
                    }.orEmpty()
            val detail = detailJob.await()?.also { r -> r.error?.let { errors += it.message } }?.value
            val held =
                watchersJob
                    .await()
                    .value
                    ?.entries
                    ?.filter { it.packageName == packageName }
                    .orEmpty()
            val wakeupTotals =
                wakeupsJob
                    .await()
                    .value
                    ?.apps
                    ?.firstOrNull { it.uid == uid || (packageName != null && it.packageName == packageName) }

            mutableState.value =
                mutableState.value.copy(
                    loading = false,
                    uid = uid,
                    packageName = packageName,
                    info = packageName?.let(catalog::info),
                    processes = rows,
                    detail = detail,
                    watchers = held,
                    wakeups = wakeupTotals,
                    errors = errors,
                )
        }
    }

    fun request(option: ActionOption) {
        val request = option.request
        if (request is ActionRequest.OpenSettings) return
        viewModelScope.launch {
            val reply =
                when (request) {
                    is ActionRequest.Plain -> {
                        actions.plan(request.action)
                    }

                    is ActionRequest.DisableAccessibility -> {
                        actions.planDisableAccessibility(request.packageName, request.userId, request.component)
                    }

                    is ActionRequest.OpenSettings -> {
                        return@launch
                    }
                }
            val plan = reply.value
            mutableState.update { s ->
                if (plan != null) {
                    s.copy(pending = PendingAction(plan, option))
                } else {
                    s.copy(message = failure(option, reply.error?.message.orEmpty()))
                }
            }
        }
    }

    fun confirm() {
        val pending = mutableState.value.pending ?: return
        mutableState.update { it.copy(pending = null) }
        viewModelScope.launch {
            val executed = actions.execute(pending.plan)
            val message =
                if (executed.success) {
                    ActionMessage.Done(pending.option.label, executed.entryId.takeIf { pending.plan.inverse != null })
                } else {
                    failure(pending.option, executed.failure.orEmpty())
                }
            mutableState.update { it.copy(message = message) }
            refresh()
        }
    }

    /** One-tap undo from the snackbar. */
    fun undo(entryId: Long) {
        viewModelScope.launch {
            val executed = actions.undo(entryId)
            val message =
                if (executed.success) {
                    ActionMessage.Undone(executed.action?.let(::touchesRestrictedSetting) == true)
                } else {
                    ActionMessage.UndoFailed(executed.failure.orEmpty())
                }
            mutableState.update { it.copy(message = message) }
            refresh()
        }
    }

    fun dismissPending() = mutableState.update { it.copy(pending = null) }

    fun consumeMessage() = mutableState.update { it.copy(message = null) }

    private fun failure(
        option: ActionOption,
        reason: String,
    ): ActionMessage.Failed {
        val action = (option.request as? ActionRequest.Plain)?.action
        val forceStop =
            if (action is Action.StopService) {
                ActionOption(
                    R.string.action_force_stop,
                    null,
                    R.string.explain_force_stop,
                    ActionRequest.Plain(Action.ForceStop(action.packageName, action.userId)),
                )
            } else {
                null
            }
        val screen =
            when (val r = option.request) {
                is ActionRequest.Plain -> SystemScreen.forAction(r.action)
                is ActionRequest.DisableAccessibility -> SystemScreen.ACCESSIBILITY
                is ActionRequest.OpenSettings -> r.screen
            }
        return ActionMessage.Failed(option.label, reason, forceStop, screen)
    }

    private companion object {
        const val DAY_MS = 24 * 3_600_000L
    }
}
