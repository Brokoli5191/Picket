package app.brokoli5191.picket.core.command

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadCommandsTest {
    @Test
    fun `parameterised commands validate every variable part`() {
        assertThat(ReadCommands.standbyBucket("com.example", 10).display())
            .isEqualTo("/system/bin/am get-standby-bucket --user 10 com.example")
        assertThat(ReadCommands.meminfo(42).argv.last()).isEqualTo("42")
        listOf(
            { ReadCommands.packageDump("com.example;reboot") },
            { ReadCommands.activityServices("../x") },
            { ReadCommands.appOps("com.example", -1) },
            { ReadCommands.roleHolders(Role.SMS, 99_999) },
            { ReadCommands.appOpsHolders("get_usage_stats") },
            { ReadCommands.appOpsHolders("X;Y") },
            { ReadCommands.meminfo(0) },
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun `fixed commands only use whitelisted binaries`() {
        val all =
            listOf(
                ReadCommands.activityServices,
                ReadCommands.notification,
                ReadCommands.devicePolicy,
                ReadCommands.vpnManagement,
                ReadCommands.alarm,
                ReadCommands.jobScheduler,
                ReadCommands.power,
                ReadCommands.batteryStatsCheckin,
                ReadCommands.idleWhitelist,
                ReadCommands.inputMethods,
                ReadCommands.homeActivity,
                ReadCommands.secure(SecureKey.DEFAULT_INPUT_METHOD),
                ReadCommands.roleHolders(Role.HOME, 0),
            )
        all.forEach { assertThat(ShellCommand.ALLOWED_BINARIES).contains(it.argv[0]) }
    }
}
