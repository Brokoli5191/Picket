package app.brokoli5191.picket.ui.connection

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.privileged.client.PrivilegedState
import app.brokoli5191.picket.ui.components.ExpandableContent
import app.brokoli5191.picket.ui.expressive.BadgeShape
import app.brokoli5191.picket.ui.expressive.DelayedLoadingIndicator
import app.brokoli5191.picket.ui.expressive.badgeShape
import kotlinx.coroutines.delay

/** After this long in "Connecting", the screen offers to start over instead of only waiting. */
private const val SLOW_AFTER_MS = 4_000L

private val MarkerSize = 32.dp
private val MarkerGap = 16.dp

/**
 * Setup as three steps (install, start, allow) with the action for the one that is next, followed
 * by the helper's own state once they are done. Has its own top app bar, so it sits below the
 * status bar like every other screen.
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
            val statuses = stepStatuses(state)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SetupStep.entries.forEachIndexed { index, step ->
                        StepRow(
                            number = index + 1,
                            title = stringResource(stepTitle(step, state)),
                            status = statuses.getValue(step),
                        ) { StepAction(step, state, onRequestPermission, onOpenShizuku, onRetry) }
                    }
                }
            }
            HelperStatus(state, onRetry)
            Text(stringResource(R.string.connection_why), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    title: String,
    status: StepStatus,
    content: @Composable () -> Unit,
) {
    val spoken =
        stringResource(
            when (status) {
                StepStatus.DONE -> R.string.connection_step_done
                StepStatus.CURRENT -> R.string.connection_step_current
                StepStatus.PENDING -> R.string.connection_step_pending
            },
        )
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(
            Modifier.semantics(mergeDescendants = true) { stateDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(MarkerGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepMarker(number, status)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (status == StepStatus.PENDING) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
        ExpandableContent(visible = status == StepStatus.CURRENT) {
            Column(
                Modifier.padding(start = MarkerSize + MarkerGap, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

/** A check once done, the number in an Expressive cookie shape while current, an outline before. */
@Composable
private fun StepMarker(
    number: Int,
    status: StepStatus,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            val enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f)
            enter togetherWith fadeOut(motion.fastEffectsSpec())
        },
        label = "step marker",
    ) { shown ->
        when (shown) {
            StepStatus.DONE -> {
                Marker(CircleShape, colors.primary, colors.onPrimary) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            StepStatus.CURRENT -> {
                Marker(badgeShape(BadgeShape.COOKIE), colors.primaryContainer, colors.onPrimaryContainer) {
                    Text(number.toString(), style = MaterialTheme.typography.labelLarge)
                }
            }

            StepStatus.PENDING -> {
                Marker(
                    CircleShape,
                    Color.Transparent,
                    colors.onSurfaceVariant,
                    BorderStroke(1.dp, colors.outlineVariant),
                ) { Text(number.toString(), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun Marker(
    shape: Shape,
    color: Color,
    contentColor: Color,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(Modifier.size(MarkerSize), shape = shape, color = color, contentColor = contentColor, border = border) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** What the user does for [step]; [state] only picks between two variants of the same step. */
@Composable
private fun StepAction(
    step: SetupStep,
    state: PrivilegedState,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRetry: () -> Unit,
) {
    when (step) {
        SetupStep.INSTALL -> {
            if (state is PrivilegedState.Unsupported) {
                Body(R.string.connection_body_unsupported)
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.connection_action_retry)) }
            } else {
                Body(R.string.connection_body_not_installed)
                Button(onClick = onOpenShizuku) { Text(stringResource(R.string.connection_action_get_shizuku)) }
            }
        }

        SetupStep.START -> {
            Body(R.string.connection_body_not_running)
            Button(onClick = onOpenShizuku) { Text(stringResource(R.string.connection_action_open_shizuku)) }
        }

        SetupStep.ALLOW -> {
            if (state == PrivilegedState.PermissionDenied) {
                Body(R.string.connection_body_denied)
                Button(onClick = onOpenShizuku) { Text(stringResource(R.string.connection_action_open_shizuku)) }
            } else {
                Body(R.string.connection_body_permission)
                Button(onClick = onRequestPermission) { Text(stringResource(R.string.connection_action_grant)) }
            }
        }
    }
}

@Composable
private fun Body(
    @StringRes text: Int,
) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}

private fun stepTitle(
    step: SetupStep,
    state: PrivilegedState,
): Int =
    when (step) {
        SetupStep.INSTALL -> {
            if (state is PrivilegedState.Unsupported) {
                R.string.connection_step_update
            } else {
                R.string.connection_step_install
            }
        }

        SetupStep.START -> {
            R.string.connection_step_start
        }

        SetupStep.ALLOW -> {
            R.string.connection_step_allow
        }
    }

/** After the three steps: the helper connecting, with a way out if that hangs, or its failure. */
@Composable
private fun HelperStatus(
    state: PrivilegedState,
    onRetry: () -> Unit,
) {
    val waiting = state is PrivilegedState.Checking || state is PrivilegedState.Connecting
    ExpandableContent(visible = waiting || state is PrivilegedState.Failed) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state is PrivilegedState.Failed) {
                    Text(
                        stringResource(R.string.connection_title_failed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(stringResource(R.string.connection_body_failed), style = MaterialTheme.typography.bodyMedium)
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.connection_action_retry)) }
                } else {
                    Text(
                        stringResource(R.string.connection_title_connecting),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.connection_body_connecting),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    DelayedLoadingIndicator(Modifier.fillMaxWidth())
                    SlowConnectionHint(waiting, onRetry)
                }
            }
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
