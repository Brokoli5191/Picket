package app.brokoli5191.picket.core.command

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ShellCommandTest {
    @Test
    fun `only whitelisted binaries with absolute paths`() {
        assertThat(ShellCommand.of("/system/bin/am", "force-stop", "com.example").argv).hasSize(3)
        listOf("am", "/system/bin/sh", "/system/bin/toybox", "/data/local/tmp/x", "/system/bin/../bin/sh", "sh")
            .forEach { bin -> assertThrows(IllegalArgumentException::class.java) { ShellCommand.of(bin, "x") } }
    }

    @Test
    fun `rejects empty commands, empty arguments and control characters`() {
        assertThrows(IllegalArgumentException::class.java) { ShellCommand(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.of("/system/bin/am", "") }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.of("/system/bin/am", "a\nb") }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.of("/system/bin/am", "a\u0000") }
        assertThrows(IllegalArgumentException::class.java) { ShellCommand.of("/system/bin/am", "a\u007f") }
    }

    @Test
    fun `display quotes exactly what a shell would need`() {
        assertThat(
            ShellCommand
                .of(
                    "/system/bin/cmd",
                    "appops",
                    "set",
                    "com.example",
                    "RUN_ANY_IN_BACKGROUND",
                    "ignore",
                ).display(),
        ).isEqualTo("/system/bin/cmd appops set com.example RUN_ANY_IN_BACKGROUND ignore")
        assertThat(ShellCommand.of("/system/bin/settings", "put", "secure", "x", "a b;c").display())
            .isEqualTo("/system/bin/settings put secure x 'a b;c'")
        assertThat(ShellCommand.quote("it's")).isEqualTo("'it'\\''s'")
        assertThat(ShellCommand.quote("com.example/.Svc:com.other/.Svc")).isEqualTo("com.example/.Svc:com.other/.Svc")
    }
}
