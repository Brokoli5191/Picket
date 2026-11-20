package app.brokoli5191.picket.core.proc

import app.brokoli5191.picket.core.DirProcFs
import app.brokoli5191.picket.core.Fixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class ProcessSnapshotReaderTest {
    private fun reader(fs: ProcFs) =
        ProcessSnapshotReader(fs, clockTicksPerSecond = 100, pageSizeBytes = 4096, elapsedRealtimeMs = { 1_000 })

    @Test
    fun `reads every process with identity and adj`() {
        val fs = DirProcFs(Fixtures.dir("synthetic/linux-proc/sample1"))
        val snapshot = reader(fs).snapshot()

        assertThat(
            snapshot.processes.map { it.pid },
        ).containsExactly(1, 2, 45, 1043, 4321, 12345, 23456, 31337).inOrder()
        assertThat(snapshot.uptimeMs).isEqualTo(35_021_370)
        assertThat(snapshot.cpu.onlineCpus).isEqualTo(8)
        assertThat(snapshot.clockTicksPerSecond).isEqualTo(100)
        assertThat(snapshot.pageSizeBytes).isEqualTo(4096)
        assertThat(snapshot.elapsedRealtimeMs).isEqualTo(1_000)

        val gms = snapshot.processes.single { it.pid == 12345 }
        assertThat(gms.name).isEqualTo("com.google.android.gms.unstable")
        assertThat(gms.comm).isEqualTo("id.gms.unstabl")
        assertThat(gms.uid).isEqualTo(10123)
        assertThat(gms.oomScoreAdj).isEqualTo(905)
        assertThat(gms.cpuTicks).isEqualTo(70211 + 24310)
        assertThat(gms.rssBytes(snapshot.pageSizeBytes)).isEqualTo(46911L * 4096)
        assertThat(gms.uptimeMs(snapshot.uptimeMs, snapshot.clockTicksPerSecond)).isEqualTo(35_021_370L - 8_123_450L)

        val kworker = snapshot.processes.single { it.pid == 45 }
        assertThat(kworker.name).isEqualTo("kworker/u16:0-events_unbound")
        assertThat(kworker.uid).isEqualTo(0)

        val hidden = snapshot.processes.single { it.pid == 31337 }
        assertThat(hidden.uid).isEqualTo(ProcessSnapshotReader.UNKNOWN_UID)
        assertThat(hidden.name).isEqualTo("secretd")
        assertThat(hidden.oomScoreAdj).isNull()
    }

    @Test
    fun `caches identity between samples and refreshes it periodically`() {
        val fs = DirProcFs(Fixtures.dir("synthetic/linux-proc/sample1"))
        val reader = ProcessSnapshotReader(fs, 100, 4096, { 0 }, identityRefreshInterval = 3)

        reader.snapshot()
        val first = reader.lastFilesRead
        fs.reads.clear()
        reader.snapshot()
        assertThat(reader.lastFilesRead).isLessThan(first)
        assertThat(fs.reads.none { it.endsWith("/cmdline") && !it.contains("31337") }).isTrue()

        fs.reads.clear()
        reader.snapshot()
        reader.snapshot()
        assertThat(fs.reads.any { it == "/proc/12345/cmdline" }).isTrue()
    }

    @Test
    fun `a recycled pid gets a fresh identity`() {
        val root = Files.createTempDirectory("proc").toFile()
        Fixtures.dir("synthetic/linux-proc/sample1").copyRecursively(root)
        val fs = DirProcFs(root)
        val reader = reader(fs)
        assertThat(
            reader
                .snapshot()
                .processes
                .single { it.pid == 12345 }
                .uid,
        ).isEqualTo(10123)

        val stat = root.resolve("12345/stat")
        stat.writeText(stat.readText().replace(" 812345 ", " 999999 "))
        root.resolve("12345/status").writeText("Uid:\t10200\t10200\t10200\t10200\n")
        root.resolve("12345/cmdline").writeText("com.example.other\u0000")
        val again = reader.snapshot().processes.single { it.pid == 12345 }
        assertThat(again.uid).isEqualTo(10200)
        assertThat(again.name).isEqualTo("com.example.other")

        root.resolve("4321").deleteRecursively()
        assertThat(reader.snapshot().processes.none { it.pid == 4321 }).isTrue()
        root.deleteRecursively()
    }

    @Test
    fun `fails loudly when global files are unreadable`() {
        val empty = Files.createTempDirectory("proc").toFile()
        val fs = DirProcFs(empty)
        assertThrows(IllegalStateException::class.java) { reader(fs).snapshot() }
        empty.resolve("stat").writeText("cpu  1 2 3 4 5 6 7 8 0 0\n")
        assertThrows(IllegalStateException::class.java) { reader(fs).snapshot() }
        empty.deleteRecursively()
    }
}
