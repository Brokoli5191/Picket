package app.brokoli5191.picket.data.journal

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class JournalDaoTest {
    private lateinit var db: JournalDatabase
    private lateinit var dao: JournalDao

    @Before
    fun open() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), JournalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.journal()
    }

    @After
    fun close() = db.close()

    private fun entry(
        time: Long,
        session: String = "s1",
        success: Boolean = true,
        inverse: String? = "{}",
        undoOf: Long? = null,
        argv: List<String> = listOf("/system/bin/cmd", "activity", "force-stop", "--user", "0", "com.example.chat"),
    ) = JournalEntry(
        sessionId = session,
        timestampMs = time,
        packageName = "com.example.chat",
        actionJson = "{}",
        argv = argv,
        command = argv.joinToString(" "),
        risk = "LOW",
        previousState = null,
        inverseJson = inverse,
        success = success,
        exitCode = 0,
        stdout = "",
        stderr = "",
        note = null,
        undoOfId = undoOf,
    )

    @Test
    fun undoableInSessionSkipsFailedIrreversibleUndoneAndForeignEntries() =
        runBlocking {
            val older = dao.insert(entry(1_000))
            val newer = dao.insert(entry(2_000))
            dao.insert(entry(3_000, success = false))
            dao.insert(entry(4_000, inverse = null))
            dao.insert(entry(5_000, session = "other"))
            val undone = dao.insert(entry(6_000))
            val undo = dao.insert(entry(7_000, undoOf = undone))
            dao.markUndone(undone, undo)

            assertThat(dao.undoableInSession("s1").map { it.id }).containsExactly(newer, older).inOrder()
            assertThat(dao.byId(undone)?.undoneById).isEqualTo(undo)
        }

    @Test
    fun argvSurvivesArgumentsWithSpacesAndQuotes() =
        runBlocking {
            val argv = listOf("/system/bin/cmd", "settings", "put", "secure", "key", "a b", "it's", "x,y:z")
            val id = dao.insert(entry(1_000, argv = argv))
            assertThat(dao.byId(id)?.argv).isEqualTo(argv)
        }

    @Test
    fun observeAllIsNewestFirstAndEverythingOldestFirst() =
        runBlocking {
            val a = dao.insert(entry(1_000))
            val b = dao.insert(entry(2_000))
            assertThat(dao.observeAll().first().map { it.id }).containsExactly(b, a).inOrder()
            assertThat(dao.everything().map { it.id }).containsExactly(a, b).inOrder()
        }
}
