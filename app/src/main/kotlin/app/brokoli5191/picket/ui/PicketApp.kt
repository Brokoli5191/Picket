package app.brokoli5191.picket.ui

import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.brokoli5191.picket.BuildConfig
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.process.GroupKey
import app.brokoli5191.picket.data.settings.AppSettings
import app.brokoli5191.picket.privileged.client.PrivilegedState
import app.brokoli5191.picket.ui.connection.ConnectionScreen
import app.brokoli5191.picket.ui.detail.AppDetailEntry
import app.brokoli5191.picket.ui.detail.AppDetailViewModel
import app.brokoli5191.picket.ui.expressive.detailPaneMetadata
import app.brokoli5191.picket.ui.expressive.listPaneMetadata
import app.brokoli5191.picket.ui.expressive.rememberPicketSceneStrategy
import app.brokoli5191.picket.ui.journal.JournalDestination
import app.brokoli5191.picket.ui.journal.JournalViewModel
import app.brokoli5191.picket.ui.navigation.AppDetailRoute
import app.brokoli5191.picket.ui.navigation.AppearanceRoute
import app.brokoli5191.picket.ui.navigation.HowItWorksRoute
import app.brokoli5191.picket.ui.navigation.JournalRoute
import app.brokoli5191.picket.ui.navigation.LicensesRoute
import app.brokoli5191.picket.ui.navigation.ProcessesRoute
import app.brokoli5191.picket.ui.navigation.ProtectedRoute
import app.brokoli5191.picket.ui.navigation.SettingsRoute
import app.brokoli5191.picket.ui.navigation.TopLevel
import app.brokoli5191.picket.ui.navigation.WakeupsRoute
import app.brokoli5191.picket.ui.navigation.WatchersRoute
import app.brokoli5191.picket.ui.processes.ProcessesScreen
import app.brokoli5191.picket.ui.processes.ProcessesViewModel
import app.brokoli5191.picket.ui.settings.AppearanceScreen
import app.brokoli5191.picket.ui.settings.HowItWorksScreen
import app.brokoli5191.picket.ui.settings.LicensesScreen
import app.brokoli5191.picket.ui.settings.ProtectedDestination
import app.brokoli5191.picket.ui.settings.SettingsLink
import app.brokoli5191.picket.ui.settings.SettingsScreen
import app.brokoli5191.picket.ui.theme.PicketTheme
import app.brokoli5191.picket.ui.wakeups.WakeupsScreen
import app.brokoli5191.picket.ui.wakeups.WakeupsViewModel
import app.brokoli5191.picket.ui.watchers.WatchersScreen
import app.brokoli5191.picket.ui.watchers.WatchersViewModel
import kotlinx.coroutines.launch

/** Root of the UI: theme from settings, navigation bar/rail, Navigation 3 back stack with list-detail. */
@Composable
fun PicketApp(onOpenShizuku: () -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
    PicketTheme(settings.appearance) {
        val backStack = rememberNavBackStack(ProcessesRoute)
        val current = TopLevel.of(backStack.firstOrNull())
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevel.entries.forEach { destination ->
                    val selected = destination == current
                    item(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                backStack.clear()
                                backStack.add(destination.route)
                            }
                        },
                        icon = {
                            Icon(
                                painterResource(if (selected) destination.selectedIcon else destination.icon),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            },
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                sceneStrategies = listOf(rememberPicketSceneStrategy()),
                entryProvider =
                    entryProvider {
                        entry<ProcessesRoute>(metadata = listPaneMetadata()) {
                            Privileged(R.string.nav_processes, onOpenShizuku) {
                                val vm: ProcessesViewModel = viewModel(factory = AppViewModels.factory(container))
                                val state by vm.state.collectAsStateWithLifecycle()
                                ProcessesScreen(
                                    state = state,
                                    onSort = vm::setSort,
                                    onShowSystem = vm::setShowSystem,
                                    onQuery = vm::setQuery,
                                    onPause = vm::setPaused,
                                    onInteracting = vm::setInteracting,
                                    onOpenApp = { group ->
                                        ownerUid(
                                            group.key,
                                        )?.let { openDetail(backStack, AppDetailRoute(it, group.label.packageName)) }
                                    },
                                    showDebug = settings.debugOverlay || BuildConfig.DEBUG,
                                )
                            }
                        }
                        entry<AppDetailRoute>(metadata = detailPaneMetadata()) { route ->
                            Privileged(R.string.app_name, onOpenShizuku) {
                                val vm: AppDetailViewModel =
                                    viewModel(factory = AppViewModels.detailFactory(container, route))
                                AppDetailEntry(vm, onBack = { backStack.remove(route) })
                            }
                        }
                        entry<WatchersRoute>(metadata = listPaneMetadata()) {
                            Privileged(R.string.nav_watchers, onOpenShizuku) {
                                val vm: WatchersViewModel = viewModel(factory = AppViewModels.factory(container))
                                val state by vm.state.collectAsStateWithLifecycle()
                                WatchersScreen(state, onOpenApp = {
                                    uid,
                                    pkg,
                                    ->
                                    openDetail(backStack, AppDetailRoute(uid, pkg))
                                })
                            }
                        }
                        entry<WakeupsRoute>(metadata = listPaneMetadata()) {
                            Privileged(R.string.nav_wakeups, onOpenShizuku) {
                                val vm: WakeupsViewModel = viewModel(factory = AppViewModels.factory(container))
                                val state by vm.state.collectAsStateWithLifecycle()
                                WakeupsScreen(state, vm::setWindow, onOpenApp = {
                                    uid,
                                    pkg,
                                    ->
                                    openDetail(backStack, AppDetailRoute(uid, pkg))
                                })
                            }
                        }
                        entry<SettingsRoute> {
                            SettingsEntry(settings) { backStack.add(it) }
                        }
                        entry<AppearanceRoute> {
                            val scope = rememberCoroutineScope()
                            AppearanceScreen(
                                appearance = settings.appearance,
                                dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                onChange = { change ->
                                    scope.launch { container.settingsRepository.updateAppearance(change) }
                                },
                                onBack = { backStack.remove(AppearanceRoute) },
                            )
                        }
                        entry<JournalRoute> {
                            // Readable and exportable without the service; undo reports if it is not connected.
                            val vm: JournalViewModel = viewModel(factory = AppViewModels.factory(container))
                            JournalDestination(vm, onBack = { backStack.remove(JournalRoute) })
                        }
                        entry<ProtectedRoute> { ProtectedDestination(onBack = { backStack.remove(ProtectedRoute) }) }
                        entry<HowItWorksRoute> { HowItWorksScreen(onBack = { backStack.remove(HowItWorksRoute) }) }
                        entry<LicensesRoute> { LicensesScreen(onBack = { backStack.remove(LicensesRoute) }) }
                    },
            )
        }
    }
}

private fun ownerUid(key: GroupKey): Int? =
    when (key) {
        is GroupKey.App -> key.uid
        is GroupKey.SystemUid -> key.uid
        else -> null
    }

/** Replaces any open detail so list-detail never stacks details on top of each other. */
private fun openDetail(
    backStack: MutableList<NavKey>,
    route: AppDetailRoute,
) {
    backStack.removeAll { it is AppDetailRoute }
    backStack.add(route)
}

/** Shows the connection screen instead of [content] until the privileged service is connected. */
@Composable
private fun Privileged(
    @StringRes title: Int,
    onOpenShizuku: () -> Unit,
    content: @Composable () -> Unit,
) {
    val client = LocalAppContainer.current.privilegedClient
    val connection by client.state.collectAsStateWithLifecycle()
    if (connection is PrivilegedState.Connected) {
        content()
    } else {
        Box(Modifier.fillMaxSize()) {
            ConnectionScreen(
                state = connection,
                title = title,
                onRequestPermission = client::requestPermission,
                onOpenShizuku = onOpenShizuku,
                onRetry = client::reconnect,
            )
        }
    }
}

@Composable
private fun SettingsEntry(
    settings: AppSettings,
    navigate: (NavKey) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val links =
        listOf(
            SettingsLink(R.string.settings_appearance, R.string.settings_appearance_summary, R.drawable.ic_palette) {
                navigate(AppearanceRoute)
            },
            SettingsLink(R.string.settings_journal, R.string.settings_journal_summary, R.drawable.ic_history) {
                navigate(JournalRoute)
            },
            SettingsLink(R.string.settings_protected, R.string.settings_protected_summary, R.drawable.ic_shield) {
                navigate(ProtectedRoute)
            },
            SettingsLink(R.string.settings_how_it_works, R.string.settings_how_it_works_summary, R.drawable.ic_help) {
                navigate(HowItWorksRoute)
            },
            SettingsLink(R.string.settings_licenses, R.string.settings_licenses_summary, R.drawable.ic_code) {
                navigate(LicensesRoute)
            },
            SettingsLink(R.string.settings_github, R.string.settings_github_summary, R.drawable.ic_open_in_new) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri())) }
            },
        )
    SettingsScreen(
        settings = settings,
        versionName = BuildConfig.VERSION_NAME,
        links = links,
        onSampling = { ms ->
            scope.launch { container.settingsRepository.update { it.copy(samplingIntervalMs = ms) } }
        },
        onShowSystem = { on -> scope.launch { container.settingsRepository.update { it.copy(showSystemApps = on) } } },
        onDebugOverlay = { on -> scope.launch { container.settingsRepository.update { it.copy(debugOverlay = on) } } },
    )
}

private const val GITHUB_URL = "https://github.com/Brokoli5191/Picket"
