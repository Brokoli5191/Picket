package app.brokoli5191.picket.core.process

import app.brokoli5191.picket.core.model.CpuTimes
import app.brokoli5191.picket.core.model.ProcessSample
import app.brokoli5191.picket.core.model.ProcessSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProcessTableTest {
    private fun p(
        pid: Int,
        uid: Int,
        name: String,
        ticks: Long,
        rss: Long,
        start: Long,
        adj: Int?,
    ) = ProcessSample(pid, 1, uid, name, name.takeLast(15), 'S', ticks, 0, start, rss, 3, adj)

    private val before =
        ProcessSnapshot(
            0,
            10_000,
            CpuTimes(1_000, 900, 8),
            100,
            4096,
            listOf(p(10, 10_001, "com.chat", 0, 100, 100, 0), p(20, 10_002, "com.maps", 0, 300, 50, 905)),
        )
    private val after =
        ProcessSnapshot(
            2_000,
            12_000,
            CpuTimes(2_000, 1_500, 8),
            100,
            4096,
            listOf(
                p(10, 10_001, "com.chat", 300, 100, 100, 0),
                p(11, 10_001, "com.chat:push", 0, 50, 900, 500),
                p(20, 10_002, "com.maps", 100, 300, 50, 905),
                p(30, 1000, "system_server", 50, 900, 1, -900),
            ),
        )

    private val labels =
        mapOf<GroupKey, GroupLabel>(
            GroupKey.App(10_001) to GroupLabel("Chat", "com.chat", isSystem = false),
            GroupKey.App(10_002) to GroupLabel("maps", "com.maps", isSystem = false),
            GroupKey.SystemUid(1000) to GroupLabel("System", null, isSystem = true),
        )

    private fun rows(): List<GroupRow> {
        val groups = ProcessGrouping { _, _ -> null }.group(after.processes)
        return ProcessTable.build(after, CpuUsage.between(before, after), groups) { key, _ -> labels.getValue(key) }
    }

    @Test
    fun `builds rows with cpu, memory, uptime and importance`() {
        val chat = rows().single { it.key == GroupKey.App(10_001) }
        assertThat(chat.processes.map { it.name }).containsExactly("com.chat", "com.chat:push").inOrder()
        assertThat(chat.cpuPercent).isWithin(1e-9).of(30.0)
        assertThat(chat.rssBytes).isEqualTo(150L * 4096)
        assertThat(chat.importance).isEqualTo(Importance.FOREGROUND)
        assertThat(chat.uptimeMs).isEqualTo(12_000L - 1_000)
        assertThat(chat.processes[1].userName).isEqualTo("u0_a1")
        assertThat(chat.processes[1].importance).isEqualTo(Importance.SERVICE)
    }

    @Test
    fun `filters system rows and searches labels, packages, process names and pids`() {
        val all = rows()
        assertThat(ProcessTable.filter(all, ProcessFilter()).map { it.label.title }).containsExactly("Chat", "maps")
        assertThat(ProcessTable.filter(all, ProcessFilter(showSystem = true))).hasSize(3)
        assertThat(
            ProcessTable
                .filter(all, ProcessFilter(query = "MAPS"))
                .single()
                .label.title,
        ).isEqualTo("maps")
        assertThat(
            ProcessTable
                .filter(all, ProcessFilter(query = ":push"))
                .single()
                .label.title,
        ).isEqualTo("Chat")
        assertThat(
            ProcessTable
                .filter(all, ProcessFilter(showSystem = true, query = "30"))
                .single()
                .label.title,
        ).isEqualTo("System")
        assertThat(
            ProcessTable
                .filter(all, ProcessFilter(query = "com.chat "))
                .single()
                .label.title,
        ).isEqualTo("Chat")
        assertThat(ProcessTable.filter(all, ProcessFilter(query = "nothing"))).isEmpty()
    }

    @Test
    fun `sort orders`() {
        val all = rows()

        fun titles(order: SortOrder) = all.sortedWith(ProcessTable.comparator(order)).map { it.label.title }
        assertThat(titles(SortOrder.CPU)).containsExactly("Chat", "maps", "System").inOrder()
        assertThat(titles(SortOrder.MEMORY)).containsExactly("System", "maps", "Chat").inOrder()
        assertThat(titles(SortOrder.NAME)).containsExactly("Chat", "maps", "System").inOrder()
        assertThat(titles(SortOrder.UPTIME)).containsExactly("System", "maps", "Chat").inOrder()
    }

    @Test
    fun `stable order freezes positions until a resort is allowed`() {
        val order = StableOrder<String>()
        val cmp = compareByDescending<Pair<String, Int>> { it.second }
        assertThat(order.apply(listOf("a" to 1, "b" to 2), { it.first }, cmp, resortAllowed = false).map { it.first })
            .containsExactly("b", "a")
            .inOrder()
        assertThat(
            order
                .apply(
                    listOf("a" to 9, "b" to 2, "c" to 5, "d" to 7),
                    { it.first },
                    cmp,
                    resortAllowed = false,
                ).map { it.first },
        ).containsExactly("b", "a", "d", "c").inOrder()
        assertThat(order.apply(listOf("a" to 9, "c" to 5), { it.first }, cmp, resortAllowed = false).map { it.first })
            .containsExactly("a", "c")
            .inOrder()
        assertThat(order.apply(listOf("a" to 1, "c" to 5), { it.first }, cmp, resortAllowed = true).map { it.first })
            .containsExactly("c", "a")
            .inOrder()
    }
}
