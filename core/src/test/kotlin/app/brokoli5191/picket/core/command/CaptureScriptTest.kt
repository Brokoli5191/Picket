package app.brokoli5191.picket.core.command

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * scripts/capture-fixtures.sh must record every read command the collectors run, with the same argv,
 * or real fixtures would silently miss a source.
 */
class CaptureScriptTest {
    private val script = File("../scripts/capture-fixtures.sh").readText()

    private fun shellForm(command: ShellCommand) =
        command.argv.mapIndexed { i, a ->
            if (i ==
                0
            ) {
                a.removePrefix(ShellCommand.BIN)
            } else {
                a
            }
        }

    @Test
    fun `every fixed read command is captured verbatim`() {
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
            ReadCommands.appOpsHolders("GET_USAGE_STATS"),
            ReadCommands.appOpsHolders("SYSTEM_ALERT_WINDOW"),
        ).forEach { command ->
            val line = shellForm(command).joinToString(" ")
            assertWithMessage(line)
                .that(
                    script.lines().any {
                        it.trim().matches(
                            Regex("capture filter_\\w+ " + Regex.escape(line)),
                        )
                    },
                ).isTrue()
        }
    }

    @Test
    fun `parameterised read commands are captured for a sample package, user 0, every key and role`() {
        val sample = "com.android.systemui"
        listOf(
            ReadCommands.activityServices(sample),
            ReadCommands.packageDump(sample),
            ReadCommands.appOps(sample, 0),
            ReadCommands.standbyBucket(sample, 0),
        ).forEach { command ->
            val expected =
                shellForm(
                    command,
                ).joinToString(" ").replace(sample, "\"\$SAMPLE_PKG\"").replace(" 0 ", " \"\$U\" ")
            assertWithMessage(expected).that(script).contains("capture filter_default $expected")
        }
        SecureKey.entries.forEach { key -> assertWithMessage(key.key).that(script).contains(key.key) }
        assertWithMessage(
            "role loop",
        ).that(
            script,
        ).contains("capture filter_default cmd role get-role-holders --user \"\$U\" \"android.app.role.\$role\"")
        Role.entries.forEach { role -> assertWithMessage(role.name).that(script).contains(role.name) }
    }
}
