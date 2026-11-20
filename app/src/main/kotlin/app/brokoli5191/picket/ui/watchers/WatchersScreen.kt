package app.brokoli5191.picket.ui.watchers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.model.WatcherKind
import app.brokoli5191.picket.ui.components.AppIcon
import app.brokoli5191.picket.ui.components.NoticeCard
import app.brokoli5191.picket.ui.components.SectionHeader
import app.brokoli5191.picket.ui.components.segmentedColors
import app.brokoli5191.picket.ui.expressive.BadgeShape
import app.brokoli5191.picket.ui.expressive.DelayedLoadingIndicator
import app.brokoli5191.picket.ui.expressive.ShapeContainer

/** One app holding one hook, resolved for display. */
data class WatcherItem(
    val kind: WatcherKind,
    val packageName: String,
    val label: String,
    val component: String?,
    val detail: String?,
    val active: Boolean,
    /** Not installed from a known store (installer missing or unknown). */
    val sideloaded: Boolean,
    val uid: Int,
)

data class WatcherGroup(
    val kind: WatcherKind,
    val items: List<WatcherItem>,
)

data class WatchersUiState(
    val loading: Boolean = true,
    val groups: List<WatcherGroup> = emptyList(),
    val failedSources: List<String> = emptyList(),
    val error: String? = null,
)

/** Plain-language presentation of each hook kind. */
enum class WatcherPresentation(
    val kind: WatcherKind,
    @param:StringRes val title: Int,
    @param:StringRes val explanation: Int,
    @param:DrawableRes val icon: Int,
    val shape: BadgeShape,
) {
    NOTIFICATIONS(
        WatcherKind.NOTIFICATION_LISTENER,
        R.string.watcher_notifications,
        R.string.watcher_notifications_explain,
        R.drawable.ic_notifications,
        BadgeShape.COOKIE,
    ),
    ACCESSIBILITY(
        WatcherKind.ACCESSIBILITY_SERVICE,
        R.string.watcher_accessibility,
        R.string.watcher_accessibility_explain,
        R.drawable.ic_accessibility_new,
        BadgeShape.SOFT_BURST,
    ),
    ADMIN(
        WatcherKind.DEVICE_ADMIN,
        R.string.watcher_admin,
        R.string.watcher_admin_explain,
        R.drawable.ic_admin_panel_settings,
        BadgeShape.GEM,
    ),
    IME(
        WatcherKind.INPUT_METHOD,
        R.string.watcher_ime,
        R.string.watcher_ime_explain,
        R.drawable.ic_keyboard,
        BadgeShape.PILL,
    ),
    USAGE(
        WatcherKind.USAGE_ACCESS,
        R.string.watcher_usage,
        R.string.watcher_usage_explain,
        R.drawable.ic_query_stats,
        BadgeShape.CLOVER,
    ),
    OVERLAY(
        WatcherKind.OVERLAY,
        R.string.watcher_overlay,
        R.string.watcher_overlay_explain,
        R.drawable.ic_layers,
        BadgeShape.SQUARE,
    ),
    VPN(WatcherKind.VPN, R.string.watcher_vpn, R.string.watcher_vpn_explain, R.drawable.ic_vpn_key, BadgeShape.ARCH),
    ROLES(
        WatcherKind.DEFAULT_ROLE,
        R.string.watcher_roles,
        R.string.watcher_roles_explain,
        R.drawable.ic_badge,
        BadgeShape.SUNNY,
    ),
    DND(
        WatcherKind.DND_ACCESS,
        R.string.watcher_dnd,
        R.string.watcher_dnd_explain,
        R.drawable.ic_do_not_disturb_on,
        BadgeShape.COOKIE,
    ),
    ;

    companion object {
        fun of(kind: WatcherKind): WatcherPresentation = entries.first { it.kind == kind }
    }
}

@Composable
fun WatchersScreen(
    state: WatchersUiState,
    onOpenApp: (uid: Int, packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val total = state.groups.sumOf { it.items.size }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.watchers_title)) },
                subtitle = { Text(pluralStringResource(R.plurals.watchers_count, total, total)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.loading && state.groups.isEmpty()) {
            DelayedLoadingIndicator(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.watchers_intro),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let { message ->
                item(key = "error") { NoticeCard(stringResource(R.string.error_title), message, error = true) }
            }
            if (state.failedSources.isNotEmpty()) {
                item(key = "partial") {
                    NoticeCard(
                        stringResource(R.string.watchers_partial_title),
                        stringResource(R.string.watchers_partial_body, state.failedSources.joinToString()),
                    )
                }
            }
            state.groups.forEach { group ->
                val presentation = WatcherPresentation.of(group.kind)
                item(key = "header-${group.kind}") {
                    Column(Modifier.animateItem()) {
                        SectionHeader(stringResource(presentation.title))
                        Text(
                            stringResource(presentation.explanation),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(group.items, key = {
                    _,
                    it,
                    ->
                    "${group.kind}-${it.packageName}-${it.component}-${it.detail}"
                }) { index, item ->
                    WatcherRow(
                        item = item,
                        presentation = presentation,
                        index = index,
                        count = group.items.size,
                        onClick = { onOpenApp(item.uid, item.packageName) },
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WatcherRow(
    item: WatcherItem,
    presentation: WatcherPresentation,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentedColors(),
        modifier = modifier,
        leadingContent = { AppIcon(item.packageName) },
        supportingContent = {
            Text(item.component ?: item.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.sideloaded) {
                    AssistChip(onClick = onClick, label = { Text(stringResource(R.string.watcher_sideloaded)) })
                }
                ShapeContainer(presentation.shape) {
                    Icon(painterResource(presentation.icon), contentDescription = stringResource(presentation.title))
                }
            }
        },
        overlineContent = {
            val qualifier =
                listOfNotNull(
                    item.detail,
                    if (!item.active) stringResource(R.string.watcher_inactive) else null,
                ).joinToString(stringResource(R.string.separator))
            if (qualifier.isNotEmpty()) Text(qualifier)
        },
    ) {
        Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
