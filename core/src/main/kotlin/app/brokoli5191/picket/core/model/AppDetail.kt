package app.brokoli5191.picket.core.model

import kotlinx.serialization.Serializable

/** App standby buckets (`UsageStatsManager.STANDBY_BUCKET_*`). RESTRICTED exists from API 30. */
@Serializable
enum class StandbyBucket(
    val value: Int,
    val shellName: String,
) {
    EXEMPTED(5, "exempted"),
    ACTIVE(10, "active"),
    WORKING_SET(20, "working_set"),
    FREQUENT(30, "frequent"),
    RARE(40, "rare"),
    RESTRICTED(45, "restricted"),
    NEVER(50, "never"),
    ;

    companion object {
        fun fromValue(value: Int): StandbyBucket? = entries.firstOrNull { it.value == value }

        fun fromShell(text: String): StandbyBucket? {
            val t = text.trim()
            return t.toIntOrNull()?.let(::fromValue) ?: entries.firstOrNull { it.shellName.equals(t, ignoreCase = true) }
        }
    }
}

@Serializable
data class PermissionGrant(
    val name: String,
    val granted: Boolean,
    /** Flags as printed, e.g. USER_SET, USER_FIXED, POLICY_FIXED, SYSTEM_FIXED. */
    val flags: List<String> = emptyList(),
) {
    /** Fixed permissions cannot be changed with pm grant/revoke. */
    val fixed: Boolean get() = flags.any { it == "POLICY_FIXED" || it == "SYSTEM_FIXED" }
}

@Serializable
data class AppOpEntry(
    val op: String,
    val mode: String,
    /** True when the mode is set for the whole uid rather than the package. */
    val uidMode: Boolean = false,
    val lastAccess: String? = null,
)

/** Battery optimisation (doze) exemption. */
@Serializable
enum class IdleExemption { NONE, USER, SYSTEM }

@Serializable
data class AppDetail(
    val packageName: String,
    val userId: Int,
    val uid: Int? = null,
    val services: List<RunningService> = emptyList(),
    val standbyBucket: StandbyBucket? = null,
    val idleExemption: IdleExemption? = null,
    val runtimePermissions: List<PermissionGrant> = emptyList(),
    val appOps: List<AppOpEntry> = emptyList(),
    val installer: String? = null,
    val enabledState: String? = null,
    val sources: List<SourceStatus> = emptyList(),
)
