package app.brokoli5191.picket.ui.wakeups

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.format.Formatters
import app.brokoli5191.picket.core.model.WakeupKind
import app.brokoli5191.picket.ui.components.AppIcon
import app.brokoli5191.picket.ui.components.NoticeCard
import app.brokoli5191.picket.ui.components.SectionHeader
import app.brokoli5191.picket.ui.components.itemMotion
import app.brokoli5191.picket.ui.components.segmentedColors
import app.brokoli5191.picket.ui.expressive.ChoiceRow
import app.brokoli5191.picket.ui.expressive.DelayedLoadingIndicator
import app.brokoli5191.picket.ui.theme.rememberHaptics

enum class WakeupWindow(
    val hours: Int,
) {
    ONE(1),
    SIX(6),
    DAY(24),
    ;

    val millis: Long get() = hours * 3_600_000L
}

/** One app in the top-offenders list, with its timeline as fractions of the window (0 = start). */
data class OffenderRow(
    val uid: Int,
    val packageName: String?,
    val label: String,
    val alarmWakeups: Int,
    val wakelockCount: Int,
    val wakelockMs: Long,
    val jobCount: Int,
    val marks: List<Pair<Float, WakeupKind>>,
)

data class WakeupsUiState(
    val loading: Boolean = true,
    val window: WakeupWindow = WakeupWindow.SIX,
    val offenders: List<OffenderRow> = emptyList(),
    /** Start of the totals (last charge), wall clock. */
    val totalsSinceMs: Long? = null,
    val failedSources: List<String> = emptyList(),
    val error: String? = null,
)

@Composable
fun WakeupsScreen(
    state: WakeupsUiState,
    onWindow: (WakeupWindow) -> Unit,
    onOpenApp: (uid: Int, packageName: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberHaptics()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.wakeups_title)) },
                subtitle = { Text(stringResource(R.string.wakeups_subtitle, state.window.hours)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "window") {
                ChoiceRow(
                    options = WakeupWindow.entries,
                    selected = state.window,
                    label = { stringResource(R.string.wakeups_window, it.hours) },
                    onSelect = { window ->
                        haptics.tick()
                        onWindow(window)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            state.error?.let {
                item(
                    key = "error",
                ) { NoticeCard(stringResource(R.string.error_title), it, error = true) }
            }
            item(key = "note") {
                val since =
                    state.totalsSinceMs?.let {
                        java.text.DateFormat
                            .getDateTimeInstance(
                                java.text.DateFormat.MEDIUM,
                                java.text.DateFormat.SHORT,
                            ).format(java.util.Date(it))
                    }
                NoticeCard(
                    stringResource(R.string.wakeups_note_title),
                    if (since !=
                        null
                    ) {
                        stringResource(R.string.wakeups_note_since, since)
                    } else {
                        stringResource(R.string.wakeups_note_unknown)
                    },
                )
            }
            if (state.failedSources.isNotEmpty()) {
                item(key = "partial") {
                    NoticeCard(
                        stringResource(R.string.watchers_partial_title),
                        stringResource(R.string.watchers_partial_body, state.failedSources.joinToString()),
                    )
                }
            }
            if (state.loading && state.offenders.isEmpty()) {
                item(key = "loading") { DelayedLoadingIndicator(Modifier.fillMaxWidth().height(160.dp)) }
            }
            if (state.offenders.isNotEmpty()) {
                item(key = "header") { SectionHeader(stringResource(R.string.wakeups_top)) }
            }
            itemsIndexed(state.offenders, key = { _, row -> row.uid to row.packageName }) { index, row ->
                SegmentedListItem(
                    onClick = { onOpenApp(row.uid, row.packageName) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = state.offenders.size),
                    colors = segmentedColors(),
                    modifier = itemMotion().padding(horizontal = 16.dp, vertical = 1.dp),
                    leadingContent = { AppIcon(row.packageName) },
                    supportingContent = {
                        Column {
                            Text(
                                stringResource(
                                    R.string.wakeups_counts,
                                    row.alarmWakeups,
                                    row.wakelockCount,
                                    Formatters.duration(row.wakelockMs),
                                    row.jobCount,
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            )
                            if (row.marks.isNotEmpty()) Timeline(row.marks)
                        }
                    },
                ) {
                    Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!state.loading && state.offenders.isEmpty() && state.error == null) {
                item(key = "empty") {
                    Row(Modifier.fillMaxWidth().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.wakeups_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Ticks across the window, coloured by kind. Drawn in one Canvas: no recomposition per mark. */
@Composable
private fun Timeline(marks: List<Pair<Float, WakeupKind>>) {
    val colors = MaterialTheme.colorScheme
    val track = colors.surfaceContainerHighest
    val kindColor: (WakeupKind) -> Color = {
        when (it) {
            WakeupKind.ALARM -> colors.primary
            WakeupKind.WAKELOCK -> colors.tertiary
            WakeupKind.JOB -> colors.secondary
            WakeupKind.PUSH -> colors.error
        }
    }
    Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(14.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        val w = 3.dp.toPx()
        marks.forEach { (fraction, kind) ->
            val x = (fraction.coerceIn(0f, 1f) * (size.width - w))
            drawRoundRect(
                kindColor(kind),
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
                cornerRadius =
                    CornerRadius(w / 2),
            )
        }
    }
}
