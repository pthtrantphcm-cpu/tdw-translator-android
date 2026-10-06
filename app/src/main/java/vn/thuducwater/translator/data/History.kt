package vn.thuducwater.translator.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** text | voice | conversation | ocr | camera */
    val kind: String,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT 500")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE favorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(item: HistoryEntity): Long

    @Query("UPDATE history SET favorite = CASE WHEN favorite = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("SELECT favorite FROM history WHERE id = :id")
    suspend fun isFavorite(id: Long): Boolean?

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

/** Repository Pattern: ViewModel không gọi DAO trực tiếp. */
class HistoryRepository(private val dao: HistoryDao) {
    val all: Flow<List<HistoryEntity>> = dao.observeAll()
    val favorites: Flow<List<HistoryEntity>> = dao.observeFavorites()

    suspend fun add(kind: String, source: String, translated: String, src: String, tgt: String): Long {
        if (source.isBlank() || translated.isBlank()) return -1
        return dao.insert(HistoryEntity(kind = kind, sourceText = source, translatedText = translated, sourceLang = src, targetLang = tgt))
    }

    suspend fun toggleFavorite(id: Long): Boolean {
        dao.toggleFavorite(id)
        return dao.isFavorite(id) ?: false
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()
}

/** Tùy chọn nhỏ của người dùng (ngôn ngữ đích mặc định, tự đọc kết quả). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tdw_prefs", Context.MODE_PRIVATE)

    var targetLang: String
        get() = sp.getString("target", "vi") ?: "vi"
        set(v) = sp.edit().putString("target", v).apply()

    var autoSpeak: Boolean
        get() = sp.getBoolean("auto_speak", true)
        set(v) = sp.edit().putBoolean("auto_speak", v).apply()
}
