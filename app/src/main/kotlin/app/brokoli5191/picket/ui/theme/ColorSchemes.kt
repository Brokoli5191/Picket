package app.brokoli5191.picket.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import app.brokoli5191.picket.data.settings.SchemeStyle
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/** Contrast levels of the Material colour system: 0 standard, 0.5 medium, 1 high. */
object ContrastLevel {
    const val STANDARD = 0.0
    const val MEDIUM = 0.5
    const val HIGH = 1.0
}

/** Builds Compose colour schemes from a seed colour with material-color-utilities. */
object ColorSchemes {
    fun fromSeed(
        seedArgb: Int,
        dark: Boolean,
        style: SchemeStyle,
        contrastLevel: Double,
    ): ColorScheme {
        val hct = Hct.fromInt(seedArgb)
        val scheme: DynamicScheme =
            when (style) {
                SchemeStyle.TONAL_SPOT -> SchemeTonalSpot(hct, dark, contrastLevel)
                SchemeStyle.VIBRANT -> SchemeVibrant(hct, dark, contrastLevel)
                SchemeStyle.EXPRESSIVE -> SchemeExpressive(hct, dark, contrastLevel)
                SchemeStyle.FIDELITY -> SchemeFidelity(hct, dark, contrastLevel)
            }
        return toCompose(scheme, dark)
    }

    private fun toCompose(
        s: DynamicScheme,
        dark: Boolean,
    ): ColorScheme {
        val c = MaterialDynamicColors()

        fun role(color: DynamicColor) = Color(color.getArgb(s))
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = role(c.primary),
            onPrimary = role(c.onPrimary),
            primaryContainer = role(c.primaryContainer),
            onPrimaryContainer = role(c.onPrimaryContainer),
            inversePrimary = role(c.inversePrimary),
            secondary = role(c.secondary),
            onSecondary = role(c.onSecondary),
            secondaryContainer = role(c.secondaryContainer),
            onSecondaryContainer = role(c.onSecondaryContainer),
            tertiary = role(c.tertiary),
            onTertiary = role(c.onTertiary),
            tertiaryContainer = role(c.tertiaryContainer),
            onTertiaryContainer = role(c.onTertiaryContainer),
            background = role(c.background),
            onBackground = role(c.onBackground),
            surface = role(c.surface),
            onSurface = role(c.onSurface),
            surfaceVariant = role(c.surfaceVariant),
            onSurfaceVariant = role(c.onSurfaceVariant),
            surfaceTint = role(c.surfaceTint),
            inverseSurface = role(c.inverseSurface),
            inverseOnSurface = role(c.inverseOnSurface),
            error = role(c.error),
            onError = role(c.onError),
            errorContainer = role(c.errorContainer),
            onErrorContainer = role(c.onErrorContainer),
            outline = role(c.outline),
            outlineVariant = role(c.outlineVariant),
            scrim = role(c.scrim),
            surfaceBright = role(c.surfaceBright),
            surfaceDim = role(c.surfaceDim),
            surfaceContainer = role(c.surfaceContainer),
            surfaceContainerHigh = role(c.surfaceContainerHigh),
            surfaceContainerHighest = role(c.surfaceContainerHighest),
            surfaceContainerLow = role(c.surfaceContainerLow),
            surfaceContainerLowest = role(c.surfaceContainerLowest),
            primaryFixed = role(c.primaryFixed),
            primaryFixedDim = role(c.primaryFixedDim),
            onPrimaryFixed = role(c.onPrimaryFixed),
            onPrimaryFixedVariant = role(c.onPrimaryFixedVariant),
            secondaryFixed = role(c.secondaryFixed),
            secondaryFixedDim = role(c.secondaryFixedDim),
            onSecondaryFixed = role(c.onSecondaryFixed),
            onSecondaryFixedVariant = role(c.onSecondaryFixedVariant),
            tertiaryFixed = role(c.tertiaryFixed),
            tertiaryFixedDim = role(c.tertiaryFixedDim),
            onTertiaryFixed = role(c.onTertiaryFixed),
            onTertiaryFixedVariant = role(c.onTertiaryFixedVariant),
        )
    }

    /** AMOLED variant: true black behind content, containers lifted just enough to stay visible. */
    fun pureBlack(scheme: ColorScheme): ColorScheme =
        scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222),
        )
}
