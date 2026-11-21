package app.brokoli5191.picket.data.journal

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** One executed command: what ran, what it replaced, what came back, and how to undo it. */
@Serializable
@Entity(tableName = "journal")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Process session; "undo all from this session" works on it. */
    val sessionId: String,
    val timestampMs: Long,
    val packageName: String,
    /** JSON of the core Action. */
    val actionJson: String,
    /** Exactly what ran, one element per argument. */
    val argv: List<String>,
    /** [argv] shell-quoted for display. */
    val command: String,
    val risk: String,
    val previousState: String?,
    /** JSON of the inverse Action, null where none exists. */
    val inverseJson: String?,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val note: String?,
    /** Set on entries that undo another entry. */
    val undoOfId: Long? = null,
    /** Set on entries that have been undone. */
    val undoneById: Long? = null,
)

@Dao
interface JournalDao {
    @Insert
    suspend fun insert(entry: JournalEntry): Long

    @Query("SELECT * FROM journal ORDER BY timestampMs DESC, id DESC")
    fun observeAll(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal WHERE id = :id")
    suspend fun byId(id: Long): JournalEntry?

    @Query("UPDATE journal SET undoneById = :undoId WHERE id = :id")
    suspend fun markUndone(
        id: Long,
        undoId: Long,
    )

    @Query(
        "SELECT * FROM journal WHERE sessionId = :sessionId AND success = 1 AND undoOfId IS NULL " +
            "AND undoneById IS NULL AND inverseJson IS NOT NULL ORDER BY timestampMs DESC, id DESC",
    )
    suspend fun undoableInSession(sessionId: String): List<JournalEntry>

    @Query("SELECT * FROM journal ORDER BY timestampMs, id")
    suspend fun everything(): List<JournalEntry>
}

/** Stores argv as a JSON array so no separator can be confused with an argument. */
class JournalConverters {
    @TypeConverter
    fun fromArgv(argv: List<String>): String = Json.encodeToString(ListSerializer(String.serializer()), argv)

    @TypeConverter
    fun toArgv(json: String): List<String> = Json.decodeFromString(ListSerializer(String.serializer()), json)
}

@Database(entities = [JournalEntry::class], version = 1, exportSchema = true)
@TypeConverters(JournalConverters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journal(): JournalDao

    companion object {
        fun create(context: Context): JournalDatabase =
            Room.databaseBuilder(context, JournalDatabase::class.java, "journal.db").build()
    }
}
