package app.brokoli5191.picket.core.parse

/** Parses durations printed by `android.util.TimeUtils.formatDuration`: `+1d2h3m4s5ms`, `-42s1ms`, `0`. */
object Durations {
    private val part = Regex("(\\d+)(d|h|ms|m|s)")

    /** Signed milliseconds, or null if [text] is not a TimeUtils duration (`--` for "never"). */
    fun parse(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty() || t == "--") return null
        if (t == "0") return 0
        val sign = if (t.startsWith("-")) -1 else 1
        val body = t.removePrefix("+").removePrefix("-")
        if (body.isEmpty()) return null
        var total = 0L
        var consumed = 0
        for (m in part.findAll(body)) {
            if (m.range.first != consumed) return null
            consumed = m.range.last + 1
            val n = m.groupValues[1].toLong()
            total +=
                when (m.groupValues[2]) {
                    "d" -> n * 86_400_000
                    "h" -> n * 3_600_000
                    "m" -> n * 60_000
                    "s" -> n * 1_000
                    else -> n
                }
        }
        return if (consumed == body.length) sign * total else null
    }
}
