package app.brokoli5191.picket.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class WakeupKind { ALARM, WAKELOCK, JOB, PUSH }

/** One thing that woke or kept the device awake, at a known time. */
@Serializable
data class WakeupEvent(
    val kind: WakeupKind,
    val uid: Int,
    val packageName: String? = null,
    val tag: String,
    /** Wall-clock time in ms since epoch (converted in the service). */
    val atMs: Long,
    val durationMs: Long? = null,
)

/** Totals per app over the report window, for the top-offenders list. */
@Serializable
data class AppWakeups(
    val uid: Int,
    val packageName: String? = null,
    val alarmWakeups: Int = 0,
    val wakelockCount: Int = 0,
    val wakelockMs: Long = 0,
    val jobCount: Int = 0,
    val jobMs: Long = 0,
) {
    val total: Int get() = alarmWakeups + wakelockCount + jobCount
}

@Serializable
data class WakeupReport(
    val windowMs: Long,
    /** Wall-clock end of the window. */
    val untilMs: Long,
    val apps: List<AppWakeups>,
    /** Bounded; newest first. */
    val events: List<WakeupEvent>,
    val sources: List<SourceStatus>,
    /** Wall-clock start of the [apps] totals (battery statistics reset at the last charge); null if unknown. */
    val totalsSinceMs: Long? = null,
)
