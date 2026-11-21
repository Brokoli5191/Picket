package app.brokoli5191.picket.core.process

/**
 * Importance class derived from `oom_score_adj`, using the bands of
 * `com.android.server.am.ProcessList`: persistent/system < 0 ≤ foreground < 100 ≤ visible < 200
 * ≤ perceptible < 300 ≤ service (incl. backup, heavy-weight, home, previous, B-services) < 900 ≤ cached.
 *
 * Bands rather than exact constants, because the constants between 0 and 900 have been added to and
 * renumbered across Android releases while the band edges stayed put.
 */
enum class Importance {
    SYSTEM,
    FOREGROUND,
    VISIBLE,
    PERCEPTIBLE,
    SERVICE,
    CACHED,
    UNKNOWN,
    ;

    companion object {
        fun fromOomScoreAdj(adj: Int?): Importance =
            when {
                adj == null -> UNKNOWN
                adj < 0 -> SYSTEM
                adj < 100 -> FOREGROUND
                adj < 200 -> VISIBLE
                adj < 300 -> PERCEPTIBLE
                adj < 900 -> SERVICE
                adj <= 1000 -> CACHED
                else -> UNKNOWN
            }
    }
}
