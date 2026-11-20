package app.brokoli5191.picket.core.model

import kotlinx.serialization.Serializable

/** A service record as ActivityManager reports it. */
@Serializable
data class RunningService(
    /** Flattened short component, `pkg/.Cls`. */
    val component: String,
    val packageName: String,
    val userId: Int,
    val processName: String? = null,
    val pid: Int? = null,
    /** Started with startService/startForegroundService and not stopped. */
    val started: Boolean = false,
    /** At least one client is bound. */
    val bound: Boolean = false,
    val foreground: Boolean = false,
    /** `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` bits; null when not foreground or not printed. */
    val foregroundTypes: Int? = null,
    /** Milliseconds since the record was created, when printed. */
    val createdAgoMs: Long? = null,
    val lastActivityAgoMs: Long? = null,
)

/** Foreground service type bits (`android.content.pm.ServiceInfo`), stable across releases. */
object ForegroundServiceTypes {
    val names: Map<Int, String> =
        linkedMapOf(
            1 to "dataSync",
            2 to "mediaPlayback",
            4 to "phoneCall",
            8 to "location",
            16 to "connectedDevice",
            32 to "mediaProjection",
            64 to "camera",
            128 to "microphone",
            256 to "health",
            512 to "remoteMessaging",
            1024 to "systemExempted",
            2048 to "shortService",
            4096 to "fileManagement",
            8192 to "mediaProcessing",
            1 shl 30 to "specialUse",
        )

    fun decode(bits: Int): List<String> = names.filterKeys { bits and it != 0 }.values.toList()
}
