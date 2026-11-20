package app.brokoli5191.picket.ui.processes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brokoli5191.picket.core.ipc.ServiceError
import app.brokoli5191.picket.core.model.ProcessSample
import app.brokoli5191.picket.core.process.GroupKey
import app.brokoli5191.picket.core.process.GroupLabel
import app.brokoli5191.picket.core.process.GroupRow
import app.brokoli5191.picket.core.process.ProcessFilter
import app.brokoli5191.picket.core.process.ProcessGrouping
import app.brokoli5191.picket.core.process.ProcessTable
import app.brokoli5191.picket.core.process.SortOrder
import app.brokoli5191.picket.core.process.StableOrder
import app.brokoli5191.picket.core.process.Uids
import app.brokoli5191.picket.data.PackageCatalog
import app.brokoli5191.picket.data.ProcessFrame
import app.brokoli5191.picket.data.ProcessSampler
import app.brokoli5191.picket.data.settings.AppSettings
import app.brokoli5191.picket.privileged.client.CallTiming
import app.brokoli5191.picket.privileged.client.PrivilegedClient
import app.brokoli5191.picket.privileged.client.PrivilegedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProcessesUiState(
    val loading: Boolean = true,
    val groups: List<GroupRow> = emptyList(),
    val totalCpuPercent: Double = 0.0,
    val processCount: Int = 0,
    val sort: SortOrder = SortOrder.CPU,
    val filter: ProcessFilter = ProcessFilter(),
    val paused: Boolean = false,
    val error: ServiceError? = null,
    val timing: CallTiming? = null,
)

private data class Controls(
    val sort: SortOrder = SortOrder.CPU,
    val filter: ProcessFilter = ProcessFilter(),
    val paused: Boolean = false,
    /** True while the user touches or scrolls the list: positions stay frozen. */
    val interacting: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessesViewModel(
    client: PrivilegedClient,
    private val catalog: PackageCatalog,
    sampler: ProcessSampler,
    settings: Flow<AppSettings>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val controls = MutableStateFlow(Controls())

    @Volatile
    private var intervalMs = AppSettings.DEFAULT_SAMPLING_MS
    private var systemToggledByUser = false
    private val grouping = ProcessGrouping(catalog::uidOf)
    private val stableOrder = StableOrder<GroupKey>()
    private var lastResortMs = 0L
    private var lastSort: SortOrder? = null

    private val frames =
        combine(client.state, controls.map { it.paused }) { state, paused ->
            state is PrivilegedState.Connected &&
                !paused
        }.flatMapLatest { active ->
            if (active) sampler.frames { ProcessSampler.DEFAULT_INTERVAL_MS } else emptyFlow()
        }.flowOn(Dispatchers.Default)

    val state: StateFlow<ProcessesUiState> =
        combine(frames, controls) { frame, controls -> toUi(frame, controls) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProcessesUiState())

    init {
        viewModelScope.launch {
            settings.collect { s ->
                intervalMs = s.samplingIntervalMs
                if (!systemToggledByUser) {
                    controls.update { it.copy(filter = it.filter.copy(showSystem = s.showSystemApps)) }
                }
            }
        }
    }

    fun setSort(order: SortOrder) = controls.update { it.copy(sort = order) }

    fun setShowSystem(show: Boolean) {
        systemToggledByUser = true
        controls.update { it.copy(filter = it.filter.copy(showSystem = show)) }
    }

    fun setQuery(query: String) = controls.update { it.copy(filter = it.filter.copy(query = query)) }

    fun setPaused(paused: Boolean) = controls.update { it.copy(paused = paused) }

    fun setInteracting(interacting: Boolean) = controls.update { it.copy(interacting = interacting) }

    private fun toUi(
        frame: ProcessFrame,
        controls: Controls,
    ): ProcessesUiState {
        val snapshot =
            frame.snapshot ?: return ProcessesUiState(
                loading = frame.error == null,
                sort = controls.sort,
                filter = controls.filter,
                paused = controls.paused,
                error = frame.error,
                timing = frame.timing,
            )
        val rows = ProcessTable.build(snapshot, frame.cpu, grouping.group(snapshot.processes), ::labelOf)
        val now = nowMs()
        val resort =
            !controls.interacting &&
                (controls.sort != lastSort || now - lastResortMs >= RESORT_INTERVAL_MS)
        if (resort) {
            lastResortMs = now
            lastSort = controls.sort
        }
        val ordered =
            stableOrder.apply(
                rows = ProcessTable.filter(rows, controls.filter),
                key = GroupRow::key,
                comparator = ProcessTable.comparator(controls.sort),
                resortAllowed = resort,
            )
        return ProcessesUiState(
            loading = false,
            groups = ordered,
            totalCpuPercent = frame.cpu.totalPercent,
            processCount = snapshot.processes.size,
            sort = controls.sort,
            filter = controls.filter,
            paused = controls.paused,
            error = frame.error,
            timing = frame.timing,
        )
    }

    private fun labelOf(
        key: GroupKey,
        processes: List<ProcessSample>,
    ): GroupLabel =
        when (key) {
            is GroupKey.App -> {
                val pkg = catalog.primaryPackage(key.uid, processes.map { it.name })
                val info = pkg?.let(catalog::info)
                GroupLabel(
                    title = info?.label ?: pkg ?: Uids.displayName(key.uid),
                    packageName = pkg,
                    isSystem = info?.isSystem ?: false,
                )
            }

            is GroupKey.SystemUid -> {
                GroupLabel(Uids.displayName(key.uid), null, isSystem = true)
            }

            GroupKey.Kernel -> {
                GroupLabel(KERNEL_TITLE, null, isSystem = true)
            }

            GroupKey.UnattributedIsolated -> {
                GroupLabel(ISOLATED_TITLE, null, isSystem = false)
            }
        }

    companion object {
        const val RESORT_INTERVAL_MS = 5_000L
        private const val STOP_TIMEOUT_MS = 5_000L

        // Group titles that are not app labels; the UI replaces them with string resources.
        const val KERNEL_TITLE = "kernel"
        const val ISOLATED_TITLE = "isolated"
    }
}
