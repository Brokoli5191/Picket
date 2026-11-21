package app.brokoli5191.picket.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Palette algorithms of the Material colour system offered for seed colours. */
enum class SchemeStyle { TONAL_SPOT, VIBRANT, EXPRESSIVE, FIDELITY }

enum class ContrastPref { SYSTEM, STANDARD, MEDIUM, HIGH }

/** FULL: expressive motion. REDUCED: standard motion, no hero transitions. OFF: no animation. */
enum class MotionPref { SYSTEM, FULL, REDUCED, OFF }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    /** Index into [Seeds.all]. */
    val seed: Int = Seeds.DEFAULT,
    val style: SchemeStyle = SchemeStyle.TONAL_SPOT,
    val contrast: ContrastPref = ContrastPref.SYSTEM,
    val motion: MotionPref = MotionPref.SYSTEM,
    val haptics: Boolean = true,
)

data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val samplingIntervalMs: Long = DEFAULT_SAMPLING_MS,
    val showSystemApps: Boolean = false,
    val debugOverlay: Boolean = false,
) {
    companion object {
        const val DEFAULT_SAMPLING_MS = 2_000L
        val SAMPLING_CHOICES_MS = listOf(1_000L, 2_000L, 5_000L, 10_000L)
    }
}

/** Curated seed colours for when dynamic colour is off or unavailable (below Android 12). */
object Seeds {
    data class Seed(
        val name: String,
        val argb: Int,
    )

    const val DEFAULT = 0

    val all: List<Seed> =
        listOf(
            Seed("Teal", 0xFF12676B.toInt()),
            Seed("Amber", 0xFF8A6100.toInt()),
            Seed("Indigo", 0xFF4355B9.toInt()),
            Seed("Rose", 0xFF9C4146.toInt()),
            Seed("Moss", 0xFF4C6B1F.toInt()),
            Seed("Slate", 0xFF4E5B71.toInt()),
            Seed("Violet", 0xFF6E4FA3.toInt()),
            Seed("Copper", 0xFF9A4600.toInt()),
        )

    fun at(index: Int): Seed = all.getOrElse(index) { all[DEFAULT] }
}
