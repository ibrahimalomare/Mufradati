package com.mufradati

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.*

@Entity(tableName = "words", indices = [Index(value = ["word"], unique = true)])
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val meaning: String,
    val definition: String = "",
    val example: String = "",
    val synonyms: String = "",
    val part: String = "",
    val notes: String = "",
    val category: String = "",
    val favorite: Boolean = false,
    val added: Long = System.currentTimeMillis(),
    val status: String = "NEW",
    val lastReview: Long = 0,
    val due: Long = 0,
    val interval: Int = 0,
    val reviews: Int = 0,
)

@Entity(
    tableName = "reviews",
    foreignKeys =
        [
            ForeignKey(
                entity = Word::class,
                parentColumns = ["id"],
                childColumns = ["wordId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("wordId")],
)
data class Review(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val at: Long,
    val grade: Int,
    val mode: String,
    val session: String,
    val seconds: Int,
)

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM words ORDER BY id") fun words(): Flow<List<Word>>

    @Query("SELECT * FROM reviews ORDER BY at DESC") fun history(): Flow<List<Review>>

    @Query("SELECT * FROM words WHERE id = :id") suspend fun get(id: Long): Word?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(word: Word): Long

    @Update suspend fun update(word: Word)

    @Delete suspend fun delete(word: Word)

    @Insert suspend fun log(review: Review)

    @Query("DELETE FROM reviews") suspend fun clearHistory()

    @Query("UPDATE words SET status='NEW',lastReview=0,due=0,interval=0,reviews=0")
    suspend fun resetWords()
}

@Database(entities = [Word::class, Review::class], version = 1, exportSchema = true)
abstract class VocabularyDatabase : RoomDatabase() {
    abstract fun dao(): VocabularyDao

    companion object {
        @Volatile private var instance: VocabularyDatabase? = null

        fun get(context: Context): VocabularyDatabase =
            instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                VocabularyDatabase::class.java,
                                "mufradati.db",
                            )
                            .build()
                            .also { instance = it }
                }
    }
}

class VocabularyRepository(private val db: VocabularyDatabase) {
    val words = db.dao().words()
    val history = db.dao().history()

    suspend fun save(word: Word) {
        require(word.word.isNotBlank() && word.meaning.isNotBlank())
        val clean = word.copy(word = word.word.trim(), meaning = word.meaning.trim())
        if (clean.id == 0L) db.dao().insert(clean) else db.dao().update(clean)
    }

    suspend fun delete(word: Word) = db.dao().delete(word)

    suspend fun favorite(id: Long) =
        db.withTransaction {
            db.dao().get(id)?.let { db.dao().update(it.copy(favorite = !it.favorite)) }
        }

    suspend fun review(id: Long, grade: Int, mode: String, session: String, seconds: Int) =
        db.withTransaction {
            val word = db.dao().get(id) ?: return@withTransaction
            val now = System.currentTimeMillis()
            val days = Schedule.next(word.interval, grade)
            db.dao()
                .update(
                    word.copy(
                        status =
                            if (grade == 2) "KNOWN"
                            else if (grade == 0) "DIFFICULT" else "LEARNING",
                        lastReview = now,
                        due = now + days * 86_400_000L,
                        interval = days,
                        reviews = word.reviews + 1,
                    )
                )
            db.dao()
                .log(
                    Review(
                        wordId = id,
                        at = now,
                        grade = grade,
                        mode = mode,
                        session = session,
                        seconds = seconds.coerceIn(0, 600),
                    )
                )
        }

    suspend fun reset() =
        db.withTransaction {
            db.dao().clearHistory()
            db.dao().resetWords()
        }

    suspend fun importWords(incoming: List<Word>): Int =
        db.withTransaction {
            val existing = db.dao().words().first().map { it.word.lowercase() }.toMutableSet()
            var count = 0
            incoming.forEach {
                if (existing.add(it.word.trim().lowercase())) {
                    save(
                        it.copy(
                            id = 0,
                            status = "NEW",
                            reviews = 0,
                            due = 0,
                            interval = 0,
                            lastReview = 0,
                        )
                    )
                    count++
                }
            }
            count
        }
}

val Context.preferences by preferencesDataStore("preferences")

data class Settings(
    val arabic: Boolean = false,
    val theme: String = "SYSTEM",
    val goal: Int = 10,
    val sort: String = "AZ",
    val reverse: Boolean = false,
    val voice: String = "",
    val rate: Float = 0.85f,
    val pitch: Float = 1f,
    val animation: Boolean = true,
)

class PreferencesRepository(private val context: Context) {
    val settings =
        context.preferences.data
            .catch { emit(emptyPreferences()) }
            .map { p ->
                Settings(
                    p[booleanPreferencesKey("arabic")] ?: false,
                    p[stringPreferencesKey("theme")] ?: "SYSTEM",
                    p[intPreferencesKey("goal")] ?: 10,
                    p[stringPreferencesKey("sort")] ?: "AZ",
                    p[booleanPreferencesKey("reverse")] ?: false,
                    p[stringPreferencesKey("voice")] ?: "",
                    p[floatPreferencesKey("rate")] ?: 0.85f,
                    p[floatPreferencesKey("pitch")] ?: 1f,
                    p[booleanPreferencesKey("animation")] ?: true,
                )
            }

    suspend fun save(s: Settings) {
        context.preferences.edit { p ->
            p[booleanPreferencesKey("arabic")] = s.arabic
            p[stringPreferencesKey("theme")] = s.theme
            p[intPreferencesKey("goal")] = s.goal
            p[stringPreferencesKey("sort")] = s.sort
            p[booleanPreferencesKey("reverse")] = s.reverse
            p[stringPreferencesKey("voice")] = s.voice
            p[floatPreferencesKey("rate")] = s.rate
            p[floatPreferencesKey("pitch")] = s.pitch
            p[booleanPreferencesKey("animation")] = s.animation
        }
    }
}

fun epochDay(ms: Long): LocalDate =
    java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
