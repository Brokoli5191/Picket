package app.brokoli5191.picket.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import app.brokoli5191.picket.core.model.AppDetail
import app.brokoli5191.picket.core.model.AppOpEntry
import app.brokoli5191.picket.core.model.AppWakeups
import app.brokoli5191.picket.core.model.IdleExemption
import app.brokoli5191.picket.core.model.PermissionGrant
import app.brokoli5191.picket.core.model.RunningService
import app.brokoli5191.picket.core.model.StandbyBucket
import app.brokoli5191.picket.core.model.WakeupKind
import app.brokoli5191.picket.core.model.WatcherEntry
import app.brokoli5191.picket.core.model.WatcherKind
import app.brokoli5191.picket.core.process.GroupKey
import app.brokoli5191.picket.core.process.GroupLabel
import app.brokoli5191.picket.core.process.GroupRow
import app.brokoli5191.picket.core.process.Importance
import app.brokoli5191.picket.core.process.ProcessKey
import app.brokoli5191.picket.core.process.ProcessRow
import app.brokoli5191.picket.data.AppInfo
import app.brokoli5191.picket.data.journal.JournalEntry
import app.brokoli5191.picket.data.settings.AppSettings
import app.brokoli5191.picket.data.settings.AppearanceSettings
import app.brokoli5191.picket.data.settings.ContrastPref
import app.brokoli5191.picket.data.settings.MotionPref
import app.brokoli5191.picket.data.settings.ThemeMode
import app.brokoli5191.picket.privileged.client.PrivilegedState
import app.brokoli5191.picket.ui.actions.actionOptionsFor
import app.brokoli5191.picket.ui.connection.ConnectionScreen
import app.brokoli5191.picket.ui.detail.AppDetailScreen
import app.brokoli5191.picket.ui.detail.AppDetailUiState
import app.brokoli5191.picket.ui.detail.actionItems
import app.brokoli5191.picket.ui.journal.JournalRow
import app.brokoli5191.picket.ui.journal.JournalScreen
import app.brokoli5191.picket.ui.processes.ProcessesScreen
import app.brokoli5191.picket.ui.processes.ProcessesUiState
import app.brokoli5191.picket.ui.settings.AppearanceScreen
import app.brokoli5191.picket.ui.settings.SettingsLink
import app.brokoli5191.picket.ui.settings.SettingsScreen
import app.brokoli5191.picket.ui.theme.PicketTheme
import app.brokoli5191.picket.ui.wakeups.OffenderRow
import app.brokoli5191.picket.ui.wakeups.WakeupWindow
import app.brokoli5191.picket.ui.wakeups.WakeupsScreen
import app.brokoli5191.picket.ui.wakeups.WakeupsUiState
import app.brokoli5191.picket.ui.watchers.WatcherGroup
import app.brokoli5191.picket.ui.watchers.WatcherItem
import app.brokoli5191.picket.ui.watchers.WatchersScreen
import app.brokoli5191.picket.ui.watchers.WatchersUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** Every main screen, rendered from fake data, in light, dark, high contrast and pure black. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest(
    private val variant: Variant,
) {
    enum class Variant(
        val appearance: AppearanceSettings,
    ) {
        LIGHT(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false, motion = MotionPref.OFF)),
        DARK(AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false, motion = MotionPref.OFF)),
        HIGH_CONTRAST(
            AppearanceSettings(
                themeMode = ThemeMode.LIGHT,
                dynamicColor = false,
                contrast = ContrastPref.HIGH,
                motion = MotionPref.OFF,
            ),
        ),
        PURE_BLACK(
            AppearanceSettings(
                themeMode = ThemeMode.DARK,
                dynamicColor = false,
                pureBlack = true,
                motion = MotionPref.OFF,
            ),
        ),
        ;

        val suffix: String get() = name.lowercase().replace('_', '-')
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(
        screen: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { PicketTheme(variant.appearance) { Surface { content() } } }
        compose.onRoot().captureRoboImage("$screen-${variant.suffix}.png")
    }

    @Test
    fun processes() = capture("processes") { ProcessesScreen(SampleData.processes, {}, {}, {}, {}, {}, {}) }

    @Test
    fun watchers() = capture("watchers") { WatchersScreen(SampleData.watchers, onOpenApp = { _, _ -> }) }

    @Test
    fun wakeups() = capture("wakeups") { WakeupsScreen(SampleData.wakeups, {}, onOpenApp = { _, _ -> }) }

    @Test
    fun appDetail() = capture("app-detail") { AppDetailScreen(SampleData.detail, onBack = {}, onRefresh = {}) }

    @Test
    fun appDetailActions() {
        compose.setContent {
            PicketTheme(variant.appearance) {
                Surface {
                    AppDetailScreen(SampleData.detail, onBack = {}, onRefresh = {}) {
                        actionItems(
                            actionOptionsFor(SampleData.detail),
                            permissionsOpen = false,
                            onPermissionsOpen = {},
                            onSelect = {},
                        )
                    }
                }
            }
        }
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("actions-header")
        compose.onRoot().captureRoboImage("app-detail-actions-${variant.suffix}.png")
    }

    @Test
    fun journal() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        capture("journal") {
            JournalScreen(
                SampleData.journal,
                sessionId = "session",
                onUndo = {},
                onUndoSession = {},
                onExport = {},
                onBack = {},
            )
        }
    }

    @Test
    fun settings() =
        capture("settings") {
            SettingsScreen(AppSettings(), "0.1.0", SampleData.links, {}, {}, {})
        }

    @Test
    fun appearance() =
        capture("appearance") {
            AppearanceScreen(variant.appearance, dynamicAvailable = true, onChange = {}, onBack = {})
        }

    @Test
    fun connection() =
        capture("connection") {
            ConnectionScreen(PrivilegedState.NotRunning, app.brokoli5191.picket.R.string.nav_processes, {}, {}, {})
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = Variant.entries.map { arrayOf(it) }
    }
}

/** Layouts must survive 200 % font scale. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
class LargeFontScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val appearance =
        AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false, motion = MotionPref.OFF)

    @Test
    fun processes() {
        compose.setContent {
            PicketTheme(appearance) { Surface { ProcessesScreen(SampleData.processes, {}, {}, {}, {}, {}, {}) } }
        }
        compose.onRoot().captureRoboImage("font-200-processes.png")
    }

    @Test
    fun watchers() {
        compose.setContent {
            PicketTheme(appearance) {
                Surface {
                    WatchersScreen(SampleData.watchers, onOpenApp = {
                        _,
                        _,
                        ->
                    })
                }
            }
        }
        compose.onRoot().captureRoboImage("font-200-watchers.png")
    }

    /** The appearance choices have the longest button labels ("Follow system", "Expressive"). */
    @Test
    fun appearance() {
        compose.setContent {
            PicketTheme(appearance) {
                Surface { AppearanceScreen(appearance, dynamicAvailable = true, onChange = {}, onBack = {}) }
            }
        }
        compose.onRoot().captureRoboImage("font-200-appearance.png")
    }

    @Test
    fun settings() {
        compose.setContent {
            PicketTheme(appearance) { Surface { SettingsScreen(AppSettings(), "0.1.0", SampleData.links, {}, {}, {}) } }
        }
        compose.onRoot().captureRoboImage("font-200-settings.png")
    }

    @Test
    fun wakeups() {
        compose.setContent {
            PicketTheme(appearance) { Surface { WakeupsScreen(SampleData.wakeups, {}, onOpenApp = { _, _ -> }) } }
        }
        compose.onRoot().captureRoboImage("font-200-wakeups.png")
    }
}

/** Plausible, invented data for screenshots and README images. */
object SampleData {
    private fun process(
        pid: Int,
        name: String,
        cpu: Double,
        rssMb: Long,
        importance: Importance,
        user: String = "u0_a123",
    ) = ProcessRow(
        pid,
        ProcessKey(pid, pid * 10L),
        name,
        user,
        10_123,
        'S',
        24,
        rssMb * 1_048_576,
        cpu,
        5_400_000,
        0,
        importance,
    )

    val processes =
        ProcessesUiState(
            loading = false,
            totalCpuPercent = 23.4,
            processCount = 412,
            groups =
                listOf(
                    GroupRow(
                        GroupKey.App(10_123),
                        GroupLabel("Chat", "com.example.chat", isSystem = false),
                        listOf(
                            process(4242, "com.example.chat", 12.5, 210, Importance.FOREGROUND),
                            process(4243, "com.example.chat:push", 0.4, 38, Importance.SERVICE),
                        ),
                    ),
                    GroupRow(
                        GroupKey.App(10_146),
                        GroupLabel("Google Play services", "com.google.android.gms", isSystem = true),
                        listOf(
                            process(
                                3185,
                                "com.google.android.gms.persistent",
                                4.1,
                                290,
                                Importance.PERCEPTIBLE,
                                "u0_a146",
                            ),
                        ),
                    ),
                    GroupRow(
                        GroupKey.App(10_201),
                        GroupLabel("Weather", "com.example.weather", isSystem = false),
                        listOf(process(9120, "com.example.weather", 1.2, 96, Importance.CACHED, "u0_a201")),
                    ),
                    GroupRow(
                        GroupKey.App(10_150),
                        GroupLabel("Maps", "com.example.maps", isSystem = false),
                        listOf(process(5150, "com.example.maps", 0.0, 312, Importance.CACHED, "u0_a150")),
                    ),
                ),
        )

    private fun watcher(
        kind: WatcherKind,
        pkg: String,
        label: String,
        component: String? = null,
        detail: String? = null,
        active: Boolean = true,
        sideloaded: Boolean = false,
    ) = WatcherItem(kind, pkg, label, component, detail, active, sideloaded, 10_100)

    val watchers =
        WatchersUiState(
            loading = false,
            groups =
                listOf(
                    WatcherGroup(
                        WatcherKind.NOTIFICATION_LISTENER,
                        listOf(
                            watcher(
                                WatcherKind.NOTIFICATION_LISTENER,
                                "com.example.notes",
                                "Notes",
                                "com.example.notes/.NoteListener",
                            ),
                            watcher(
                                WatcherKind.NOTIFICATION_LISTENER,
                                "org.example.mirror",
                                "Notification Mirror",
                                "org.example.mirror/.MirrorService",
                                sideloaded = true,
                            ),
                        ),
                    ),
                    WatcherGroup(
                        WatcherKind.ACCESSIBILITY_SERVICE,
                        listOf(
                            watcher(
                                WatcherKind.ACCESSIBILITY_SERVICE,
                                "com.example.pass",
                                "Passwords",
                                "com.example.pass/.AutofillA11y",
                            ),
                        ),
                    ),
                    WatcherGroup(
                        WatcherKind.INPUT_METHOD,
                        listOf(
                            watcher(
                                WatcherKind.INPUT_METHOD,
                                "org.example.keyboard",
                                "Keyboard",
                                "org.example.keyboard/.KeyboardService",
                                "current",
                            ),
                        ),
                    ),
                    WatcherGroup(
                        WatcherKind.DEFAULT_ROLE,
                        listOf(
                            watcher(WatcherKind.DEFAULT_ROLE, "org.mozilla.firefox", "Firefox", detail = "browser"),
                            watcher(
                                WatcherKind.DEFAULT_ROLE,
                                "com.google.android.apps.messaging",
                                "Messages",
                                detail = "sms",
                            ),
                        ),
                    ),
                ),
        )

    val wakeups =
        WakeupsUiState(
            loading = false,
            window = WakeupWindow.SIX,
            totalsSinceMs = 1_795_158_600_000,
            offenders =
                listOf(
                    OffenderRow(
                        10_201,
                        "com.example.weather",
                        "Weather",
                        96,
                        96,
                        120_345,
                        12,
                        listOf(
                            0.1f to WakeupKind.ALARM,
                            0.35f to WakeupKind.ALARM,
                            0.6f to WakeupKind.JOB,
                            0.9f to WakeupKind.ALARM,
                        ),
                    ),
                    OffenderRow(
                        10_123,
                        "com.example.chat",
                        "Chat",
                        49,
                        291,
                        261_233,
                        31,
                        listOf(
                            0.2f to WakeupKind.JOB,
                            0.5f to WakeupKind.JOB,
                            0.75f to WakeupKind.ALARM,
                            0.97f to WakeupKind.ALARM,
                        ),
                    ),
                ),
        )

    val detail =
        AppDetailUiState(
            loading = false,
            uid = 10_123,
            packageName = "com.example.chat",
            info =
                AppInfo(
                    "com.example.chat",
                    "Chat",
                    10_123,
                    isSystem = false,
                    isUpdatedSystem = false,
                    installer = "com.android.vending",
                ),
            processes = processes.groups.first().processes,
            detail =
                AppDetail(
                    packageName = "com.example.chat",
                    userId = 0,
                    uid = 10_123,
                    services =
                        listOf(
                            RunningService(
                                "com.example.chat/.push.PushService",
                                "com.example.chat",
                                0,
                                started = true,
                                foreground = true,
                                foregroundTypes = 512,
                            ),
                            RunningService(
                                "com.example.chat/.sync.ChatSyncService",
                                "com.example.chat",
                                0,
                                bound = true,
                            ),
                        ),
                    standbyBucket = StandbyBucket.ACTIVE,
                    idleExemption = IdleExemption.USER,
                    runtimePermissions =
                        listOf(
                            PermissionGrant("android.permission.POST_NOTIFICATIONS", true),
                            PermissionGrant("android.permission.READ_CONTACTS", true),
                        ),
                    appOps = listOf(AppOpEntry("RUN_ANY_IN_BACKGROUND", "allow", uidMode = true)),
                ),
            watchers =
                listOf(
                    WatcherEntry(WatcherKind.NOTIFICATION_LISTENER, "com.example.chat", "com.example.chat/.Listener"),
                ),
            wakeups =
                AppWakeups(
                    10_123,
                    "com.example.chat",
                    alarmWakeups = 49,
                    wakelockCount = 291,
                    wakelockMs = 261_233,
                    jobCount = 31,
                ),
        )

    val links =
        listOf(
            SettingsLink(
                app.brokoli5191.picket.R.string.settings_appearance,
                app.brokoli5191.picket.R.string.settings_appearance_summary,
                app.brokoli5191.picket.R.drawable.ic_palette,
            ) {
            },
            SettingsLink(
                app.brokoli5191.picket.R.string.settings_journal,
                app.brokoli5191.picket.R.string.settings_journal_summary,
                app.brokoli5191.picket.R.drawable.ic_history,
            ) {
            },
            SettingsLink(
                app.brokoli5191.picket.R.string.settings_protected,
                app.brokoli5191.picket.R.string.settings_protected_summary,
                app.brokoli5191.picket.R.drawable.ic_shield,
            ) {
            },
            SettingsLink(
                app.brokoli5191.picket.R.string.settings_how_it_works,
                app.brokoli5191.picket.R.string.settings_how_it_works_summary,
                app.brokoli5191.picket.R.drawable.ic_help,
            ) {
            },
            SettingsLink(
                app.brokoli5191.picket.R.string.settings_licenses,
                app.brokoli5191.picket.R.string.settings_licenses_summary,
                app.brokoli5191.picket.R.drawable.ic_code,
            ) {
            },
        )

    private const val BASE_TIME = 1_795_000_000_000L

    private fun entry(
        id: Long,
        minutesAgo: Int,
        argv: List<String>,
        previous: String?,
        inverse: Boolean,
        success: Boolean = true,
        undoneById: Long? = null,
        undoOfId: Long? = null,
        note: String? = null,
    ) = JournalEntry(
        id = id,
        sessionId = "session",
        timestampMs = BASE_TIME - minutesAgo * 60_000L,
        packageName = argv.first { it.startsWith("com.") }.substringBefore('/'),
        actionJson = "{}",
        argv = argv,
        command = argv.joinToString(" "),
        risk = "LOW",
        previousState = previous,
        inverseJson = if (inverse) "{}" else null,
        success = success,
        exitCode = if (success) 0 else 255,
        stdout = "",
        stderr = "",
        note = note,
        undoOfId = undoOfId,
        undoneById = undoneById,
    )

    val journal =
        listOf(
            JournalRow(
                entry(
                    4,
                    2,
                    listOf(
                        "/system/bin/cmd",
                        "notification",
                        "disallow_listener",
                        "com.example.chat/.NotificationReader",
                        "0",
                    ),
                    previous = "allowed",
                    inverse = true,
                ),
                undoCommand = "/system/bin/cmd notification allow_listener com.example.chat/.NotificationReader 0",
                restricted = true,
            ),
            JournalRow(
                entry(
                    3,
                    9,
                    listOf(
                        "/system/bin/cmd",
                        "appops",
                        "set",
                        "--user",
                        "0",
                        "com.example.chat",
                        "RUN_ANY_IN_BACKGROUND",
                        "ignore",
                    ),
                    previous = "allow",
                    inverse = true,
                ),
                undoCommand = "/system/bin/cmd appops set --user 0 com.example.chat RUN_ANY_IN_BACKGROUND allow",
                restricted = false,
            ),
            JournalRow(
                entry(
                    2,
                    15,
                    listOf(
                        "/system/bin/cmd",
                        "activity",
                        "stop-service",
                        "--user",
                        "0",
                        "-n",
                        "com.example.chat/.sync.ChatSyncService",
                    ),
                    previous = null,
                    inverse = false,
                    success = false,
                    note = "Service is bound by another app and keeps running",
                ),
                undoCommand = null,
                restricted = false,
            ),
            JournalRow(
                entry(
                    1,
                    31,
                    listOf("/system/bin/cmd", "activity", "force-stop", "--user", "0", "com.example.chat"),
                    null,
                    inverse = false,
                ),
                undoCommand = null,
                restricted = false,
            ),
        )
}

/** States worth one screenshot each, in the light theme only. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StateScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val appearance =
        AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false, motion = MotionPref.OFF)

    @Test
    fun processesPaused() =
        capture(
            "processes-paused.png",
            SampleData.processes.copy(paused = true, filter = SampleData.processes.filter.copy(showSystem = true)),
        )

    @Test
    fun processesSearching() =
        capture(
            "processes-searching.png",
            SampleData.processes.copy(
                groups = SampleData.processes.groups.take(1),
                filter = SampleData.processes.filter.copy(query = "chat"),
            ),
        )

    private fun capture(
        name: String,
        state: ProcessesUiState,
    ) {
        compose.setContent {
            PicketTheme(appearance) { Surface { ProcessesScreen(state, {}, {}, {}, {}, {}, {}) } }
        }
        compose.onRoot().captureRoboImage(name)
    }
}
