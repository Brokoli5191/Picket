package app.brokoli5191.picket.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.brokoli5191.picket.AppContainer
import app.brokoli5191.picket.data.ProcessSampler
import app.brokoli5191.picket.ui.detail.AppDetailViewModel
import app.brokoli5191.picket.ui.navigation.AppDetailRoute
import app.brokoli5191.picket.ui.processes.ProcessesViewModel
import app.brokoli5191.picket.ui.wakeups.WakeupsViewModel
import app.brokoli5191.picket.ui.watchers.WatchersViewModel

/** The process-wide [AppContainer], provided once at the root of the composition. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** ViewModel factories wired from the [AppContainer] (manual DI). */
object AppViewModels {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ProcessesViewModel(
                    client = container.privilegedClient,
                    catalog = container.packageCatalog,
                    sampler = ProcessSampler(container.privilegedClient),
                    settings = container.settingsRepository.settings,
                )
            }
            initializer { WatchersViewModel(container.watchersRepository, container.packageCatalog) }
            initializer { WakeupsViewModel(container.wakeupsRepository, container.packageCatalog) }
        }

    fun detailFactory(
        container: AppContainer,
        route: AppDetailRoute,
    ): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                AppDetailViewModel(
                    uid = route.uid,
                    packageName = route.packageName,
                    client = container.privilegedClient,
                    catalog = container.packageCatalog,
                    details = container.appDetailRepository,
                    watchers = container.watchersRepository,
                    wakeups = container.wakeupsRepository,
                )
            }
        }
}
