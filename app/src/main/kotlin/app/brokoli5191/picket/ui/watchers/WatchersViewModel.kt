package app.brokoli5191.picket.ui.watchers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brokoli5191.picket.core.model.WatcherKind
import app.brokoli5191.picket.core.model.WatchersSnapshot
import app.brokoli5191.picket.data.PackageCatalog
import app.brokoli5191.picket.data.WatchersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchersViewModel(
    private val repository: WatchersRepository,
    private val catalog: PackageCatalog,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WatchersUiState())
    val state: StateFlow<WatchersUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val reply = repository.load()
            val snapshot = reply.value
            mutableState.value =
                if (snapshot == null) {
                    WatchersUiState(loading = false, error = reply.error?.message)
                } else {
                    withContext(Dispatchers.Default) { toUi(snapshot) }
                }
        }
    }

    private fun toUi(snapshot: WatchersSnapshot): WatchersUiState {
        val groups =
            WatcherKind.entries.mapNotNull { kind ->
                val items =
                    snapshot.entries
                        .filter { it.kind == kind }
                        .map { entry ->
                            val info = catalog.info(entry.packageName)
                            WatcherItem(
                                kind = kind,
                                packageName = entry.packageName,
                                label = info?.label ?: entry.packageName,
                                component = entry.component,
                                detail = entry.detail,
                                active = entry.active,
                                sideloaded = info != null && !info.isSystem && info.installer !in KNOWN_STORES,
                                uid = info?.uid ?: -1,
                            )
                        }.sortedWith(compareBy({ !it.active }, { it.label.lowercase() }))
                if (items.isEmpty()) null else WatcherGroup(kind, items)
            }
        return WatchersUiState(
            loading = false,
            groups = groups,
            failedSources = snapshot.sources.filterNot { it.ok }.map { it.source },
        )
    }

    companion object {
        /** Installers treated as known stores; anything else (or none) counts as sideloaded. */
        val KNOWN_STORES =
            setOf(
                "com.android.vending",
                "com.sec.android.app.samsungapps",
                "org.fdroid.fdroid",
                "com.looker.droidify",
                "com.machiav3lli.fdroid",
                "dev.imranr.obtainium",
                "com.aurora.store",
                "com.huawei.appmarket",
                "com.amazon.venezia",
            )
    }
}
