package app.brokoli5191.picket.privileged.client

/** Where the connection to the privileged service stands, as shown to the user. */
sealed interface PrivilegedState {
    /** Initial state until the first evaluation. */
    data object Checking : PrivilegedState

    /** Neither the Shizuku app nor Sui is present. */
    data object NotInstalled : PrivilegedState

    /** Shizuku is installed but its server is not running (always the case after a reboot). */
    data object NotRunning : PrivilegedState

    /** A Shizuku server older than API v11; UserServices need v11+. */
    data class Unsupported(
        val serverVersion: Int,
    ) : PrivilegedState

    /** Shizuku runs, Picket has not been granted access yet. */
    data object PermissionRequired : PrivilegedState

    /** The user chose "Deny and don't ask again"; it can only be changed in the Shizuku app. */
    data object PermissionDenied : PrivilegedState

    data object Connecting : PrivilegedState

    data class Connected(
        val identity: ServiceIdentity,
    ) : PrivilegedState

    data class Failed(
        val message: String,
    ) : PrivilegedState
}

data class ServiceIdentity(
    /** uid the privileged service runs as. */
    val uid: Int,
    val seLinuxContext: String,
    /** Shizuku server API version. */
    val serverVersion: Int,
    /** True when the privileges come from Sui (root) rather than the Shizuku app. */
    val isSui: Boolean,
) {
    val isRoot: Boolean get() = uid == 0
}
