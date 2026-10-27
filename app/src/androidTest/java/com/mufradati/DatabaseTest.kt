package com.mufradati

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var context: Context
    private lateinit var db: VocabularyDatabase

    private fun open() =
        Room.databaseBuilder(context, VocabularyDatabase::class.java, "test-persistence.db").build()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test-persistence.db")
        db = open()
    }

    @After
    fun cleanup() {
        db.close()
        context.deleteDatabase("test-persistence.db")
    }

    @Test
    fun crudReviewResetAndReopen() = runBlocking {
        var repo = VocabularyRepository(db)
        repo.save(Word(word = "retain", meaning = "يحتفظ"))
        val original = db.dao().words().first().single()
        repo.save(original.copy(meaning = "يتذكر", category = "Learning"))
        repo.review(original.id, 2, "BASIC", "test-session", 12)
        db.close()
        db = open()
        repo = VocabularyRepository(db)
        val stored = db.dao().get(original.id)!!
        assertEquals("يتذكر", stored.meaning)
        assertEquals(1, stored.reviews)
        assertEquals("KNOWN", stored.status)
        assertEquals(1, db.dao().history().first().size)
        repo.reset()
        assertEquals(0, db.dao().get(original.id)!!.reviews)
        assertTrue(db.dao().history().first().isEmpty())
        repo.delete(stored)
        assertTrue(db.dao().words().first().isEmpty())
    }

    @Test
    fun importIsAtomicAndSkipsCaseInsensitiveDuplicates() = runBlocking {
        val repo = VocabularyRepository(db)
        assertEquals(
            1,
            repo.importWords(
                listOf(
                    Word(word = "Hello", meaning = "مرحباً"),
                    Word(word = "hello", meaning = "أهلاً"),
                )
            ),
        )
        try {
            repo.importWords(
                listOf(Word(word = "valid", meaning = "صحيح"), Word(word = "", meaning = "invalid"))
            )
            fail("Expected invalid input")
        } catch (_: IllegalArgumentException) {}
        assertEquals(1, db.dao().words().first().size)
    }

    @Test
    fun jsonAndCsvRoundTrip() {
        val w =
            Word(word = "hello", meaning = "مرحباً", notes = "a, b\n\"quoted\"", favorite = true)
        listOf(Exchange.csv(listOf(w)), Exchange.json(listOf(w))).forEach {
            val r = Exchange.parse(it).single()
            assertEquals(w.word, r.word)
            assertEquals(w.notes, r.notes)
            assertTrue(r.favorite)
        }
    }
}
