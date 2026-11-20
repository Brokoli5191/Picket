package app.brokoli5191.picket.core.parse

import app.brokoli5191.picket.core.Fixtures
import app.brokoli5191.picket.core.model.ForegroundServiceTypes
import app.brokoli5191.picket.core.model.IdleExemption
import app.brokoli5191.picket.core.model.StandbyBucket
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParsersTest {
    private fun fixture(name: String) = Fixtures.text("synthetic/aosp-main/$name")

    @Test
    fun durations() {
        assertThat(Durations.parse("+1d2h3m4s5ms")).isEqualTo(93_784_005)
        assertThat(Durations.parse("-42s1ms")).isEqualTo(-42_001)
        assertThat(Durations.parse("+12ms")).isEqualTo(12)
        assertThat(Durations.parse("0")).isEqualTo(0)
        assertThat(Durations.parse("--")).isNull()
        assertThat(Durations.parse("")).isNull()
        assertThat(Durations.parse("+")).isNull()
        assertThat(Durations.parse("+5x")).isNull()
        assertThat(Durations.parse("+5m garbage")).isNull()
    }

    @Test
    fun `role holders, role dump, DND grants and query-op`() {
        assertThat(RoleHolders.parse("com.a;com.b\n")).containsExactly("com.a", "com.b").inOrder()
        org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
            RoleHolders.parse("Unknown command: get-role-holders\n")
        }
        val dump = RoleHolders.parseDump(fixture("dumpsys_role.txt"), 10)
        assertThat(dump["android.app.role.BROWSER"]).containsExactly("com.android.chrome")
        assertThat(
            RoleHolders.parseDump("user_id=0\nname=r\nholders=[\ncom.a\ncom.b\n]\nname=s\nholders=com.c;com.d\n", 0),
        ).containsExactly("r", listOf("com.a", "com.b"), "s", listOf("com.c", "com.d"))
        assertThat(NotificationDumpParser.dndAccessPackages(fixture("dumpsys_notification_--zen.txt"), 10))
            .containsExactly("com.google.android.apps.wellbeing")
        assertThat(NotificationDumpParser.dndAccessPackages("nothing", 0)).isEmpty()
        assertThat(RoleHolders.parse("\n")).isEmpty()
        assertThat(RoleHolders.parse("com.a;bad pkg")).containsExactly("com.a")
        assertThat(AppOpsParser.parseQueryOp("No operations.\n")).isEmpty()
        assertThat(AppOpsParser.parseQueryOp("com.a\ncom.b\n")).containsExactly("com.a", "com.b")
    }

    @Test
    fun `appops get`() {
        val ops = AppOpsParser.parseGet(fixture("cmd_appops_get_--user_0_com.example.chat.txt"))
        assertThat(
            ops.map {
                it.op
            },
        ).containsExactly(
            "RUN_ANY_IN_BACKGROUND",
            "WAKE_LOCK",
            "POST_NOTIFICATION",
            "SYSTEM_ALERT_WINDOW",
            "CAMERA",
        )
        assertThat(ops.first().uidMode).isTrue()
        assertThat(ops.drop(1).none { it.uidMode }).isTrue()
        assertThat(ops.first { it.op == "WAKE_LOCK" }.lastAccess).isEqualTo("+3m1s2ms")
        assertThat(ops.first { it.op == "SYSTEM_ALERT_WINDOW" }.mode).isEqualTo("ignore")
        assertThat(
            AppOpsParser.modeOf("No operations.\nDefault mode: allow\n", "RUN_ANY_IN_BACKGROUND"),
        ).isEqualTo("default")
        assertThat(AppOpsParser.modeOf("RUN_ANY_IN_BACKGROUND: ignore\n", "RUN_ANY_IN_BACKGROUND")).isEqualTo("ignore")
        assertThat(AppOpsParser.modeOf("garbage", "X")).isNull()
    }

    @Test
    fun `device policy, notification dump and vpn`() {
        val admins = DevicePolicyParser.parse(fixture("dumpsys_device_policy.txt"))
        assertThat(admins.map { it.component.short to it.role })
            .containsExactly(
                "com.example.mdm/.AdminReceiver" to "device-owner",
                "com.google.android.gms/.mdm.receivers.MdmDeviceAdminReceiver" to null,
                "com.example.work/.WorkAdmin" to null,
            )
        val profileOwned =
            DevicePolicyParser.parse(
                "  Profile Owner (User 10): \n    admin=ComponentInfo{com.example.work/com.example.work.WorkAdmin}\n" +
                    "  Enabled Device Admins (User 10, provisioningState: 3):\n" +
                    "    com.example.work/.WorkAdmin:\n      uid=1\n",
            )
        assertThat(profileOwned.single().role).isEqualTo("profile-owner")

        val live = NotificationDumpParser.liveListeners(fixture("dumpsys_notification.txt"))
        assertThat(live.map { it.first.short to it.second }).containsExactly("com.example.notes/.NoteListener" to 0)

        assertThat(
            VpnParser.activeVpnPackages(fixture("dumpsys_vpn_management.txt")),
        ).containsExactly("com.example.vpn")
        assertThat(VpnParser.activeVpnPackages("VPNs:\n")).isEmpty()
    }

    @Test
    fun `deviceidle, standby buckets and held wakelocks`() {
        val idle = DeviceIdleParser.parse(fixture("cmd_deviceidle_whitelist.txt"))
        assertThat(idle["com.example.chat"]).isEqualTo(IdleExemption.USER)
        assertThat(idle["com.google.android.gms"]).isEqualTo(IdleExemption.SYSTEM)
        assertThat(DeviceIdleParser.parse("user,com.a,1\nsystem,com.a,1\nuser,com.a,1\nweird,com.b,2\nshort\n"))
            .containsExactly("com.a", IdleExemption.SYSTEM)

        assertThat(StandbyBucketParser.parseSingle("40\n")).isEqualTo(StandbyBucket.RARE)
        assertThat(StandbyBucketParser.parseSingle("restricted")).isEqualTo(StandbyBucket.RESTRICTED)
        assertThat(StandbyBucketParser.parseSingle("")).isNull()
        assertThat(StandbyBucketParser.parseAll("com.a: 10\ncom.b: 45\nbad line\n"))
            .containsExactly("com.a", StandbyBucket.ACTIVE, "com.b", StandbyBucket.RESTRICTED)
        assertThat(StandbyBucket.fromValue(99)).isNull()

        val locks = PowerParser.wakeLocks(fixture("dumpsys_power.txt"))
        assertThat(locks).hasSize(6)
        val gms = locks.first { it.tag == "GCoreFlp" }
        assertThat(gms.ownerUid).isEqualTo(10146)
        assertThat(gms.pid).isEqualTo(4021)
        assertThat(gms.workSourceUids).containsExactly(10146)
        assertThat(gms.acquiredAgoMs).isEqualTo(72_003)
        assertThat(locks.first { it.tag == "WeatherRefresh" }.disabled).isTrue()
        assertThat(locks.first { it.tag == "ChatPush:socket" }.workSourceUids).isEmpty()
        assertThat(PowerParser.wakeLocks("no section")).isEmpty()
    }

    @Test
    fun `package dump`() {
        val dump = PackageDumpParser.parse(fixture("dumpsys_package_com.example.chat.txt"), "com.example.chat")!!
        assertThat(dump.appId).isEqualTo(10_123)
        assertThat(dump.installer).isEqualTo("com.android.vending")
        assertThat(dump.enabledState).containsExactly(0, 0, 10, 3)
        val perms = dump.runtimePermissions.getValue(0)
        assertThat(perms.map { it.name to it.granted })
            .containsExactly(
                "android.permission.POST_NOTIFICATIONS" to true,
                "android.permission.CAMERA" to false,
                "android.permission.READ_CONTACTS" to true,
            )
        assertThat(perms.first { it.name.endsWith("READ_CONTACTS") }.flags).containsExactly("USER_SET", "USER_FIXED")
        assertThat(perms.none { it.fixed }).isTrue()
        assertThat(PackageDumpParser.parse(fixture("dumpsys_package_com.example.chat.txt"), "com.other")).isNull()
        val legacy = PackageDumpParser.parse("  Package [com.x] (1):\n    userId=10200\n", "com.x")!!
        assertThat(legacy.appId).isEqualTo(10_200)
    }

    @Test
    fun `active services across releases and users`() {
        val services = ActiveServicesParser.parse(fixture("dumpsys_activity_services.txt"))
        assertThat(services.map { it.component to it.userId })
            .containsExactly(
                "com.example.notes/.sync.SyncForegroundService" to 0,
                "com.example.notes/.audio.VoiceNotePlaybackService" to 0,
                "com.example.notes/.api.NotesApiService" to 10,
            ).inOrder()
        val sync = services[0]
        assertThat(sync.foreground).isTrue()
        assertThat(ForegroundServiceTypes.decode(sync.foregroundTypes!!)).containsExactly("dataSync")
        assertThat(sync.started).isTrue()
        assertThat(sync.pid).isEqualTo(12345)
        assertThat(services[2].bound).isTrue()
        val api34 =
            ActiveServicesParser.parse(
                "  * ServiceRecord{1 u0 com.a/.S}\n    isForeground=true foregroundId=1 types=00000008\n",
            )
        assertThat(api34.single().foregroundTypes).isEqualTo(8)
        assertThat(MeminfoParser.totalPssKb(fixture("dumpsys_meminfo_--checkin_12345.txt"))).isEqualTo(66_168)
        assertThat(MeminfoParser.totalPssKb("time,1,2\n")).isNull()
    }

    @Test
    fun `active services`() {
        val services = ActiveServicesParser.parse(fixture("dumpsys_activity_services_com.example.chat.txt"))
        assertThat(services).hasSize(2)
        val push = services[0]
        assertThat(push.component).isEqualTo("com.example.chat/.push.PushService")
        assertThat(push.userId).isEqualTo(0)
        assertThat(push.processName).isEqualTo("com.example.chat:push")
        assertThat(push.pid).isEqualTo(8123)
        assertThat(push.foreground).isTrue()
        assertThat(ForegroundServiceTypes.decode(push.foregroundTypes!!)).containsExactly("remoteMessaging")
        assertThat(push.started).isTrue()
        assertThat(push.bound).isFalse()
        assertThat(push.createdAgoMs).isEqualTo(7_384_005)
        assertThat(push.lastActivityAgoMs).isEqualTo(181_002)
        val sync = services[1]
        assertThat(sync.bound).isTrue()
        assertThat(sync.started).isFalse()
        assertThat(sync.foreground).isFalse()
        assertThat(sync.foregroundTypes).isNull()
        assertThat(ActiveServicesParser.parse("")).isEmpty()
    }
}
