package app.brokoli5191.picket.core

import app.brokoli5191.picket.core.proc.ProcFs
import java.io.File

/** Access to `src/test/resources/fixtures/...`. */
object Fixtures {
    fun dir(path: String): File {
        val url =
            checkNotNull(Fixtures::class.java.classLoader.getResource("fixtures/$path")) { "Missing fixture $path" }
        return File(url.toURI())
    }

    fun text(path: String): String = dir(path).readText(Charsets.UTF_8)

    fun bytes(path: String): ByteArray = dir(path).readBytes()
}

/** A [ProcFs] backed by a directory laid out like /proc. */
class DirProcFs(
    private val root: File,
) : ProcFs {
    var reads = mutableListOf<String>()

    override fun pids(): IntArray =
        root
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { it.name.toIntOrNull() }
            .sorted()
            .toIntArray()

    override fun read(path: String): ByteArray? {
        require(path.startsWith("/proc/")) { path }
        val file = File(root, path.removePrefix("/proc/"))
        if (!file.isFile) return null
        reads += path
        return file.readBytes()
    }
}
