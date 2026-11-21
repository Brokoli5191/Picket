package app.brokoli5191.picket.core.parse

import app.brokoli5191.picket.core.FixtureExecutor
import app.brokoli5191.picket.core.Fixtures
import app.brokoli5191.picket.core.collect.WakeupsCollector
import app.brokoli5191.picket.core.command.CommandOutput
import app.brokoli5191.picket.core.model.WakeupKind
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class WakeupParsersTest {
    private fun fixture(name: String) = Fixtures.text("synthetic/aosp-main/$name")

    @Test
    fun `uid names`() {
        assertThat(UidNames.parse("u0a123")).isEqualTo(10_123)
        assertThat(UidNames.parse("u10a5")).isEqualTo(1_010_005)
        assertThat(UidNames.parse("u0i12")).isEqualTo(99_012)
        assertThat(UidNames.parse("1000")).isEqualTo(1000)
        assertThat(UidNames.parse("bogus")).isNull()
    }

    @Test
    fun `alarm history and stats`() {
        val text = fixture("dumpsys_alarm.txt")
        val history = AlarmDumpParser.history(text).associateBy { it.packageName }
        assertThat(history.getValue("com.example.chat").relativeMs).hasSize(5)
        assertThat(history.getValue("com.example.chat").relativeMs.first()).isEqualTo(-181_002)
        assertThat(history.getValue("com.google.android.gms").userId).isEqualTo(0)

        val stats = AlarmDumpParser.stats(text).associateBy { it.packageName }
        val chat = stats.getValue("com.example.chat")
        assertThat(chat.uid).isEqualTo(10_123)
        assertThat(chat.wakeups).isEqualTo(349)
        assertThat(chat.tags.map { it.tag to it.wakeups })
            .containsExactly(
                "*walarm*:com.example.chat.ACTION_KEEPALIVE" to 288,
                "*walarm*:com.example.chat/.push.ReconnectReceiver" to 61,
                "*alarm*:com.example.chat.UI_REFRESH" to 0,
            ).inOrder()
        assertThat(chat.tags.first().lastRelativeMs).isEqualTo(-181_002)
        assertThat(stats.getValue("android").uid).isEqualTo(1000)
        assertThat(AlarmDumpParser.stats("nothing")).isEmpty()
        assertThat(AlarmDumpParser.history("nothing")).isEmpty()
    }

    @Test
    fun `job history`() {
        val events = JobHistoryParser.parse(fixture("dumpsys_jobscheduler.txt"))
        assertThat(events).hasSize(11)
        val first = events.first()
        assertThat(first.start).isTrue()
        assertThat(first.uid).isEqualTo(10_123)
        assertThat(first.jobId).isEqualTo(1001)
        assertThat(first.component).isEqualTo("com.example.chat/.sync.SyncJobService")
        assertThat(first.relativeMs).isEqualTo(-(3_600_000L + 44 * 60_000 + 59_790))
        assertThat(events.last().relativeMs).isEqualTo(-2_310)
        assertThat(JobHistoryParser.parse("no history")).isEmpty()
    }

    @Test
    fun `batterystats checkin`() {
        val checkin = BatteryStatsCheckinParser.parse(fixture("dumpsys_batterystats_-c_android.txt"))
        assertThat(checkin.startClockMs).isEqualTo(1_795_158_600_000)
        assertThat(
            checkin.packagesByUid.getValue(10_146),
        ).containsExactly("com.google.android.gms", "com.google.android.gsf")
        val chat = checkin.totals.getValue(10_123)
        assertThat(chat.wakelockMs).isEqualTo(61_234L + 98_765 + 101_234)
        assertThat(chat.wakelockCount).isEqualTo(49 + 31 + 211)
        assertThat(chat.jobMs).isEqualTo(98_012)
        assertThat(chat.jobCount).isEqualTo(31)
        assertThat(chat.alarmWakeups).isEqualTo(37 + 3 + 9)
        assertThat(BatteryStatsCheckinParser.splitCsv("a,\"b,c\",d")).containsExactly("a", "b,c", "d").inOrder()
        val broken = BatteryStatsCheckinParser.parse("9,x,l,wl\n9,1,l,wl,a,1\n9,1,l,jb,x\n9,1,l,wua,t,n\n")
        assertThat(broken.totals).isEmpty()
    }

    @Test
    fun `wakeups report combines totals and a windowed timeline`() {
        val now = 1_795_185_000_000L
        val collector = WakeupsCollector(FixtureExecutor(Fixtures.dir("synthetic/aosp-main"))) { now }
        val report = collector.collect(6 * 3_600_000L)
        assertThat(report.untilMs).isEqualTo(now)
        assertThat(report.totalsSinceMs).isEqualTo(1_795_158_600_000)
        assertThat(report.apps.map { it.total }).isInOrder(Comparator.reverseOrder<Int>())
        val chat = report.apps.single { it.packageName == "com.example.chat" }
        assertThat(chat.alarmWakeups).isEqualTo(49)
        assertThat(chat.jobCount).isEqualTo(31)
        assertThat(report.events.map { it.atMs }).isInOrder(Comparator.reverseOrder<Long>())
        val jobs = report.events.filter { it.kind == WakeupKind.JOB && it.packageName == "com.example.chat" }
        assertThat(jobs).hasSize(3)
        assertThat(jobs.first().durationMs).isEqualTo(3_150)
        assertThat(
            report.events.count { it.kind == WakeupKind.ALARM && it.packageName == "com.example.chat" },
        ).isAtLeast(5)
        assertThat(report.sources.all { it.ok }).isTrue()
        assertThat(collector.commandsRun).isEqualTo(3)

        val oneHour = collector.collect(3_600_000L)
        assertThat(oneHour.events.all { it.atMs >= now - 3_600_000L }).isTrue()
        assertThrows(IllegalArgumentException::class.java) { collector.collect(1) }
    }

    @Test
    fun `without batterystats the totals fall back to alarm stats`() {
        val dir = Fixtures.dir("synthetic/aosp-main")
        val collector =
            WakeupsCollector({ cmd ->
                if ("batterystats" in cmd.argv) CommandOutput(1, "", "denied") else FixtureExecutor(dir).run(cmd)
            }) { 1_795_185_000_000L }
        val report = collector.collect(3_600_000L)
        assertThat(report.totalsSinceMs).isNull()
        assertThat(report.apps.first { it.packageName == "com.example.chat" }.alarmWakeups).isEqualTo(349)
        assertThat(report.sources.single { !it.ok }.source).isEqualTo("battery statistics")
    }
}
