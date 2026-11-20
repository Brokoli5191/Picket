package app.brokoli5191.picket.ui.theme

import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import app.brokoli5191.picket.data.settings.AppearanceSettings
import app.brokoli5191.picket.data.settings.ContrastPref
import app.brokoli5191.picket.data.settings.MotionPref
import app.brokoli5191.picket.data.settings.SchemeStyle
import app.brokoli5191.picket.data.settings.Seeds
import app.brokoli5191.picket.data.settings.ThemeMode
import app.brokoli5191.picket.ui.expressive.PicketExpressiveTheme
import app.brokoli5191.picket.ui.expressive.expressiveMotion
import app.brokoli5191.picket.ui.expressive.standardMotion

/**
 * Resolves the appearance settings (theme, pure black, dynamic or seed colour, scheme style,
 * contrast, motion, haptics) into the Material 3 Expressive theme. Settings changes recompose;
 * the activity is never recreated.
 */
@Composable
fun PicketTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark =
        when (appearance.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val systemContrast = rememberSystemContrast(context)
    val contrastLevel =
        when (appearance.contrast) {
            ContrastPref.SYSTEM -> systemContrast.toDouble().coerceIn(ContrastLevel.STANDARD, ContrastLevel.HIGH)
            ContrastPref.STANDARD -> ContrastLevel.STANDARD
            ContrastPref.MEDIUM -> ContrastLevel.MEDIUM
            ContrastPref.HIGH -> ContrastLevel.HIGH
        }
    val inPreview = LocalInspectionMode.current
    val colorScheme =
        remember(appearance, dark, contrastLevel, inPreview) {
            resolveColorScheme(context, appearance, dark, contrastLevel, dynamicAvailable = !inPreview)
        }

    val animatorScale = rememberAnimatorDurationScale(context)
    val motion =
        when (appearance.motion) {
            MotionPref.SYSTEM -> if (animatorScale == 0f) MotionLevel.REDUCED else MotionLevel.FULL
            MotionPref.FULL -> MotionLevel.FULL
            MotionPref.REDUCED -> MotionLevel.REDUCED
            MotionPref.OFF -> MotionLevel.OFF
        }
    val motionScheme: MotionScheme =
        when (motion) {
            MotionLevel.FULL -> expressiveMotion()
            MotionLevel.REDUCED -> standardMotion()
            MotionLevel.OFF -> NoMotionScheme
        }

    CompositionLocalProvider(
        LocalMotionLevel provides motion,
        LocalHapticsEnabled provides appearance.haptics,
        LocalDarkTheme provides dark,
    ) {
        PicketExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            typography = Typography(),
            content = content,
        )
    }
}

internal fun resolveColorScheme(
    context: Context,
    appearance: AppearanceSettings,
    dark: Boolean,
    contrastLevel: Double,
    dynamicAvailable: Boolean = true,
): ColorScheme {
    val dynamic = appearance.dynamicColor && dynamicAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme =
        when {
            dynamic && appearance.contrast == ContrastPref.SYSTEM -> {
                // The platform's dynamic roles already follow the system contrast setting.
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            dynamic -> {
                // An explicit contrast needs the scheme rebuilt from the wallpaper's seed colour.
                val seed = wallpaperSeed(context) ?: Seeds.at(appearance.seed).argb
                ColorSchemes.fromSeed(seed, dark, SchemeStyle.TONAL_SPOT, contrastLevel)
            }

            else -> {
                ColorSchemes.fromSeed(Seeds.at(appearance.seed).argb, dark, appearance.style, contrastLevel)
            }
        }
    return if (dark && appearance.pureBlack) ColorSchemes.pureBlack(scheme) else scheme
}

private fun wallpaperSeed(context: Context): Int? =
    runCatching {
        WallpaperManager
            .getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
    }.getOrNull()

/** `UiModeManager.getContrast()` (Android 14+), kept current while composed; 0 below Android 14. */
@Composable
private fun rememberSystemContrast(context: Context): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0f
    val manager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    var contrast by remember(manager) { mutableFloatStateOf(manager?.contrast ?: 0f) }
    DisposableEffect(manager) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        manager?.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { manager?.removeContrastChangeListener(listener) }
    }
    return contrast
}

/** Global animator duration scale; 0 when "Remove animations" is on. */
@Composable
private fun rememberAnimatorDurationScale(context: Context): Float {
    val resolver = context.contentResolver

    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    var scale by remember(resolver) { mutableFloatStateOf(read()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    scale = read()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}
