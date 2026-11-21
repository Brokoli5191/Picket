package app.brokoli5191.picket.core.process

import app.brokoli5191.picket.core.model.ProcessSample
import app.brokoli5191.picket.core.model.ProcessSnapshot

/** How a group is presented; resolved by the app from PackageManager. */
data class GroupLabel(
    val title: String,
    val packageName: String?,
    val isSystem: Boolean,
)

data class ProcessRow(
    val pid: Int,
    val key: ProcessKey,
    val name: String,
    val userName: String,
    val uid: Int,
    val state: Char,
    val threads: Int,
    val rssBytes: Long,
    val cpuPercent: Double,
    val uptimeMs: Long,
    val oomScoreAdj: Int?,
    val importance: Importance,
)

data class GroupRow(
    val key: GroupKey,
    val label: GroupLabel,
    val processes: List<ProcessRow>,
) {
    val cpuPercent: Double = processes.sumOf { it.cpuPercent }
    val rssBytes: Long = processes.sumOf { it.rssBytes }
    val uptimeMs: Long = processes.maxOfOrNull { it.uptimeMs } ?: 0

    /** The most important process decides (foreground beats cached). */
    val importance: Importance = processes.minOfOrNull { it.importance } ?: Importance.UNKNOWN
}

enum class SortOrder { CPU, MEMORY, NAME, UPTIME }

data class ProcessFilter(
    val showSystem: Boolean = false,
    val query: String = "",
)

/** Turns snapshots into the rows of the process list. Pure, so it is tested without Android. */
object ProcessTable {
    fun build(
        snapshot: ProcessSnapshot,
        cpu: CpuUsage,
        groups: List<ProcessGroup>,
        label: (GroupKey, List<ProcessSample>) -> GroupLabel,
    ): List<GroupRow> =
        groups.map { group ->
            GroupRow(
                key = group.key,
                label = label(group.key, group.processes),
                processes =
                    group.processes.map { p ->
                        ProcessRow(
                            pid = p.pid,
                            key = p.key,
                            name = p.name,
                            userName = Uids.displayName(p.uid),
                            uid = p.uid,
                            state = p.state,
                            threads = p.threads,
                            rssBytes = p.rssBytes(snapshot.pageSizeBytes),
                            cpuPercent = cpu.percentOf(p),
                            uptimeMs = p.uptimeMs(snapshot.uptimeMs, snapshot.clockTicksPerSecond),
                            oomScoreAdj = p.oomScoreAdj,
                            importance = Importance.fromOomScoreAdj(p.oomScoreAdj),
                        )
                    },
            )
        }

    fun filter(
        rows: List<GroupRow>,
        filter: ProcessFilter,
    ): List<GroupRow> {
        val query = filter.query.trim()
        return rows.filter { row ->
            (filter.showSystem || !row.label.isSystem) &&
                (
                    query.isEmpty() ||
                        row.label.title.contains(query, ignoreCase = true) ||
                        row.label.packageName?.contains(query, ignoreCase = true) == true ||
                        row.processes.any { it.name.contains(query, ignoreCase = true) || it.pid.toString() == query }
                )
        }
    }

    fun comparator(order: SortOrder): Comparator<GroupRow> {
        val byTitle = compareBy<GroupRow, String>(String.CASE_INSENSITIVE_ORDER) { it.label.title }
        return when (order) {
            SortOrder.CPU -> {
                compareByDescending<GroupRow> { it.cpuPercent }.thenByDescending { it.rssBytes }.then(
                    byTitle,
                )
            }

            SortOrder.MEMORY -> {
                compareByDescending<GroupRow> { it.rssBytes }.then(byTitle)
            }

            SortOrder.NAME -> {
                byTitle
            }

            SortOrder.UPTIME -> {
                compareByDescending<GroupRow> { it.uptimeMs }.then(byTitle)
            }
        }
    }
}

/**
 * Keeps a live-sorted list from dancing: the order is recomputed only when [resortAllowed] is true
 * (at most every few seconds, never while the user touches the list); otherwise known rows keep
 * their position, rows that disappeared are dropped and new rows are appended.
 */
class StableOrder<K> {
    private var order: List<K> = emptyList()

    fun <T> apply(
        rows: List<T>,
        key: (T) -> K,
        comparator: Comparator<T>,
        resortAllowed: Boolean,
    ): List<T> {
        val result =
            if (resortAllowed || order.isEmpty()) {
                rows.sortedWith(comparator)
            } else {
                val byKey = rows.associateBy(key)
                val kept = order.mapNotNull { byKey[it] }
                val known = order.toHashSet()
                kept + rows.filter { key(it) !in known }.sortedWith(comparator)
            }
        order = result.map(key)
        return result
    }
}
