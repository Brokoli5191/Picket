package app.brokoli5191.picket.core.process

import app.brokoli5191.picket.core.model.CpuTimes
import app.brokoli5191.picket.core.model.ProcessSample
import app.brokoli5191.picket.core.model.ProcessSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProcessLogicTest {
    private fun sample(
        pid: Int,
        uid: Int = 10_123,
        name: String = "com.example",
        ppid: Int = 1043,
        user: Long = 0,
        system: Long = 0,
        start: Long = 100,
        adj: Int? = 0,
    ) = ProcessSample(pid, ppid, uid, name, name.takeLast(15), 'S', user, system, start, 10, 1, adj)

    private fun snapshot(
        elapsed: Long,
        total: Long,
        idle: Long,
        vararg processes: ProcessSample,
    ) = ProcessSnapshot(elapsed, elapsed, CpuTimes(total, idle, 8), 100, 4096, processes.toList())

    @Test
    fun `cpu usage is a share of the whole machine`() {
        val before = snapshot(0, 10_000, 8_000, sample(10, user = 100, system = 50), sample(11, user = 10))
        val after =
            snapshot(
                2_000,
                11_600,
                9_000,
                sample(10, user = 300, system = 90),
                sample(11, user = 10),
                sample(12, user = 500),
            )
        val usage = CpuUsage.between(before, after)
        assertThat(usage.intervalMs).isEqualTo(2_000)
        assertThat(usage.totalPercent).isWithin(1e-9).of(100.0 * 600 / 1600)
        assertThat(usage[ProcessKey(10, 100)]).isWithin(1e-9).of(100.0 * 240 / 1600)
        assertThat(usage.percentOf(sample(11))).isEqualTo(0.0)
        assertThat(usage[ProcessKey(12, 100)]).isNull()
    }

    @Test
    fun `cpu usage ignores recycled pids and odd deltas`() {
        val before = snapshot(0, 10_000, 8_000, sample(10, user = 100, start = 1))
        val after = snapshot(1_000, 10_100, 8_050, sample(10, user = 5, start = 2))
        assertThat(CpuUsage.between(before, after)[ProcessKey(10, 2)]).isNull()
        assertThat(CpuUsage.between(null, after)).isSameInstanceAs(CpuUsage.NONE)
        assertThat(CpuUsage.between(after, after)).isSameInstanceAs(CpuUsage.NONE)
        val spike = snapshot(1_000, 10_100, 8_000, sample(10, user = 100_000, start = 1))
        assertThat(CpuUsage.between(before, spike)[ProcessKey(10, 1)]).isEqualTo(100.0)
    }

    @Test
    fun `importance bands`() {
        assertThat(Importance.fromOomScoreAdj(null)).isEqualTo(Importance.UNKNOWN)
        assertThat(Importance.fromOomScoreAdj(-1000)).isEqualTo(Importance.SYSTEM)
        assertThat(Importance.fromOomScoreAdj(-800)).isEqualTo(Importance.SYSTEM)
        assertThat(Importance.fromOomScoreAdj(0)).isEqualTo(Importance.FOREGROUND)
        assertThat(Importance.fromOomScoreAdj(50)).isEqualTo(Importance.FOREGROUND)
        assertThat(Importance.fromOomScoreAdj(100)).isEqualTo(Importance.VISIBLE)
        assertThat(Importance.fromOomScoreAdj(200)).isEqualTo(Importance.PERCEPTIBLE)
        assertThat(Importance.fromOomScoreAdj(250)).isEqualTo(Importance.PERCEPTIBLE)
        assertThat(Importance.fromOomScoreAdj(500)).isEqualTo(Importance.SERVICE)
        assertThat(Importance.fromOomScoreAdj(800)).isEqualTo(Importance.SERVICE)
        assertThat(Importance.fromOomScoreAdj(900)).isEqualTo(Importance.CACHED)
        assertThat(Importance.fromOomScoreAdj(999)).isEqualTo(Importance.CACHED)
        assertThat(Importance.fromOomScoreAdj(1001)).isEqualTo(Importance.UNKNOWN)
    }

    @Test
    fun `uid arithmetic and names`() {
        assertThat(Uids.userId(1_010_123)).isEqualTo(10)
        assertThat(Uids.appId(1_010_123)).isEqualTo(10_123)
        assertThat(Uids.displayName(10_123)).isEqualTo("u0_a123")
        assertThat(Uids.displayName(1_510_123)).isEqualTo("u15_a123")
        assertThat(Uids.displayName(99_012)).isEqualTo("u0_i12")
        assertThat(Uids.displayName(90_003)).isEqualTo("u0_ai3")
        assertThat(Uids.displayName(20_123)).isEqualTo("u0_s123")
        assertThat(Uids.displayName(1000)).isEqualTo("system")
        assertThat(Uids.displayName(1_001_000)).isEqualTo("u10_system")
        assertThat(Uids.displayName(1234)).isEqualTo("1234")
        assertThat(Uids.isApp(10_123)).isTrue()
        assertThat(Uids.isIsolated(99_012)).isTrue()
        assertThat(Uids.isSdkSandbox(20_123)).isTrue()
        assertThat(Uids.appUidOfSdkSandbox(20_123)).isEqualTo(10_123)
    }

    @Test
    fun `groups by owner`() {
        val packages = mapOf("com.android.chrome" to 10_200)
        val grouping = ProcessGrouping { pkg, user -> packages[pkg]?.plus(user * Uids.PER_USER_RANGE) }
        val groups =
            grouping
                .group(
                    listOf(
                        sample(1, uid = 0, name = "/system/bin/init", ppid = 0),
                        sample(2, uid = 0, name = "kthreadd", ppid = 0),
                        sample(45, uid = 0, name = "kworker/0:1", ppid = 2),
                        sample(300, uid = 10_200, name = "com.android.chrome"),
                        sample(301, uid = 99_012, name = "com.android.chrome:sandboxed_process0:org.chromium.X:0"),
                        sample(302, uid = 90_001, name = "com.android.chrome_zygote"),
                        sample(303, uid = 99_013, name = "unknown_isolated"),
                        sample(304, uid = 20_200, name = "com.android.chrome_sdk_sandbox"),
                        sample(305, uid = -1, name = "secretd"),
                    ),
                ).associate { it.key to it.processes.map { p -> p.pid } }

        assertThat(groups[GroupKey.SystemUid(0)]).containsExactly(1)
        assertThat(groups[GroupKey.Kernel]).containsExactly(2, 45).inOrder()
        assertThat(groups[GroupKey.App(10_200)]).containsExactly(300, 301, 302, 304).inOrder()
        assertThat(groups[GroupKey.UnattributedIsolated]).containsExactly(303)
        assertThat(groups[GroupKey.SystemUid(-1)]).containsExactly(305)
    }

    @Test
    fun `owner package of process names`() {
        assertThat(ProcessGrouping.ownerPackageOf("com.example:remote")).isEqualTo("com.example")
        assertThat(ProcessGrouping.ownerPackageOf("com.example_zygote")).isEqualTo("com.example")
        assertThat(ProcessGrouping.ownerPackageOf("surfaceflinger")).isNull()
        assertThat(ProcessGrouping.ownerPackageOf("/system/bin/x")).isNull()
        assertThat(ProcessGrouping.ownerPackageOf(":x")).isNull()
    }
}
