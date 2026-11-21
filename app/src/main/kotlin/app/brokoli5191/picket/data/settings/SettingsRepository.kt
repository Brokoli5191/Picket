package app.brokoli5191.picket.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Settings persisted in a Preferences DataStore. Changes apply live, no activity recreation. */
class SettingsRepository(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> =
        store.data
            .catch { e ->
                if (e is IOException) {
                    emit(
                        androidx.datastore.preferences.core
                            .emptyPreferences(),
                    )
                } else {
                    throw e
                }
            }.map(::read)
            .distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            write(prefs, next)
        }
    }

    suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) =
        update { it.copy(appearance = transform(it.appearance)) }

    private fun read(p: Preferences): AppSettings {
        val defaults = AppSettings()
        val a = defaults.appearance
        return AppSettings(
            appearance =
                AppearanceSettings(
                    themeMode = enumOf(p[Keys.THEME], a.themeMode),
                    pureBlack = p[Keys.PURE_BLACK] ?: a.pureBlack,
                    dynamicColor = p[Keys.DYNAMIC] ?: a.dynamicColor,
                    seed = (p[Keys.SEED] ?: a.seed).coerceIn(0, Seeds.all.lastIndex),
                    style = enumOf(p[Keys.STYLE], a.style),
                    contrast = enumOf(p[Keys.CONTRAST], a.contrast),
                    motion = enumOf(p[Keys.MOTION], a.motion),
                    haptics = p[Keys.HAPTICS] ?: a.haptics,
                ),
            samplingIntervalMs =
                (p[Keys.SAMPLING] ?: defaults.samplingIntervalMs)
                    .takeIf { it in AppSettings.SAMPLING_CHOICES_MS } ?: defaults.samplingIntervalMs,
            showSystemApps = p[Keys.SHOW_SYSTEM] ?: defaults.showSystemApps,
            debugOverlay = p[Keys.DEBUG_OVERLAY] ?: defaults.debugOverlay,
        )
    }

    private fun write(
        p: androidx.datastore.preferences.core.MutablePreferences,
        s: AppSettings,
    ) {
        p[Keys.THEME] = s.appearance.themeMode.name
        p[Keys.PURE_BLACK] = s.appearance.pureBlack
        p[Keys.DYNAMIC] = s.appearance.dynamicColor
        p[Keys.SEED] = s.appearance.seed
        p[Keys.STYLE] = s.appearance.style.name
        p[Keys.CONTRAST] = s.appearance.contrast.name
        p[Keys.MOTION] = s.appearance.motion.name
        p[Keys.HAPTICS] = s.appearance.haptics
        p[Keys.SAMPLING] = s.samplingIntervalMs
        p[Keys.SHOW_SYSTEM] = s.showSystemApps
        p[Keys.DEBUG_OVERLAY] = s.debugOverlay
    }

    private inline fun <reified E : Enum<E>> enumOf(
        name: String?,
        default: E,
    ): E = name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SEED = intPreferencesKey("seed")
        val STYLE = stringPreferencesKey("scheme_style")
        val CONTRAST = stringPreferencesKey("contrast")
        val MOTION = stringPreferencesKey("motion")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SAMPLING = longPreferencesKey("sampling_interval_ms")
        val SHOW_SYSTEM = booleanPreferencesKey("show_system_apps")
        val DEBUG_OVERLAY = booleanPreferencesKey("debug_overlay")
    }
}
