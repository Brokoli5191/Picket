package app.brokoli5191.picket.privileged.service

import app.brokoli5191.picket.core.proc.ProcFs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** Reads /proc with plain file I/O, reusing one buffer (calls are serialised by the service). */
internal class AndroidProcFs : ProcFs {
    private val buffer = ByteArray(BUFFER_SIZE)

    override fun pids(): IntArray {
        val names = File("/proc").list() ?: return IntArray(0)
        val pids = IntArray(names.size)
        var count = 0
        for (name in names) {
            if (name.isNotEmpty() && name[0].isDigit()) {
                name.toIntOrNull()?.let { pids[count++] = it }
            }
        }
        return pids.copyOf(count).apply { sort() }
    }

    override fun read(path: String): ByteArray? =
        try {
            FileInputStream(path).use(::readFully)
        } catch (_: IOException) {
            null // the process exited between listing and reading, or the file is not readable
        } catch (_: SecurityException) {
            null
        }

    private fun readFully(input: FileInputStream): ByteArray {
        var length = 0
        while (length < buffer.size) {
            val read = input.read(buffer, length, buffer.size - length)
            if (read < 0) return buffer.copyOf(length)
            length += read
        }
        // Larger than the buffer (rare for /proc): continue into a growing stream.
        val out = ByteArrayOutputStream(buffer.size * 2)
        out.write(buffer, 0, length)
        input.copyTo(out)
        return out.toByteArray()
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
    }
}
