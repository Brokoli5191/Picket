package app.brokoli5191.picket.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import app.brokoli5191.picket.data.AppDetailRepository
import app.brokoli5191.picket.data.AppInfo
import app.brokoli5191.picket.data.PackageCatalog
import app.brokoli5191.picket.data.WakeupsRepository
import app.brokoli5191.picket.data.WatchersRepository
import app.brokoli5191.picket.privileged.client.PrivilegedClient
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

class AppDetailViewModel(
    private val uid: Int,
    private val packageName: String?,
    private val client: PrivilegedClient,
    private val catalog: PackageCatalog,
    private val details: AppDetailRepository,
    private val watchers: WatchersRepository,
    private val wakeups: WakeupsRepository,
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
                AppDetailUiState(
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

    private companion object {
        const val DAY_MS = 24 * 3_600_000L
    }
}
