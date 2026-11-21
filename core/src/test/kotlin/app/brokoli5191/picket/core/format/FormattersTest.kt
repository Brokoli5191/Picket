package app.brokoli5191.picket.core.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormattersTest {
    @Test
    fun bytes() {
        assertThat(Formatters.bytes(-5)).isEqualTo("0 B")
        assertThat(Formatters.bytes(512)).isEqualTo("512 B")
        assertThat(Formatters.bytes(4096)).isEqualTo("4 KB")
        assertThat(Formatters.bytes(187_644L * 1024)).isEqualTo("183.2 MB")
        assertThat(Formatters.bytes(3L * 1024 * 1024 * 1024 + 512L * 1024 * 1024)).isEqualTo("3.50 GB")
    }

    @Test
    fun percent() {
        assertThat(Formatters.percent(-1.0)).isEqualTo("0.0 %")
        assertThat(Formatters.percent(3.14159)).isEqualTo("3.1 %")
        assertThat(Formatters.percent(42.6)).isEqualTo("43 %")
        assertThat(Formatters.percent(250.0)).isEqualTo("100 %")
    }

    @Test
    fun duration() {
        assertThat(Formatters.duration(-1)).isEqualTo("0s")
        assertThat(Formatters.duration(45_000)).isEqualTo("45s")
        assertThat(Formatters.duration(723_000)).isEqualTo("12m 3s")
        assertThat(Formatters.duration(3 * 3_600_000L + 7 * 60_000L)).isEqualTo("3h 07m")
        assertThat(Formatters.duration(52 * 3_600_000L)).isEqualTo("2d 4h")
    }
}
