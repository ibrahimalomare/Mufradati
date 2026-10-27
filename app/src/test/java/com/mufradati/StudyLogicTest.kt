package com.mufradati

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class StudyLogicTest {
    private fun word(id: Long, meaning: String = "meaning $id") =
        Word(id = id, word = "word $id", meaning = meaning, added = id * 100)

    @Test
    fun scheduleBoundsAndProgression() {
        assertEquals(1, Schedule.next(0, 2))
        assertEquals(3, Schedule.next(1, 2))
        assertEquals(7, Schedule.next(3, 2))
        assertEquals(14, Schedule.next(7, 2))
        assertEquals(180, Schedule.next(180, 2))
        assertEquals(1, Schedule.next(100, 0))
        assertEquals(7, Schedule.next(14, 1))
    }

    @Test
    fun quizHasExactlyFourUniqueChoicesAndCorrectAnswer() {
        val words = (1L..8L).map { word(it) } + word(9, "MEANING 1")
        repeat(50) { seed ->
            val q = Quiz.question(words[0], words, false, Random(seed))!!
            assertEquals(4, q.options.size)
            assertEquals(4, q.options.map { it.lowercase() }.distinct().size)
            assertTrue(q.correct in q.options)
        }
    }

    @Test
    fun reverseQuizExcludesAmbiguousSynonyms() {
        val w = word(1, "same meaning")
        val q =
            Quiz.question(w, listOf(w, word(2, "same meaning"), word(3), word(4), word(5)), true)!!
        assertFalse("word 2" in q.options)
    }

    @Test
    fun insufficientQuizAndReverse() {
        assertNull(Quiz.question(word(1), listOf(word(1), word(2)), false))
        val pool = (1L..4L).map { word(it) }
        val q = Quiz.question(pool[0], pool, true)!!
        assertEquals("meaning 1", q.prompt)
        assertEquals("word 1", q.correct)
    }

    @Test
    fun sortingAndFiltering() {
        val words = listOf(word(2), word(1).copy(favorite = true, category = "Study"), word(3))
        assertEquals(listOf(3L, 2L, 1L), sortedWords(words, "NEWEST").map { it.id })
        assertEquals(listOf(1L, 2L, 3L), sortedWords(words, "DEFAULT").map { it.id })
        assertEquals(1, searchWords(words, "MEANING", "Study", true).size)
        assertEquals(1, selectWords(words, "FAVORITES", "AZ", 100).size)
    }

    @Test
    fun duePrioritizesDifficultyAndExcludesFuture() {
        val words =
            listOf(
                word(1).copy(due = 1),
                word(2).copy(status = "DIFFICULT", due = 2),
                word(3).copy(due = 500),
            )
        assertEquals(listOf(2L, 1L), selectWords(words, "DUE", "AZ", 100).map { it.id })
    }

    @Test
    fun matchingIgnoresAlreadyMatchedAndInvalidIds() {
        val board = MatchBoard.create((1L..4L).map { word(it) }, 4, Random(42))
        val miss = board.choose(1, 2)
        assertEquals(1, miss.moves)
        assertEquals(0, miss.matched.size)
        val hit = miss.choose(1, 1)
        assertEquals(1, hit.matched.size)
        assertEquals(hit, hit.choose(1, 1))
        assertEquals(hit, hit.choose(99, 99))
        assertTrue((2L..4L).fold(hit) { b, id -> b.choose(id, id) }.complete)
    }

    @Test
    fun streakAllowsYesterdayButNotGap() {
        val t = LocalDate.of(2026, 10, 27)
        assertEquals(2, streak(setOf(t.minusDays(1), t.minusDays(2)), t))
        assertEquals(0, streak(setOf(t.minusDays(2)), t))
    }

    @Test
    fun csvEscapesMultilineArabicAndQuotes() {
        val w = word(1).copy(meaning = "معنى, عربي", notes = "line 1\n\"line 2\"")
        val rows = Exchange.parseCsv(Exchange.csv(listOf(w)))
        assertEquals(w.meaning, rows[1][1])
        assertEquals(w.notes, rows[1][6])
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCsvRejectsUnclosedQuote() {
        Exchange.parseCsv("word,meaning\n\"unfinished")
    }
}
