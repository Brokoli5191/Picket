package app.brokoli5191.picket.ui.processes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.BuildConfig
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.format.Formatters
import app.brokoli5191.picket.core.process.GroupKey
import app.brokoli5191.picket.core.process.GroupRow
import app.brokoli5191.picket.core.process.ProcessRow
import app.brokoli5191.picket.core.process.SortOrder

@Composable
fun ProcessesScreen(
    state: ProcessesUiState,
    onSort: (SortOrder) -> Unit,
    onShowSystem: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onPause: (Boolean) -> Unit,
    onInteracting: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val expanded = remember { mutableStateMapOf<GroupKey, Boolean>() }
    LaunchedEffect(listState.isScrollInProgress) { onInteracting(listState.isScrollInProgress) }

    LazyColumn(modifier = modifier, state = listState) {
        item(key = "header") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.processes_cpu_total, Formatters.percent(state.totalCpuPercent)) +
                        " · " +
                        pluralStringResource(R.plurals.processes_total, state.processCount, state.processCount),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                )
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.processes_search)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = state.sort == order,
                            onClick = { onSort(order) },
                            label = { Text(stringResource(sortLabel(order))) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.processes_show_system), Modifier.weight(1f))
                    Switch(checked = state.filter.showSystem, onCheckedChange = onShowSystem)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onPause(!state.paused) }) {
                        Text(stringResource(if (state.paused) R.string.processes_resume else R.string.processes_pause))
                    }
                }
                state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                if (BuildConfig.DEBUG) {
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
        items(state.groups, key = { it.key.toString() }) { group ->
            GroupItem(
                group = group,
                expanded = expanded[group.key] == true,
                onToggle = { expanded[group.key] = expanded[group.key] != true },
                modifier = Modifier.animateItem(),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun GroupItem(
    group: GroupRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    groupTitle(group),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    group.label.packageName
                        ?: pluralStringResource(R.plurals.processes_total, group.processes.size, group.processes.size),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Formatters.percent(group.cpuPercent), style = numberStyle())
                Text(Formatters.bytes(group.rssBytes), style = numberStyle())
            }
        }
        if (expanded) {
            group.processes.forEach { ProcessLine(it) }
        }
    }
}

@Composable
private fun ProcessLine(process: ProcessRow) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            )
        }
        Text("${Formatters.percent(process.cpuPercent)}  ${Formatters.bytes(process.rssBytes)}", style = numberStyle())
    }
}

@Composable
private fun groupTitle(group: GroupRow): String =
    when (group.key) {
        GroupKey.Kernel -> stringResource(R.string.group_kernel)
        GroupKey.UnattributedIsolated -> stringResource(R.string.group_isolated)
        is GroupKey.SystemUid -> stringResource(R.string.group_system_uid, group.label.title)
        is GroupKey.App -> group.label.title
    }

@Composable
private fun numberStyle() = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")

private fun sortLabel(order: SortOrder): Int =
    when (order) {
        SortOrder.CPU -> R.string.sort_cpu
        SortOrder.MEMORY -> R.string.sort_memory
        SortOrder.NAME -> R.string.sort_name
        SortOrder.UPTIME -> R.string.sort_uptime
    }
