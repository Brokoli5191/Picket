package app.brokoli5191.picket.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.format.Formatters
import app.brokoli5191.picket.core.model.ForegroundServiceTypes
import app.brokoli5191.picket.core.model.IdleExemption
import app.brokoli5191.picket.core.process.Uids
import app.brokoli5191.picket.ui.components.AppIcon
import app.brokoli5191.picket.ui.components.NoticeCard
import app.brokoli5191.picket.ui.components.SectionHeader
import app.brokoli5191.picket.ui.components.segmentedColors
import app.brokoli5191.picket.ui.expressive.DelayedLoadingIndicator
import app.brokoli5191.picket.ui.watchers.WatcherPresentation

@Composable
fun AppDetailScreen(
    state: AppDetailUiState,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    actions: LazyListScope.() -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = state.info?.label ?: state.packageName ?: Uids.displayName(state.uid)
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    Text(
                        state.packageName ?: Uids.displayName(state.uid),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            painterResource(R.drawable.ic_restart_alt),
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "head") { Header(state) }
            if (state.loading && state.detail == null) {
                item(key = "loading") { DelayedLoadingIndicator(Modifier.fillMaxWidth().padding(24.dp)) }
            }
            state.errors.forEach { message ->
                item { NoticeCard(stringResource(R.string.error_title), message, error = true) }
            }

            actions()

            if (state.watchers.isNotEmpty()) {
                item(key = "watchers") { SectionHeader(stringResource(R.string.detail_watchers)) }
                itemsIndexed(state.watchers) { index, entry ->
                    val p = WatcherPresentation.of(entry.kind)
                    Row(index, state.watchers.size, stringResource(p.title), entry.component ?: entry.detail.orEmpty())
                }
            }

            item(key = "processes") { SectionHeader(stringResource(R.string.detail_processes)) }
            if (state.processes.isEmpty() && !state.loading) item { Hint(stringResource(R.string.detail_no_processes)) }
            itemsIndexed(state.processes, key = { _, p -> "p${p.pid}-${p.key.startTicks}" }) { index, p ->
                Row(
                    index,
                    state.processes.size,
                    p.name,
                    stringResource(R.string.process_meta, p.pid, p.userName, Formatters.duration(p.uptimeMs)),
                    trailing = Formatters.bytes(p.rssBytes),
                )
            }

            val detail = state.detail
            if (detail != null) {
                item(key = "services") { SectionHeader(stringResource(R.string.detail_services)) }
                if (detail.services.isEmpty()) item { Hint(stringResource(R.string.detail_no_services)) }
                itemsIndexed(detail.services) { index, s ->
                    val kinds =
                        buildList {
                            if (s.started) add(stringResource(R.string.service_started))
                            if (s.bound) add(stringResource(R.string.service_bound))
                            if (s.foreground) {
                                val types = s.foregroundTypes?.let(ForegroundServiceTypes::decode).orEmpty()
                                add(
                                    stringResource(R.string.service_foreground) +
                                        if (types.isEmpty()) "" else " (${types.joinToString()})",
                                )
                            }
                        }
                    Row(
                        index,
                        detail.services.size,
                        s.component.substringAfter('/'),
                        kinds.joinToString(stringResource(R.string.separator)),
                    )
                }

                item(key = "power") { SectionHeader(stringResource(R.string.detail_power)) }
                item {
                    Row(
                        0,
                        2,
                        stringResource(R.string.detail_standby_bucket),
                        detail.standbyBucket?.shellName ?: stringResource(R.string.unknown),
                    )
                }
                item {
                    Row(
                        1,
                        2,
                        stringResource(R.string.detail_battery_exemption),
                        stringResource(
                            when (detail.idleExemption) {
                                IdleExemption.USER -> R.string.exemption_user
                                IdleExemption.SYSTEM -> R.string.exemption_system
                                IdleExemption.NONE -> R.string.exemption_none
                                null -> R.string.unknown
                            },
                        ),
                    )
                }

                val granted = detail.runtimePermissions.filter { it.granted }
                item(key = "perms") { SectionHeader(stringResource(R.string.detail_permissions)) }
                if (granted.isEmpty()) item { Hint(stringResource(R.string.detail_no_permissions)) }
                itemsIndexed(granted) { index, perm ->
                    Row(index, granted.size, perm.name.substringAfterLast('.'), perm.name)
                }

                val ops = detail.appOps.filter { it.mode != "default" }
                if (ops.isNotEmpty()) {
                    item(key = "ops") { SectionHeader(stringResource(R.string.detail_appops)) }
                    itemsIndexed(ops) { index, op ->
                        Row(
                            index,
                            ops.size,
                            op.op,
                            op.mode + (op.lastAccess?.let { " · $it" } ?: ""),
                        )
                    }
                }
            }

            state.wakeups?.let { w ->
                item(key = "wakeups") { SectionHeader(stringResource(R.string.detail_wakeups)) }
                item {
                    Row(
                        0,
                        1,
                        stringResource(R.string.detail_wakeups_24h),
                        stringResource(
                            R.string.wakeups_counts,
                            w.alarmWakeups,
                            w.wakelockCount,
                            Formatters.duration(w.wakelockMs),
                            w.jobCount,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: AppDetailUiState) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(state.packageName)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.detail_uid, Uids.displayName(state.uid), state.uid),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.info?.let { info ->
                Text(
                    stringResource(
                        R.string.detail_installer,
                        info.installer
                            ?: stringResource(
                                if (info.isSystem) R.string.installer_preinstalled else R.string.installer_unknown,
                            ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Row(
    index: Int,
    count: Int,
    headline: String,
    supporting: String,
    trailing: String? = null,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentedColors(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
        supportingContent = {
            if (supporting.isNotEmpty()) {
                Text(
                    supporting,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent =
            trailing?.let {
                { Text(it, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")) }
            },
    ) {
        Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
