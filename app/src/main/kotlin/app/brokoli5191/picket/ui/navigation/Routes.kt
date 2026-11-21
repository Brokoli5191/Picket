package app.brokoli5191.picket.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import app.brokoli5191.picket.R
import kotlinx.serialization.Serializable

@Serializable
data object ProcessesRoute : NavKey

@Serializable
data class AppDetailRoute(
    val uid: Int,
    val packageName: String?,
) : NavKey

@Serializable
data object WatchersRoute : NavKey

@Serializable
data object WakeupsRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object AppearanceRoute : NavKey

@Serializable
data object JournalRoute : NavKey

@Serializable
data object ProtectedRoute : NavKey

@Serializable
data object HowItWorksRoute : NavKey

@Serializable
data object LicensesRoute : NavKey

/** Destinations in the navigation bar / rail. */
enum class TopLevel(
    val route: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    PROCESSES(ProcessesRoute, R.string.nav_processes, R.drawable.ic_memory, R.drawable.ic_memory_filled),
    WATCHERS(WatchersRoute, R.string.nav_watchers, R.drawable.ic_visibility, R.drawable.ic_visibility_filled),
    WAKEUPS(WakeupsRoute, R.string.nav_wakeups, R.drawable.ic_alarm, R.drawable.ic_alarm_filled),
    SETTINGS(SettingsRoute, R.string.nav_settings, R.drawable.ic_settings, R.drawable.ic_settings_filled),
    ;

    companion object {
        fun of(route: NavKey?): TopLevel =
            when (route) {
                WatchersRoute -> WATCHERS
                WakeupsRoute -> WAKEUPS
                SettingsRoute, AppearanceRoute, JournalRoute, ProtectedRoute, HowItWorksRoute, LicensesRoute -> SETTINGS
                else -> PROCESSES
            }
    }
}
