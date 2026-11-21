package app.brokoli5191.picket.core.parse.proc

import app.brokoli5191.picket.core.Fixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProcParsersTest {
    private val base = "synthetic/linux-proc/sample1"

    @Test
    fun `parses stat of an app process`() {
        val stat = ProcParsers.parseStat(Fixtures.text("$base/12345/stat"))!!
        assertThat(stat.pid).isEqualTo(12345)
        assertThat(stat.comm).isEqualTo("id.gms.unstabl")
        assertThat(stat.state).isEqualTo('S')
        assertThat(stat.ppid).isEqualTo(1043)
        assertThat(stat.userTicks).isEqualTo(70211)
        assertThat(stat.systemTicks).isEqualTo(24310)
        assertThat(stat.threads).isEqualTo(87)
        assertThat(stat.startTicks).isEqualTo(812345)
        assertThat(stat.rssPages).isEqualTo(46911)
    }

    @Test
    fun `comm may contain spaces and parentheses`() {
        val stat = ProcParsers.parseStat(Fixtures.text("$base/4321/stat"))!!
        assertThat(stat.comm).isEqualTo("my (weird) proc")
        assertThat(stat.state).isEqualTo('R')
        assertThat(stat.threads).isEqualTo(2)
    }

    @Test
    fun `rejects truncated or malformed stat`() {
        assertThat(ProcParsers.parseStat("")).isNull()
        assertThat(ProcParsers.parseStat("12 (x) S 1 2 3")).isNull()
        assertThat(ProcParsers.parseStat("abc (x) S")).isNull()
        assertThat(ProcParsers.parseStat("12 x) S")).isNull()
        val good = Fixtures.text("$base/1/stat")
        assertThat(ProcParsers.parseStat(good.replace(" S ", " SS "))).isNull()
        assertThat(ProcParsers.parseStat(good.replace(" 247 6115 ", " x 6115 "))).isNull()
    }

    @Test
    fun `status uid and rss`() {
        val status = Fixtures.text("$base/12345/status")
        assertThat(ProcParsers.parseStatusUid(status)).isEqualTo(10123)
        assertThat(ProcParsers.parseStatusRssKb(status)).isEqualTo(187644)
        val kernel = Fixtures.text("$base/2/status")
        assertThat(ProcParsers.parseStatusUid(kernel)).isEqualTo(0)
        assertThat(ProcParsers.parseStatusRssKb(kernel)).isNull()
        assertThat(ProcParsers.parseStatusUid("Name:\tx\n")).isNull()
    }

    @Test
    fun `cmdline takes argv0`() {
        assertThat(ProcParsers.parseCmdline(Fixtures.bytes("$base/1/cmdline"))).isEqualTo("/system/bin/init")
        assertThat(ProcParsers.parseCmdline(Fixtures.bytes("$base/1043/cmdline"))).isEqualTo("zygote64")
        assertThat(ProcParsers.parseCmdline(Fixtures.bytes("$base/12345/cmdline")))
            .isEqualTo("com.google.android.gms.unstable")
        assertThat(ProcParsers.parseCmdline(ByteArray(0))).isNull()
        assertThat(ProcParsers.parseCmdline("  ".toByteArray())).isNull()
        assertThat(ProcParsers.parseCmdline("no-nul".toByteArray())).isEqualTo("no-nul")
    }

    @Test
    fun `oom score adj is bounded`() {
        assertThat(ProcParsers.parseOomScoreAdj("905\n")).isEqualTo(905)
        assertThat(ProcParsers.parseOomScoreAdj("-1000")).isEqualTo(-1000)
        assertThat(ProcParsers.parseOomScoreAdj("1001")).isNull()
        assertThat(ProcParsers.parseOomScoreAdj("x")).isNull()
    }

    @Test
    fun `cpu times exclude guest and count online cpus`() {
        val cpu = ProcParsers.parseCpuTimes(Fixtures.text("$base/stat"))!!
        assertThat(cpu.totalTicks).isEqualTo(1061204L + 76450 + 813254 + 31024731 + 21456 + 112370 + 43987 + 0)
        assertThat(cpu.idleTicks).isEqualTo(31024731L + 21456)
        assertThat(cpu.onlineCpus).isEqualTo(8)
        assertThat(ProcParsers.parseCpuTimes("cpu  1 2 3\n")).isNull()
        assertThat(ProcParsers.parseCpuTimes("cpu  a b c d\n")).isNull()
        assertThat(ProcParsers.parseCpuTimes("intr 1\n")).isNull()
        assertThat(ProcParsers.parseCpuTimes("cpu  1 2 3 4\n")!!.idleTicks).isEqualTo(4)
    }

    @Test
    fun `uptime in milliseconds`() {
        assertThat(ProcParsers.parseUptimeMs(Fixtures.text("$base/uptime"))).isEqualTo(35_021_370)
        assertThat(ProcParsers.parseUptimeMs("12")).isEqualTo(12_000)
        assertThat(ProcParsers.parseUptimeMs("nope")).isNull()
    }
}
