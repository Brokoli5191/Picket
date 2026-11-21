package app.brokoli5191.picket.ui.connection

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.privileged.client.PrivilegedState
import app.brokoli5191.picket.ui.components.ExpandableContent
import app.brokoli5191.picket.ui.expressive.DelayedLoadingIndicator
import kotlinx.coroutines.delay

/** After this long in "Connecting", the screen offers to start over instead of only waiting. */
private const val SLOW_AFTER_MS = 4_000L

/**
 * Explains the current Shizuku/Sui state and offers the one action that moves it forward. Has its
 * own top app bar, so it sits below the status bar like every other screen.
 */
@Composable
fun ConnectionScreen(
    state: PrivilegedState,
    @StringRes title: Int,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(title = { Text(stringResource(title)) }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StateCard(state, onRequestPermission, onOpenShizuku, onRetry)
            Text(stringResource(R.string.connection_why), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StateCard(
    state: PrivilegedState,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRetry: () -> Unit,
) {
    val waiting = state is PrivilegedState.Checking || state is PrivilegedState.Connecting
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(titleOf(state)), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(bodyOf(state)), style = MaterialTheme.typography.bodyLarge)
            if (state is PrivilegedState.Failed) {
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            }
            if (waiting) DelayedLoadingIndicator(Modifier.fillMaxWidth())
            when (state) {
                PrivilegedState.PermissionRequired -> {
                    Button(onClick = onRequestPermission) { Text(stringResource(R.string.connection_action_grant)) }
                }

                PrivilegedState.NotRunning, PrivilegedState.PermissionDenied -> {
                    Button(onClick = onOpenShizuku) { Text(stringResource(R.string.connection_action_open_shizuku)) }
                }

                is PrivilegedState.Failed, is PrivilegedState.Unsupported, PrivilegedState.NotInstalled -> {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.connection_action_retry)) }
                }

                PrivilegedState.Checking, PrivilegedState.Connecting, is PrivilegedState.Connected -> {}
            }
            SlowConnectionHint(waiting, onRetry)
        }
    }
}

/** Appears when connecting takes unusually long, with a way out that does not need an app restart. */
@Composable
private fun SlowConnectionHint(
    waiting: Boolean,
    onRetry: () -> Unit,
) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(waiting) {
        slow = false
        if (waiting) {
            delay(SLOW_AFTER_MS)
            slow = true
        }
    }
    ExpandableContent(visible = waiting && slow) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.connection_slow), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.connection_action_retry)) }
        }
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
