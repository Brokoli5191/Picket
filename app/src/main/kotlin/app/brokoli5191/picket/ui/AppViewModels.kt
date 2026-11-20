package app.brokoli5191.picket.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.brokoli5191.picket.AppContainer
import app.brokoli5191.picket.data.ProcessSampler
import app.brokoli5191.picket.ui.processes.ProcessesViewModel

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
                )
            }
        }
}
