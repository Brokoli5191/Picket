package app.brokoli5191.picket.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.privileged.client.PrivilegedState

/** Explains the current Shizuku/Sui state and offers the one action that moves it forward. */
@Composable
fun ConnectionScreen(
    state: PrivilegedState,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(titleOf(state)), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(bodyOf(state)), style = MaterialTheme.typography.bodyLarge)
                if (state is PrivilegedState.Failed) {
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
                when (state) {
                    PrivilegedState.PermissionRequired -> {
                        Button(onClick = onRequestPermission) { Text(stringResource(R.string.connection_action_grant)) }
                    }

                    PrivilegedState.NotRunning, PrivilegedState.PermissionDenied -> {
                        Button(
                            onClick = onOpenShizuku,
                        ) { Text(stringResource(R.string.connection_action_open_shizuku)) }
                    }

                    is PrivilegedState.Failed, is PrivilegedState.Unsupported, PrivilegedState.NotInstalled -> {
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.connection_action_retry)) }
                    }

                    PrivilegedState.Checking, PrivilegedState.Connecting, is PrivilegedState.Connected -> {}
                }
            }
        }
        Text(stringResource(R.string.connection_why), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun titleOf(state: PrivilegedState): Int =
    when (state) {
        PrivilegedState.Checking, PrivilegedState.Connecting -> R.string.connection_title_connecting
        PrivilegedState.NotInstalled -> R.string.connection_title_not_installed
        PrivilegedState.NotRunning -> R.string.connection_title_not_running
        is PrivilegedState.Unsupported -> R.string.connection_title_unsupported
        PrivilegedState.PermissionRequired -> R.string.connection_title_permission
        PrivilegedState.PermissionDenied -> R.string.connection_title_denied
        is PrivilegedState.Connected -> R.string.connection_title_connected
        is PrivilegedState.Failed -> R.string.connection_title_failed
    }

private fun bodyOf(state: PrivilegedState): Int =
    when (state) {
        PrivilegedState.Checking, PrivilegedState.Connecting -> R.string.connection_body_connecting
        PrivilegedState.NotInstalled -> R.string.connection_body_not_installed
        PrivilegedState.NotRunning -> R.string.connection_body_not_running
        is PrivilegedState.Unsupported -> R.string.connection_body_unsupported
        PrivilegedState.PermissionRequired -> R.string.connection_body_permission
        PrivilegedState.PermissionDenied -> R.string.connection_body_denied
        is PrivilegedState.Connected -> R.string.connection_body_connected
        is PrivilegedState.Failed -> R.string.connection_body_failed
    }
