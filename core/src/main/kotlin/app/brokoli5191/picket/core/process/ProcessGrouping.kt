package app.brokoli5191.picket.core.process

import app.brokoli5191.picket.core.model.ProcessSample

/** Who a group of processes belongs to. */
sealed interface GroupKey {
    /** An app uid; processes of isolated and SDK-sandbox uids are folded into their app. */
    data class App(
        val uid: Int,
    ) : GroupKey

    /** A system or native uid below the application range (root, system, radio, media, ...). */
    data class SystemUid(
        val uid: Int,
    ) : GroupKey

    /** Kernel threads (children of kthreadd). */
    data object Kernel : GroupKey

    /** Isolated processes whose owning app could not be determined. */
    data object UnattributedIsolated : GroupKey
}

data class ProcessGroup(
    val key: GroupKey,
    val processes: List<ProcessSample>,
)

/**
 * Groups processes by owner. [uidOfPackage] resolves a package name in a user to its uid and is
 * only needed for isolated processes, whose uid says nothing about the app that started them.
 */
class ProcessGrouping(
    private val uidOfPackage: (packageName: String, userId: Int) -> Int?,
) {
    fun group(processes: List<ProcessSample>): List<ProcessGroup> =
        processes
            .groupBy(::keyOf)
            .map { (key, members) -> ProcessGroup(key, members.sortedBy { it.pid }) }

    fun keyOf(process: ProcessSample): GroupKey {
        val uid = process.uid
        return when {
            process.pid == KTHREADD_PID || process.ppid == KTHREADD_PID -> {
                GroupKey.Kernel
            }

            uid < 0 -> {
                GroupKey.SystemUid(uid)
            }

            Uids.isApp(uid) -> {
                GroupKey.App(uid)
            }

            Uids.isSdkSandbox(uid) -> {
                GroupKey.App(Uids.appUidOfSdkSandbox(uid))
            }

            Uids.isIsolated(uid) -> {
                ownerPackageOf(process.name)
                    ?.let { uidOfPackage(it, Uids.userId(uid)) }
                    ?.let { GroupKey.App(it) }
                    ?: GroupKey.UnattributedIsolated
            }

            else -> {
                GroupKey.SystemUid(uid)
            }
        }
    }

    companion object {
        private const val KTHREADD_PID = 2

        /**
         * Package prefix of a process name: `com.android.chrome:sandboxed_process0:...` and
         * `com.android.chrome_zygote` both belong to `com.android.chrome`.
         */
        fun ownerPackageOf(processName: String): String? {
            val base = processName.substringBefore(':').removeSuffix("_zygote")
            return base.takeIf {
                it.isNotEmpty() && '.' in it &&
                    it.all { c -> c.isLetterOrDigit() || c == '.' || c == '_' }
            }
        }
    }
}
