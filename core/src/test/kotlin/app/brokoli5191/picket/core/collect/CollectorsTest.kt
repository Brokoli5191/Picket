package app.brokoli5191.picket.core.collect

import app.brokoli5191.picket.core.FixtureExecutor
import app.brokoli5191.picket.core.Fixtures
import app.brokoli5191.picket.core.command.CommandExecutor
import app.brokoli5191.picket.core.command.CommandOutput
import app.brokoli5191.picket.core.model.IdleExemption
import app.brokoli5191.picket.core.model.StandbyBucket
import app.brokoli5191.picket.core.model.WatcherEntry
import app.brokoli5191.picket.core.model.WatcherKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CollectorsTest {
    private val aospMain = Fixtures.dir("synthetic/aosp-main")

    @Test
    fun `watchers from every source`() {
        val snapshot = WatchersCollector(FixtureExecutor(aospMain)).collect()
        val byKind = snapshot.entries.groupBy { it.kind }

        assertThat(byKind.getValue(WatcherKind.NOTIFICATION_LISTENER).map { it.packageName to it.active })
            .containsExactly("com.example.notes" to true, "com.google.android.gms" to false)
        assertThat(byKind.getValue(WatcherKind.ACCESSIBILITY_SERVICE).map { it.component })
            .containsExactly("com.example.pass/.AutofillA11y", "com.google.android.marvin.talkback/.TalkBackService")
        assertThat(byKind.getValue(WatcherKind.DEVICE_ADMIN).map { Triple(it.packageName, it.userId, it.detail) })
            .containsExactly(
                Triple("com.example.mdm", 0, "device-owner"),
                Triple("com.google.android.gms", 0, null),
                Triple("com.example.work", 10, null),
            )
        assertThat(byKind.getValue(WatcherKind.INPUT_METHOD).map { it.packageName to it.detail })
            .containsExactly("com.samsung.android.honeyboard" to null, "org.example.keyboard" to "current")
        assertThat(byKind.getValue(WatcherKind.USAGE_ACCESS).map { it.packageName })
            .containsExactly("com.example.launcher", "com.google.android.gms")
        assertThat(byKind[WatcherKind.OVERLAY]).isNull()
        assertThat(byKind.getValue(WatcherKind.VPN)).containsExactly(WatcherEntry(WatcherKind.VPN, "com.example.vpn"))
        assertThat(byKind.getValue(WatcherKind.DEFAULT_ROLE).map { it.detail to it.packageName })
            .containsExactly(
                "sms" to "com.google.android.apps.messaging",
                "dialer" to "com.samsung.android.dialer",
                "browser" to "org.mozilla.firefox",
                "home" to "com.sec.android.app.launcher",
                "home" to "com.example.launcher",
            )
        assertThat(
            byKind.getValue(WatcherKind.DND_ACCESS).map {
                it.packageName
            },
        ).containsExactly("com.example.notes", "com.android.settings")
        assertThat(snapshot.sources.all { it.ok }).isTrue()
    }

    @Test
    fun `a failing source is reported and does not hide the others`() {
        val broken =
            CommandExecutor { command ->
                if ("device_policy" in command.argv) {
                    CommandOutput(255, "", "Can't find service: device_policy")
                } else if ("vpn_management" in command.argv) {
                    CommandOutput(-1, "", "", timedOut = true)
                } else if ("notification" in command.argv && "dumpsys" in command.argv[0]) {
                    throw IllegalStateException("boom")
                } else {
                    FixtureExecutor(aospMain).run(command)
                }
            }
        val collector = WatchersCollector(broken)
        val snapshot = collector.collect()
        val failed = snapshot.sources.filterNot { it.ok }.associate { it.source to it.message }
        assertThat(failed.keys).containsExactly("device admins", "active VPN", "live notification listeners")
        assertThat(failed["device admins"]).contains("exit 255")
        assertThat(failed["active VPN"]).isEqualTo("timed out")
        assertThat(snapshot.entries.none { it.kind == WatcherKind.DEVICE_ADMIN }).isTrue()
        // Without the live list every approved listener counts as active.
        assertThat(snapshot.entries.filter { it.kind == WatcherKind.NOTIFICATION_LISTENER }.all { it.active }).isTrue()
        assertThat(collector.commandsRun).isGreaterThan(10)
    }

    @Test
    fun `a parser surprise becomes a failed source`() {
        val weird = CommandExecutor { CommandOutput(0, "") }
        val snapshot = WatchersCollector(weird).collect()
        assertThat(snapshot.entries).isEmpty()
        assertThat(snapshot.sources.all { it.ok }).isTrue()
    }

    @Test
    fun `always-on VPN without an active VPN is reported as inactive`() {
        val executor =
            CommandExecutor { command ->
                when {
                    "always_on_vpn_app" in command.argv -> CommandOutput(0, "com.example.vpn\n")
                    "vpn_management" in command.argv -> CommandOutput(0, "VPNs:\n")
                    else -> CommandOutput(0, "")
                }
            }
        val vpn = WatchersCollector(executor).collect().entries.single { it.kind == WatcherKind.VPN }
        assertThat(
            vpn,
        ).isEqualTo(WatcherEntry(WatcherKind.VPN, "com.example.vpn", detail = "always-on", active = false))
    }

    @Test
    fun `app detail combines services, bucket, exemption, permissions and app-ops`() {
        val collector = AppDetailCollector(FixtureExecutor(aospMain))
        val detail = collector.collect("com.example.chat", 0)
        assertThat(detail.uid).isEqualTo(10_123)
        assertThat(detail.installer).isEqualTo("com.android.vending")
        assertThat(detail.enabledState).isEqualTo("default")
        assertThat(detail.standbyBucket).isEqualTo(StandbyBucket.ACTIVE)
        assertThat(detail.idleExemption).isEqualTo(IdleExemption.USER)
        assertThat(
            detail.services.map {
                it.component
            },
        ).containsExactly("com.example.chat/.push.PushService", "com.example.chat/.sync.ChatSyncService")
        assertThat(detail.runtimePermissions.filter { it.granted }.map { it.name })
            .containsExactly("android.permission.POST_NOTIFICATIONS", "android.permission.READ_CONTACTS")
        assertThat(detail.appOps.first { it.op == "RUN_ANY_IN_BACKGROUND" }.uidMode).isTrue()
        assertThat(detail.sources.all { it.ok }).isTrue()
        assertThat(collector.commandsRun).isEqualTo(5)
    }

    @Test
    fun `app detail survives missing sources`() {
        val detail = AppDetailCollector { CommandOutput(1, "", "nope") }.collect("com.example.chat", 0)
        assertThat(detail.services).isEmpty()
        assertThat(detail.standbyBucket).isNull()
        assertThat(detail.idleExemption).isNull()
        assertThat(detail.uid).isNull()
        assertThat(detail.sources).hasSize(5)
        assertThat(detail.sources.none { it.ok }).isTrue()
    }
}
