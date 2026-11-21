package app.brokoli5191.picket.ui.processes

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.format.Formatters
import app.brokoli5191.picket.core.process.GroupKey
import app.brokoli5191.picket.core.process.GroupRow
import app.brokoli5191.picket.core.process.Importance
import app.brokoli5191.picket.core.process.ProcessRow
import app.brokoli5191.picket.core.process.SortOrder
import app.brokoli5191.picket.ui.components.AppIcon
import app.brokoli5191.picket.ui.components.ChoiceMenu
import app.brokoli5191.picket.ui.components.ExpandIcon
import app.brokoli5191.picket.ui.components.ExpandableContent
import app.brokoli5191.picket.ui.components.NoticeCard
import app.brokoli5191.picket.ui.components.PopContent
import app.brokoli5191.picket.ui.components.RollingNumber
import app.brokoli5191.picket.ui.components.itemMotion
import app.brokoli5191.picket.ui.components.segmentedColors
import app.brokoli5191.picket.ui.expressive.DelayedLoadingIndicator
import app.brokoli5191.picket.ui.expressive.LabelToggle
import app.brokoli5191.picket.ui.expressive.SearchTopBar
import app.brokoli5191.picket.ui.expressive.rememberSearchTopBarScroll
import app.brokoli5191.picket.ui.theme.rememberHaptics

@Composable
fun ProcessesScreen(
    state: ProcessesUiState,
    onSort: (SortOrder) -> Unit,
    onShowSystem: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onPause: (Boolean) -> Unit,
    onInteracting: (Boolean) -> Unit,
    onOpenApp: (GroupRow) -> Unit,
    modifier: Modifier = Modifier,
    showDebug: Boolean = false,
) {
    val listState = rememberLazyListState()
    val expanded = remember { mutableStateMapOf<GroupKey, Boolean>() }
    val haptics = rememberHaptics()
    val searchScroll = rememberSearchTopBarScroll()
    val focus = LocalFocusManager.current
    LaunchedEffect(listState.isScrollInProgress) {
        onInteracting(listState.isScrollInProgress)
        // Scrolling through the matches puts the keyboard away.
        if (listState.isScrollInProgress) focus.clearFocus()
    }

    Scaffold(
        modifier = modifier.nestedScroll(searchScroll.nestedScrollConnection),
        topBar = {
            SearchTopBar(
                query = state.filter.query,
                onQueryChange = onQuery,
                placeholder = stringResource(R.string.processes_search),
                clearLabel = stringResource(R.string.processes_search_clear),
                scroll = searchScroll,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading && state.groups.isEmpty()) {
                DelayedLoadingIndicator(Modifier.fillMaxSize())
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 112.dp),
                modifier =
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        // Freeze the order while a finger rests on the list; scrolling is tracked above.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            onInteracting(true)
                            waitForUpOrCancellation(PointerEventPass.Initial)
                            onInteracting(false)
                        }
                    },
            ) {
                item(key = "header") {
                    Header(state, onSort, showDebug, haptics::tick)
                }
                itemsIndexed(state.groups, key = { _, group -> group.key.toString() }) { index, group ->
                    GroupItem(
                        group = group,
                        index = index,
                        count = state.groups.size,
                        expanded = expanded[group.key] == true,
                        onToggle = {
                            val next = expanded[group.key] != true
                            haptics.toggle(next)
                            expanded[group.key] = next
                        },
                        onOpen = { onOpenApp(group) },
                        modifier = itemMotion().padding(horizontal = 16.dp, vertical = 1.dp),
                    )
                }
            }
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                IconToggleButton(
                    checked = state.paused,
                    onCheckedChange = {
                        haptics.toggle(it)
                        onPause(it)
                    },
                ) {
                    Icon(
                        painterResource(if (state.paused) R.drawable.ic_play_arrow else R.drawable.ic_pause),
                        contentDescription =
                            stringResource(
                                if (state.paused) R.string.processes_resume else R.string.processes_pause,
                            ),
                    )
                }
                val showSystem = stringResource(R.string.processes_show_system)
                LabelToggle(
                    checked = state.filter.showSystem,
                    onCheckedChange = {
                        haptics.toggle(it)
                        onShowSystem(it)
                    },
                    label = stringResource(R.string.processes_system),
                    modifier = Modifier.semantics { contentDescription = showSystem },
                )
            }
        }
    }
}

/** Totals, the paused state and the sort order, above the list and scrolling with it. */
@Composable
private fun Header(
    state: ProcessesUiState,
    onSort: (SortOrder) -> Unit,
    showDebug: Boolean,
    tick: () -> Unit,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Separate items, so that at large font sizes the count moves to the next line as a whole
            // instead of being squeezed into a column beside the CPU figure.
            FlowRow(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                RollingNumber(
                    stringResource(R.string.processes_cpu_total, Formatters.percent(state.totalCpuPercent)),
                    state.totalCpuPercent,
                    MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(R.string.separator) +
                        pluralStringResource(R.plurals.processes_total, state.processCount, state.processCount),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                )
                PopContent(visible = state.paused) { PausedBadge(Modifier.padding(start = 8.dp)) }
            }
            SortButton(state.sort, onSort, tick)
        }
        state.error?.let { NoticeCard(stringResource(R.string.error_title), it.message, error = true) }
        if (showDebug) {
            state.timing?.let { t ->
                Text(
                    stringResource(
                        R.string.debug_sampling_cost,
                        t.roundTripMicros / 1000.0,
                        t.serviceCpuMicros / 1000.0,
                        t.payloadBytes / 1024.0,
                        t.filesRead,
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}

@Composable
private fun PausedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_pause), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.processes_paused), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SortButton(
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    tick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.processes_sort, sortLabel(sort)))
            ExpandIcon(open, decorative = true)
        }
        ChoiceMenu(
            expanded = open,
            onDismiss = { open = false },
            options = SortOrder.entries,
            selected = sort,
            label = { sortLabel(it) },
            onSelect = { order ->
                tick()
                onSort(order)
                open = false
            },
        )
    }
}

@Composable
private fun GroupItem(
    group: GroupRow,
    index: Int,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SegmentedListItem(
            onClick = onOpen,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            colors = segmentedColors(),
            leadingContent = { AppIcon(group.label.packageName) },
            overlineContent = { Text(stringResource(importanceLabel(group.importance))) },
            supportingContent = {
                Text(
                    group.label.packageName
                        ?: pluralStringResource(R.plurals.processes_total, group.processes.size, group.processes.size),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        RollingNumber(
                            Formatters.percent(group.cpuPercent),
                            group.cpuPercent,
                            MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            Formatters.bytes(group.rssBytes),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                    IconButton(onClick = onToggle) { ExpandIcon(expanded) }
                }
            },
        ) {
            Text(groupTitle(group), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ExpandableContent(expanded) {
            Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp)) {
                group.processes.forEach { ProcessLine(it) }
            }
        }
    }
}

@Composable
private fun ProcessLine(process: ProcessRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                process.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    R.string.process_meta,
                    process.pid,
                    process.userName,
                    Formatters.duration(process.uptimeMs),
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(
                Formatters.percent(process.cpuPercent),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                Formatters.bytes(process.rssBytes),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
fun groupTitle(group: GroupRow): String =
    when (group.key) {
        GroupKey.Kernel -> stringResource(R.string.group_kernel)
        GroupKey.UnattributedIsolated -> stringResource(R.string.group_isolated)
        is GroupKey.SystemUid -> stringResource(R.string.group_system_uid, group.label.title)
        is GroupKey.App -> group.label.title
    }

@Composable
private fun sortLabel(order: SortOrder): String =
    stringResource(
        when (order) {
            SortOrder.CPU -> R.string.sort_cpu
            SortOrder.MEMORY -> R.string.sort_memory
            SortOrder.NAME -> R.string.sort_name
            SortOrder.UPTIME -> R.string.sort_uptime
        },
    )

fun importanceLabel(importance: Importance): Int =
    when (importance) {
        Importance.SYSTEM -> R.string.importance_system
        Importance.FOREGROUND -> R.string.importance_foreground
        Importance.VISIBLE -> R.string.importance_visible
        Importance.PERCEPTIBLE -> R.string.importance_perceptible
        Importance.SERVICE -> R.string.importance_service
        Importance.CACHED -> R.string.importance_cached
        Importance.UNKNOWN -> R.string.importance_unknown
    }
