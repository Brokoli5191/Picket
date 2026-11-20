package app.brokoli5191.picket.core.validation

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test

class ValidatorsTest {
    @Test
    fun `accepts real package names`() {
        listOf(
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.sec.android.app.launcher",
            "app.brokoli5191.picket",
            "moe.shizuku.privileged.api",
            "org.mozilla.firefox_beta",
            "a.b1_2",
        ).forEach { assertWithMessage(it).that(Validators.isValidPackageName(it)).isTrue() }
    }

    @Test
    fun `rejects injection attempts in package names`() {
        listOf(
            "",
            "com.foo;reboot",
            "com.foo && reboot",
            "com.foo bar",
            "com.foo\$(reboot)",
            "com.foo`id`",
            "com.foo|sh",
            "com.foo>x",
            "../../data",
            "com.foo/../../x",
            "com..foo",
            ".com.foo",
            "com.foo.",
            "1com.foo",
            "com.1foo",
            "com.foo\n",
            "com.foo\u0000",
            "com.fóo",
            "-com.foo",
            "--user",
            "com.foo'",
            "com.foo\"",
            "a".repeat(Validators.MAX_PACKAGE_NAME_LENGTH + 1),
        ).forEach { assertWithMessage(it).that(Validators.isValidPackageName(it)).isFalse() }
    }

    @Test
    fun `accepts components in both flattened forms`() {
        listOf(
            "com.example/.MyService",
            "com.example/com.example.MyService",
            "com.example/com.other.Listener\$Inner",
            "com.example/_Internal",
        ).forEach { assertWithMessage(it).that(Validators.isValidComponent(it)).isTrue() }
    }

    @Test
    fun `rejects malformed or hostile components`() {
        listOf(
            "",
            "/",
            "com.example",
            "com.example/",
            "/.MyService",
            "com.example/.My Service",
            "com.example/.MyService;reboot",
            "com.example/.MyService/../x",
            "com.example//.MyService",
            "com..example/.MyService",
            "com.example/..MyService",
            "com.example/.My\$(id)",
            "com.example/.MyService\n",
            "com.example/" + "A".repeat(600),
        ).forEach { assertWithMessage(it).that(Validators.isValidComponent(it)).isFalse() }
    }

    @Test
    fun `permission names need a namespace`() {
        assertThat(Validators.isValidPermissionName("android.permission.CAMERA")).isTrue()
        assertThat(Validators.isValidPermissionName("com.example.permission.READ_FOO")).isTrue()
        assertThat(Validators.isValidPermissionName("CAMERA")).isFalse()
        assertThat(Validators.isValidPermissionName("android.permission.CAMERA;id")).isFalse()
        assertThat(Validators.isValidPermissionName("android.permission.CAMERA android.permission.X")).isFalse()
    }

    @Test
    fun `user ids are bounded`() {
        assertThat(Validators.isValidUserId(0)).isTrue()
        assertThat(Validators.isValidUserId(150)).isTrue()
        assertThat(Validators.isValidUserId(Validators.MAX_USER_ID)).isTrue()
        assertThat(Validators.isValidUserId(-1)).isFalse()
        assertThat(Validators.isValidUserId(Validators.MAX_USER_ID + 1)).isFalse()
    }

    @Test
    fun `require functions return input or throw with a sanitised message`() {
        assertThat(Validators.requirePackageName("com.example")).isEqualTo("com.example")
        assertThat(Validators.requireComponent("com.example/.S")).isEqualTo("com.example/.S")
        assertThat(Validators.requirePermissionName("android.permission.CAMERA")).isEqualTo("android.permission.CAMERA")
        assertThat(Validators.requireUserId(10)).isEqualTo(10)

        val e = assertThrows(IllegalArgumentException::class.java) { Validators.requirePackageName("x;\nreboot") }
        assertThat(e).hasMessageThat().isEqualTo("Invalid package name: \"x;\\u000areboot\"")
        assertThrows(IllegalArgumentException::class.java) { Validators.requireComponent("nope") }
        assertThrows(IllegalArgumentException::class.java) { Validators.requirePermissionName("nope") }
        assertThrows(IllegalArgumentException::class.java) { Validators.requireUserId(-5) }
    }

    @Test
    fun `printable clips long input`() {
        with(Validators) {
            assertThat("b".repeat(100).printable()).isEqualTo("\"" + "b".repeat(80) + "...\"")
        }
    }
}
