package app.brokoli5191.picket.core.format

import java.util.Locale

/** Number formatting for the UI. English only in v1, so a fixed locale keeps digits stable. */
object Formatters {
    private val locale = Locale.US

    /** Binary units with one decimal from MB on: `512 KB`, `187.6 MB`, `1.2 GB`. */
    fun bytes(value: Long): String {
        val v = value.coerceAtLeast(0).toDouble()
        return when {
            v < KIB -> "${value.coerceAtLeast(0)} B"
            v < MIB -> String.format(locale, "%.0f KB", v / KIB)
            v < GIB -> String.format(locale, "%.1f MB", v / MIB)
            else -> String.format(locale, "%.2f GB", v / GIB)
        }
    }

    /** `0.0 %` .. `100 %`; below 10 % with one decimal so small values stay distinguishable. */
    fun percent(value: Double): String {
        val v = value.coerceIn(0.0, 100.0)
        return if (v < 10.0) String.format(locale, "%.1f %%", v) else String.format(locale, "%.0f %%", v)
    }

    /** Compact duration: `45s`, `12m 3s`, `3h 07m`, `2d 4h`. */
    fun duration(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        val m = s / 60
        val h = m / 60
        val d = h / 24
        return when {
            s < 60 -> "${s}s"
            m < 60 -> "${m}m ${s % 60}s"
            h < 24 -> String.format(locale, "%dh %02dm", h, m % 60)
            else -> "${d}d ${h % 24}h"
        }
    }

    private const val KIB = 1024.0
    private const val MIB = KIB * 1024
    private const val GIB = MIB * 1024
}
