package app.brokoli5191.picket.ui.wakeups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brokoli5191.picket.core.model.AppWakeups
import app.brokoli5191.picket.core.model.WakeupReport
import app.brokoli5191.picket.data.PackageCatalog
import app.brokoli5191.picket.data.WakeupsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WakeupsViewModel(
    private val repository: WakeupsRepository,
    private val catalog: PackageCatalog,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WakeupsUiState())
    val state: StateFlow<WakeupsUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(WakeupWindow.SIX)
    }

    fun setWindow(window: WakeupWindow) {
        if (window != mutableState.value.window || mutableState.value.error != null) load(window)
    }

    private fun load(window: WakeupWindow) {
        loadJob?.cancel()
        mutableState.update { it.copy(loading = true, window = window, error = null) }
        loadJob =
            viewModelScope.launch {
                val reply = repository.load(window.millis)
                val report = reply.value
                mutableState.value =
                    if (report == null) {
                        WakeupsUiState(loading = false, window = window, error = reply.error?.message)
                    } else {
                        withContext(Dispatchers.Default) { toUi(report, window) }
                    }
            }
    }

    private fun toUi(
        report: WakeupReport,
        window: WakeupWindow,
    ): WakeupsUiState {
        val start = report.untilMs - report.windowMs
        val eventsByApp = report.events.groupBy { it.uid to it.packageName }
        val offenders =
            report.apps
                .filter { it.total > 0 }
                .sortedWith(compareByDescending<AppWakeups> { it.total }.thenByDescending { it.wakelockMs })
                .take(MAX_ROWS)
                .map { app ->
                    OffenderRow(
                        uid = app.uid,
                        packageName = app.packageName,
                        label = app.packageName?.let { catalog.info(it)?.label ?: it } ?: app.uid.toString(),
                        alarmWakeups = app.alarmWakeups,
                        wakelockCount = app.wakelockCount,
                        wakelockMs = app.wakelockMs,
                        jobCount = app.jobCount,
                        marks =
                            eventsByApp[app.uid to app.packageName].orEmpty().map { e ->
                                ((e.atMs - start).toFloat() / report.windowMs.coerceAtLeast(1)) to e.kind
                            },
                    )
                }
        return WakeupsUiState(
            loading = false,
            window = window,
            offenders = offenders,
            totalsSinceMs = report.totalsSinceMs,
            failedSources = report.sources.filterNot { it.ok }.map { it.source },
        )
    }

    private companion object {
        const val MAX_ROWS = 50
    }
}
