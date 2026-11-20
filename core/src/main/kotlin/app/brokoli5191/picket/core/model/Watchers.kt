package app.brokoli5191.picket.core.model

import kotlinx.serialization.Serializable

/** The kinds of privileged hook ("watcher") Picket reports. */
@Serializable
enum class WatcherKind {
    NOTIFICATION_LISTENER,
    ACCESSIBILITY_SERVICE,
    DEVICE_ADMIN,
    INPUT_METHOD,
    USAGE_ACCESS,
    OVERLAY,
    VPN,
    DEFAULT_ROLE,
    DND_ACCESS,
}

/** One app holding one hook. */
@Serializable
data class WatcherEntry(
    val kind: WatcherKind,
    val packageName: String,
    /** Flattened component (`pkg/.Cls`) for component-based hooks. */
    val component: String? = null,
    val userId: Int = 0,
    /**
     * Kind-specific qualifier: the role name for [WatcherKind.DEFAULT_ROLE], `current` for the
     * active input method, `device-owner`/`profile-owner` for admins, `always-on`/`lockdown` for VPN.
     */
    val detail: String? = null,
    /**
     * False when the hook is granted but not in effect (an enabled accessibility service while
     * accessibility is switched off, an allowed listener that is not bound).
     */
    val active: Boolean = true,
)

/** Result of one data source; a failing source does not hide the others. */
@Serializable
data class SourceStatus(
    val source: String,
    val ok: Boolean,
    val message: String? = null,
)

@Serializable
data class WatchersSnapshot(
    val entries: List<WatcherEntry>,
    val sources: List<SourceStatus>,
)
