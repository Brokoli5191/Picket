package app.brokoli5191.picket.core.ipc

import app.brokoli5191.picket.core.model.CpuTimes
import app.brokoli5191.picket.core.model.ProcessSample
import app.brokoli5191.picket.core.model.ProcessSnapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class PayloadCodecTest {
    private val snapshot =
        ProcessSnapshot(
            elapsedRealtimeMs = 5,
            uptimeMs = 6,
            cpu = CpuTimes(100, 50, 8),
            clockTicksPerSecond = 100,
            pageSizeBytes = 4096,
            processes =
                List(700) { i ->
                    ProcessSample(
                        i,
                        1,
                        10_000 + i,
                        "com.example.app$i:service",
                        "app$i",
                        'S',
                        i * 10L,
                        i * 3L,
                        i * 7L,
                        1000,
                        4,
                        900,
                    )
                },
        )

    @Test
    fun `round trips a realistic process snapshot well below the binder limit`() {
        val reply = Reply.ok(snapshot, CallCost(wallMicros = 1, cpuMicros = 2, filesRead = 3, commandsRun = 0))
        val bytes = PayloadCodec.encodeReply(ProcessSnapshot.serializer(), reply)
        assertThat(bytes.size).isLessThan(100 * 1024)
        val decoded = PayloadCodec.decodeReply(ProcessSnapshot.serializer(), bytes)
        assertThat(decoded).isEqualTo(reply)
    }

    @Test
    fun `errors travel as typed replies`() {
        val reply = Reply.failed<String>(ServiceError(ServiceError.Kind.INVALID_ARGUMENT, "bad"))
        val decoded =
            PayloadCodec.decodeReply(
                String.serializer(),
                PayloadCodec.encodeReply(String.serializer(), reply),
            )
        assertThat(decoded.error).isEqualTo(ServiceError(ServiceError.Kind.INVALID_ARGUMENT, "bad"))
        assertThat(decoded.value).isNull()
    }

    @Test
    fun `a reply has exactly one of value and error`() {
        assertThrows(IllegalArgumentException::class.java) { Reply<String>() }
        assertThrows(IllegalArgumentException::class.java) {
            Reply("x", ServiceError(ServiceError.Kind.INTERNAL, "y"))
        }
    }

    @Test
    fun `refuses payloads that would not fit a binder transaction`() {
        val random = java.util.Random(1)
        val huge = buildString { repeat(2_000_000) { append(('a'.code + random.nextInt(26)).toChar()) } }
        assertThrows(IllegalStateException::class.java) { PayloadCodec.encode(String.serializer(), huge) }
    }

    @Test
    fun `refuses decompression bombs`() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gz ->
            val zeros = ByteArray(1024 * 1024)
            repeat(PayloadCodec.MAX_DECOMPRESSED_BYTES / zeros.size + 1) { gz.write(zeros) }
        }
        assertThrows(IllegalStateException::class.java) { PayloadCodec.decode(String.serializer(), out.toByteArray()) }
    }

    @Test
    fun `unknown keys are rejected`() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write("""{"value":"x","surprise":1}""".toByteArray()) }
        assertThrows(Exception::class.java) { PayloadCodec.decodeReply(String.serializer(), out.toByteArray()) }
    }
}
