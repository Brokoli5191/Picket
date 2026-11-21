package app.brokoli5191.picket.core.ipc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Wire format between the privileged service and the app: JSON of a [Reply], gzip-compressed.
 *
 * Both sides are compiled from the same sources into the same APK, so the format only has to be
 * stable within one build; [PROTOCOL_VERSION] still guards against a service process that outlives
 * an app update.
 */
object PayloadCodec {
    /** Bump whenever a payload type or an AIDL method changes shape. */
    const val PROTOCOL_VERSION = 1

    /**
     * Binder transactions share a 1 MB buffer per process. Payloads are compressed, so this cap on the
     * compressed size leaves plenty of headroom; anything larger is a bug, not a big device.
     */
    const val MAX_COMPRESSED_BYTES = 512 * 1024

    /** Guards decompression against a corrupted or hostile stream. */
    const val MAX_DECOMPRESSED_BYTES = 32 * 1024 * 1024

    val json: Json =
        Json {
            ignoreUnknownKeys = false
            explicitNulls = false
            encodeDefaults = true
        }

    fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray {
        val raw = json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(raw.size / 4 + 64)
        GZIPOutputStream(out).use { it.write(raw) }
        val bytes = out.toByteArray()
        check(bytes.size <= MAX_COMPRESSED_BYTES) {
            "Payload too large for a binder transaction: ${bytes.size} bytes compressed (${raw.size} raw)"
        }
        return bytes
    }

    fun <T> decode(
        serializer: KSerializer<T>,
        bytes: ByteArray,
    ): T {
        val raw = inflate(bytes)
        return json.decodeFromString(serializer, raw.toString(Charsets.UTF_8))
    }

    fun <T> encodeReply(
        serializer: KSerializer<T>,
        reply: Reply<T>,
    ): ByteArray = encode(Reply.serializer(serializer), reply)

    fun <T> decodeReply(
        serializer: KSerializer<T>,
        bytes: ByteArray,
    ): Reply<T> = decode(Reply.serializer(serializer), bytes)

    private fun inflate(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size * 4)
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                check(total <= MAX_DECOMPRESSED_BYTES) { "Payload exceeds $MAX_DECOMPRESSED_BYTES bytes" }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}

/** Result envelope of every typed operation. Exactly one of [value] and [error] is set. */
@Serializable
data class Reply<T>(
    val value: T? = null,
    val error: ServiceError? = null,
    val cost: CallCost = CallCost(),
) {
    init {
        require((value == null) != (error == null)) { "Reply needs exactly one of value or error" }
    }

    companion object {
        fun <T> ok(
            value: T,
            cost: CallCost = CallCost(),
        ): Reply<T> = Reply(value = value, cost = cost)

        fun <T> failed(
            error: ServiceError,
            cost: CallCost = CallCost(),
        ): Reply<T> = Reply(error = error, cost = cost)
    }
}

@Serializable
data class ServiceError(
    val kind: Kind,
    val message: String,
) {
    @Serializable
    enum class Kind {
        /** An argument failed validation inside the service. */
        INVALID_ARGUMENT,

        /** A command exited non-zero or timed out. */
        COMMAND_FAILED,

        /** Output did not match what the parser expects (new Android version or OEM change). */
        PARSE_FAILED,

        /** The system refused (SecurityException, SELinux denial, missing permission for shell). */
        NOT_PERMITTED,

        /** The operation is not available on this API level or build. */
        UNSUPPORTED,
        INTERNAL,
    }
}

/** What a call cost inside the service; surfaced in the debug overlay. */
@Serializable
data class CallCost(
    val wallMicros: Long = 0,
    val cpuMicros: Long = 0,
    val filesRead: Int = 0,
    val commandsRun: Int = 0,
)
