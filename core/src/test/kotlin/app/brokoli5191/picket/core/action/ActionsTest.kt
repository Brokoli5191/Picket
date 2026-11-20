package app.brokoli5191.picket.core.action

import app.brokoli5191.picket.core.FixtureExecutor
import app.brokoli5191.picket.core.Fixtures
import app.brokoli5191.picket.core.command.CommandExecutor
import app.brokoli5191.picket.core.command.CommandOutput
import app.brokoli5191.picket.core.command.Role
import app.brokoli5191.picket.core.command.ShellCommand
import app.brokoli5191.picket.core.ipc.PayloadCodec
import app.brokoli5191.picket.core.model.StandbyBucket
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ActionsTest {
    private val pkg = "com.example.chat"

    private val everyAction: List<Action> =
        listOf(
            Action.ForceStop(pkg, 0),
            Action.KillBackground(pkg, 0),
            Action.StopService(pkg, 0, "$pkg/.push.PushService"),
            Action.SetAppOp(pkg, 0, AppOp.RUN_ANY_IN_BACKGROUND, AppOpMode.IGNORE),
            Action.SetStandbyBucket(pkg, 0, StandbyBucket.RESTRICTED),
            Action.SetNotificationListener(pkg, 0, "$pkg/.Listener", allowed = false),
            Action.SetAccessibilityServices(pkg, 0, "com.a/.A:com.b/com.b.B"),
            Action.SetAccessibilityServices(pkg, 0, ""),
            Action.SetPermission(pkg, 0, "android.permission.CAMERA", granted = false),
            Action.SetIdleExemption(pkg, 0, exempt = false),
            Action.SetRoleHolder(pkg, 0, Role.BROWSER, holder = false),
            Action.SetEnabled(pkg, 0, EnabledState.DISABLED_USER),
            Action.SetEnabled(pkg, 0, EnabledState.ENABLED),
            Action.SetEnabled(pkg, 0, EnabledState.DEFAULT),
            Action.SetDndAccess(pkg, 0, allowed = false),
        )

    @Test
    fun `argv of every action calls cmd directly`() {
        val display = everyAction.associateWith { ActionPolicy.command(it).display() }
        assertThat(display.values.all { it.startsWith("/system/bin/cmd ") }).isTrue()
        assertThat(ActionPolicy.command(Action.ForceStop(pkg, 10)).display())
            .isEqualTo("/system/bin/cmd activity force-stop --user 10 com.example.chat")
        assertThat(ActionPolicy.command(Action.SetIdleExemption(pkg, 0, exempt = true)).argv.last()).isEqualTo("+$pkg")
        assertThat(ActionPolicy.command(Action.SetAccessibilityServices(pkg, 0, "")).argv)
            .containsExactly(
                "/system/bin/cmd",
                "settings",
                "--user",
                "0",
                "delete",
                "secure",
                "enabled_accessibility_services",
            ).inOrder()
        assertThat(ActionPolicy.command(Action.SetNotificationListener(pkg, 0, "$pkg/.Listener", allowed = true)).argv)
            .containsExactly("/system/bin/cmd", "notification", "allow_listener", "$pkg/.Listener", "0")
            .inOrder()
    }

    @Test
    fun `actions survive the binder round trip`() {
        everyAction.forEach { action ->
            val bytes = PayloadCodec.encode(Action.serializer(), action)
            assertThat(PayloadCodec.decode(Action.serializer(), bytes)).isEqualTo(action)
        }
    }

    @Test
    fun `injection attempts are rejected for every field`() {
        val hostile = listOf("x;reboot", "com.a && id", "com.a\$(id)", "../../data", "com.a|sh", "com.a\nid", "")
        hostile.forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { ActionPolicy.command(Action.ForceStop(bad, 0)) }
            assertThrows(
                IllegalArgumentException::class.java,
            ) { ActionPolicy.command(Action.StopService(pkg, 0, "$pkg/$bad")) }
            assertThrows(
                IllegalArgumentException::class.java,
            ) { ActionPolicy.command(Action.SetPermission(pkg, 0, bad, false)) }
            assertThrows(IllegalArgumentException::class.java) {
                ActionPolicy.command(Action.SetAccessibilityServices(pkg, 0, "com.a/.A:$bad"))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { ActionPolicy.command(Action.ForceStop(pkg, -1)) }
        assertThrows(IllegalArgumentException::class.java) { ActionPolicy.command(Action.ForceStop(pkg, 21_474)) }
        // A component of another package cannot ride on an action for this one.
        assertThrows(
            IllegalArgumentException::class.java,
        ) { ActionPolicy.command(Action.StopService(pkg, 0, "com.other/.S")) }
        assertThrows(IllegalArgumentException::class.java) {
            ActionPolicy.command(Action.SetNotificationListener(pkg, 0, "com.other/.L", false))
        }
        val tooMany = List(65) { "com.a$it/.S" }.joinToString(":")
        assertThrows(IllegalArgumentException::class.java) {
            ActionPolicy.command(Action.SetAccessibilityServices(pkg, 0, tooMany))
        }
    }

    @Test
    fun `risk and disruption`() {
        assertThat(ActionPolicy.risk(Action.SetEnabled(pkg, 0, EnabledState.DISABLED_USER))).isEqualTo(Risk.HIGH)
        assertThat(ActionPolicy.risk(Action.SetEnabled(pkg, 0, EnabledState.DEFAULT))).isEqualTo(Risk.LOW)
        assertThat(ActionPolicy.risk(Action.SetNotificationListener(pkg, 0, "$pkg/.L", false))).isEqualTo(Risk.MEDIUM)
        assertThat(everyAction.map(ActionPolicy::risk).toSet()).containsExactly(Risk.LOW, Risk.MEDIUM, Risk.HIGH)
        assertThat(ActionPolicy.isDisruptive(Action.ForceStop(pkg, 0))).isTrue()
        assertThat(ActionPolicy.isDisruptive(Action.SetStandbyBucket(pkg, 0, StandbyBucket.RESTRICTED))).isTrue()
        assertThat(ActionPolicy.isDisruptive(Action.SetStandbyBucket(pkg, 0, StandbyBucket.ACTIVE))).isFalse()
        assertThat(
            ActionPolicy.isDisruptive(Action.SetAppOp(pkg, 0, AppOp.RUN_ANY_IN_BACKGROUND, AppOpMode.IGNORE)),
        ).isTrue()
        assertThat(
            ActionPolicy.isDisruptive(Action.SetAppOp(pkg, 0, AppOp.SYSTEM_ALERT_WINDOW, AppOpMode.IGNORE)),
        ).isFalse()
        assertThat(ActionPolicy.isDisruptive(Action.SetEnabled(pkg, 0, EnabledState.ENABLED))).isFalse()
        assertThat(
            ActionPolicy.isDisruptive(Action.SetPermission(pkg, 0, "android.permission.CAMERA", false)),
        ).isFalse()
    }

    @Test
    fun `outcomes that lie with their exit code`() {
        val stop = Action.StopService(pkg, 0, "$pkg/.S")
        assertThat(
            ActionPolicy
                .outcome(
                    stop,
                    CommandOutput(255, "Stopping service: Intent { cmp=x }", "Service stopped"),
                ).success,
        ).isTrue()
        val notRunning = ActionPolicy.outcome(stop, CommandOutput(255, "", "Service not stopped: was not running."))
        assertThat(notRunning.success).isFalse()
        assertThat(notRunning.note).contains("not running")
        assertThat(
            ActionPolicy.outcome(stop, CommandOutput(255, "", "Error stopping service")).note,
        ).contains("exported")
        val listener = Action.SetNotificationListener(pkg, 0, "$pkg/.L", false)
        assertThat(
            ActionPolicy.outcome(listener, CommandOutput(0, "Error occurred: invalid component\n")).success,
        ).isFalse()
        assertThat(ActionPolicy.outcome(listener, CommandOutput(0, "")).success).isTrue()
        val disable = Action.SetEnabled(pkg, 0, EnabledState.DISABLED_USER)
        assertThat(
            ActionPolicy.outcome(disable, CommandOutput(0, "Package $pkg new state: disabled-user")).success,
        ).isTrue()
        assertThat(ActionPolicy.outcome(disable, CommandOutput(0, "")).success).isFalse()
        assertThat(
            ActionPolicy.outcome(disable, CommandOutput(-1, "", "", timedOut = true)).note,
        ).isEqualTo("timed out")
        assertThat(
            ActionPolicy
                .outcome(
                    Action.ForceStop(pkg, 0),
                    CommandOutput(255, "", "Exception occurred while executing"),
                ).success,
        ).isFalse()
    }

    @Test
    fun `protected packages`() {
        val list =
            ProtectedPackages.parse(
                """{"packages":["android","com.android.systemui"],"prefixes":["com.samsung.android.knox."],"x":1}""",
            )
        assertThat(list.isProtected("android")).isTrue()
        assertThat(list.isProtected("com.samsung.android.knox.containercore")).isTrue()
        assertThat(list.isProtected("com.example.launcher", dynamic = setOf("com.example.launcher"))).isTrue()
        assertThat(list.isProtected("com.example.chat")).isFalse()
    }

    @Test
    fun `the shipped protected list parses and covers the essentials`() {
        val shipped = ProtectedPackages.parse(File("../app/src/main/assets/protected.json").readText())
        listOf("android", "com.android.systemui", "com.android.phone", "moe.shizuku.privileged.api").forEach {
            assertWithMessage(it).that(shipped.isProtected(it)).isTrue()
        }
    }

    @Test
    fun `scripts device-checks runs the same argv as the app`() {
        val script = File("../scripts/device-checks.sh").readText()
        val target = "app.brokoli5191.picket.testtarget"
        listOf(
            Action.ForceStop(target, 0),
            Action.KillBackground(target, 0),
            Action.StopService(target, 0, "$target/.DummyForegroundService"),
            Action.SetAppOp(target, 0, AppOp.RUN_ANY_IN_BACKGROUND, AppOpMode.IGNORE),
            Action.SetNotificationListener(target, 0, "$target/.DummyNotificationListener", allowed = false),
            Action.SetPermission(target, 0, "android.permission.READ_CALENDAR", granted = false),
            Action.SetIdleExemption(target, 0, exempt = false),
            Action.SetRoleHolder(target, 0, Role.BROWSER, holder = false),
            Action.SetEnabled(target, 0, EnabledState.DISABLED_USER),
            Action.SetEnabled(target, 0, EnabledState.DEFAULT),
            Action.SetDndAccess(target, 0, allowed = false),
        ).forEach { action ->
            val argv = ActionPolicy.command(action).argv
            val words = listOf(argv[0].removePrefix(ShellCommand.BIN)) + argv.drop(1)
            val pattern =
                words.joinToString(" ") { w ->
                    when (w) {
                        target -> "\"\\\$PKG\""
                        "$target/.DummyForegroundService" -> "\"\\\$FGS\""
                        "$target/.DummyNotificationListener" -> "\"\\\$NLS\""
                        "android.permission.READ_CALENDAR" -> "\"\\\$PERM\""
                        "android.app.role.BROWSER" -> "\"\\\$ROLE\""
                        "0" -> "\"\\\$USER_ID\""
                        "-$target" -> "\"-\\\$PKG\""
                        else -> Regex.escape(w)
                    }
                }
            assertWithMessage(words.joinToString(" ")).that(Regex(pattern).containsMatchIn(script)).isTrue()
        }
    }

    @Test
    fun `planner records previous state and builds the inverse`() {
        val planner = ActionPlanner(FixtureExecutor(Fixtures.dir("synthetic/aosp-main")))
        val restrict = planner.plan(Action.SetAppOp(pkg, 0, AppOp.SYSTEM_ALERT_WINDOW, AppOpMode.DENY))
        assertThat(restrict.previousState).isEqualTo("SYSTEM_ALERT_WINDOW: ignore")
        assertThat(restrict.inverse).isEqualTo(Action.SetAppOp(pkg, 0, AppOp.SYSTEM_ALERT_WINDOW, AppOpMode.IGNORE))
        assertThat(restrict.risk).isEqualTo(Risk.LOW)

        val bucket = planner.plan(Action.SetStandbyBucket(pkg, 0, StandbyBucket.RESTRICTED))
        assertThat(bucket.inverse).isEqualTo(Action.SetStandbyBucket(pkg, 0, StandbyBucket.ACTIVE))

        val revoke = planner.plan(Action.SetPermission(pkg, 0, "android.permission.READ_CONTACTS", granted = false))
        assertThat(
            revoke.inverse,
        ).isEqualTo(Action.SetPermission(pkg, 0, "android.permission.READ_CONTACTS", granted = true))

        val idle = planner.plan(Action.SetIdleExemption(pkg, 0, exempt = false))
        assertThat(idle.previousState).isEqualTo("battery optimisation: user")
        assertThat(idle.inverse).isEqualTo(Action.SetIdleExemption(pkg, 0, exempt = true))

        val disable = planner.plan(Action.SetEnabled(pkg, 0, EnabledState.DISABLED_USER))
        assertThat(disable.inverse).isEqualTo(Action.SetEnabled(pkg, 0, EnabledState.DEFAULT))
        assertThat(disable.risk).isEqualTo(Risk.HIGH)

        val dnd = planner.plan(Action.SetDndAccess("com.example.notes", 0, allowed = false))
        assertThat(dnd.inverse).isEqualTo(Action.SetDndAccess("com.example.notes", 0, allowed = true))

        assertThat(planner.plan(Action.ForceStop(pkg, 0)).inverse).isNull()
    }

    @Test
    fun `accessibility plan removes one entry and restores the old value`() {
        val value = "com.example.pass/com.example.pass.AutofillA11y:com.google.android.marvin.talkback/.TalkBackService"
        val executor =
            CommandExecutor { command ->
                if ("enabled_accessibility_services" in
                    command.argv
                ) {
                    CommandOutput(0, "$value\n")
                } else {
                    CommandOutput(0, "")
                }
            }
        val plan =
            ActionPlanner(
                executor,
            ).disableAccessibilityService("com.example.pass", 0, "com.example.pass/.AutofillA11y")
        assertThat(
            (plan.action as Action.SetAccessibilityServices).value,
        ).isEqualTo("com.google.android.marvin.talkback/.TalkBackService")
        assertThat(plan.inverse).isEqualTo(Action.SetAccessibilityServices("com.example.pass", 0, value))
        assertThat(plan.previousState).contains(value)
        assertThrows(IllegalArgumentException::class.java) {
            ActionPlanner(executor).disableAccessibilityService("com.example.pass", 0, "com.other/.X")
        }
    }

    @Test
    fun `role plan falls back to the role dump where get-role-holders is unknown`() {
        val dump = Fixtures.dir("synthetic/aosp-main").resolve("dumpsys_role.txt").readText()
        val executor =
            CommandExecutor { command ->
                when {
                    "get-role-holders" in command.argv -> CommandOutput(0, "Unknown command: get-role-holders\n")
                    command.argv.last() == "role" -> CommandOutput(0, dump)
                    else -> CommandOutput(0, "")
                }
            }
        val plan =
            ActionPlanner(
                executor,
            ).plan(Action.SetRoleHolder("com.android.chrome", 0, Role.BROWSER, holder = false))
        assertThat(plan.previousState).isEqualTo("android.app.role.BROWSER: com.android.chrome")
        assertThat(plan.inverse).isEqualTo(Action.SetRoleHolder("com.android.chrome", 0, Role.BROWSER, holder = true))
    }

    @Test
    fun `execute refuses tampered plans and changed state`() {
        var mode = "SYSTEM_ALERT_WINDOW: allow"
        val ran = mutableListOf<ShellCommand>()
        val executor =
            CommandExecutor { command ->
                if ("set" in command.argv) {
                    ran += command
                    CommandOutput(0, "")
                } else {
                    CommandOutput(0, "$mode\n")
                }
            }
        val planner = ActionPlanner(executor)
        val plan = planner.plan(Action.SetAppOp(pkg, 0, AppOp.SYSTEM_ALERT_WINDOW, AppOpMode.IGNORE))
        assertThat(planner.execute(plan).success).isTrue()
        assertThat(ran).hasSize(1)

        mode = "SYSTEM_ALERT_WINDOW: deny"
        val stale = planner.execute(plan)
        assertThat(stale.success).isFalse()
        assertThat(stale.note).contains("changed")

        val tampered =
            plan.copy(
                command = ShellCommand.of("/system/bin/cmd", "appops", "set", "com.evil", "X", "allow"),
            )
        assertThrows(IllegalArgumentException::class.java) { planner.execute(tampered) }
        assertThat(ran).hasSize(1)
    }

    @Test
    fun `unknown state gives no inverse`() {
        val planner = ActionPlanner { CommandOutput(1, "", "denied") }
        val plan = planner.plan(Action.SetRoleHolder(pkg, 0, Role.SMS, holder = false))
        assertThat(plan.inverse).isNull()
        assertThat(plan.previousState).endsWith("unknown")
        listOf(
            Action.SetNotificationListener(pkg, 0, "$pkg/.L", false),
            Action.SetEnabled(pkg, 0, EnabledState.DISABLED_USER),
            Action.SetDndAccess(pkg, 0, false),
            Action.SetPermission(pkg, 0, "android.permission.CAMERA", false),
            Action.SetIdleExemption(pkg, 0, false),
            Action.SetStandbyBucket(pkg, 0, StandbyBucket.RARE),
        ).forEach { assertThat(planner.plan(it).inverse).isNull() }
    }
}
