package app.brokoli5191.picket.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.brokoli5191.picket.R
import app.brokoli5191.picket.privileged.client.PrivilegedState
import app.brokoli5191.picket.ui.connection.ConnectionScreen
import app.brokoli5191.picket.ui.processes.ProcessesScreen
import app.brokoli5191.picket.ui.processes.ProcessesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicketApp(
    onOpenShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val client = container.privilegedClient
    val connection by client.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        if (connection is PrivilegedState.Connected) {
            val vm: ProcessesViewModel = viewModel(factory = AppViewModels.factory(container))
            val state by vm.state.collectAsStateWithLifecycle()
            ProcessesScreen(
                state = state,
                onSort = vm::setSort,
                onShowSystem = vm::setShowSystem,
                onQuery = vm::setQuery,
                onPause = vm::setPaused,
                onInteracting = vm::setInteracting,
                modifier = Modifier.padding(padding),
            )
        } else {
            ConnectionScreen(
                state = connection,
                onRequestPermission = client::requestPermission,
                onOpenShizuku = onOpenShizuku,
                onRetry = client::evaluate,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
