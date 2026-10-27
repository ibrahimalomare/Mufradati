package com.mufradati

import java.time.LocalDate
import kotlin.random.Random

object Schedule {
    fun next(previous: Int, grade: Int): Int {
        require(grade in 0..2)
        return when (grade) {
            0 -> 1
            1 -> (previous / 2).coerceAtLeast(1)
            else ->
                when {
                    previous < 1 -> 1
                    previous < 3 -> 3
                    previous < 7 -> 7
                    else -> (previous * 2).coerceAtMost(180)
                }
        }
    }
}

fun sortedWords(words: List<Word>, sort: String): List<Word> =
    when (sort) {
        "NEWEST" -> words.sortedByDescending { it.added }
        "OLDEST" -> words.sortedBy { it.added }
        "AZ" -> words.sortedBy { it.word.lowercase() }
        "ZA" -> words.sortedByDescending { it.word.lowercase() }
        else -> words.sortedBy { it.id }
    }

fun selectWords(words: List<Word>, scope: String, sort: String, now: Long): List<Word> {
    val chosen =
        words.filter {
            when (scope) {
                "FAVORITES" -> it.favorite
                "NEW" -> it.reviews == 0
                "DUE" -> it.due <= now
                else -> true
            }
        }
    return if (scope == "DUE")
        chosen.sortedWith(compareBy<Word> { it.status != "DIFFICULT" }.thenBy { it.due })
    else sortedWords(chosen, sort)
}

fun searchWords(words: List<Word>, query: String, category: String, favorites: Boolean) =
    words.filter {
        (it.word.contains(query, true) || it.meaning.contains(query, true)) &&
            (category.isEmpty() || it.category == category) &&
            (!favorites || it.favorite)
    }

data class Question(
    val word: Word,
    val prompt: String,
    val correct: String,
    val options: List<String>,
)

object Quiz {
    fun answer(w: Word, reverse: Boolean) = (if (reverse) w.word else w.meaning).trim()

    fun question(
        w: Word,
        pool: List<Word>,
        reverse: Boolean,
        random: Random = Random.Default,
    ): Question? {
        val correct = answer(w, reverse)
        val other =
            pool
                .filter { !reverse || !it.meaning.trim().equals(w.meaning.trim(), true) }
                .map { answer(it, reverse) }
                .filter { !it.equals(correct, true) }
                .distinctBy { it.lowercase() }
                .shuffled(random)
                .take(3)
        if (other.size < 3) return null
        return Question(
            w,
            if (reverse) w.meaning else w.word,
            correct,
            (other + correct).shuffled(random),
        )
    }
}

data class MatchBoard(
    val left: List<Word>,
    val right: List<Word>,
    val matched: Set<Long> = emptySet(),
    val moves: Int = 0,
) {
    val complete
        get() = matched.size == left.size

    fun choose(leftId: Long, rightId: Long): MatchBoard {
        if (
            left.none { it.id == leftId } ||
                right.none { it.id == rightId } ||
                leftId in matched ||
                rightId in matched
        )
            return this
        return copy(
            matched = if (leftId == rightId) matched + leftId else matched,
            moves = moves + 1,
        )
    }

    companion object {
        fun create(words: List<Word>, count: Int, random: Random = Random.Default): MatchBoard {
            val chosen =
                words.distinctBy { it.meaning.trim().lowercase() }.shuffled(random).take(count)
            return MatchBoard(chosen.shuffled(random), chosen.shuffled(random))
        }
    }
}

fun streak(days: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in days) today else today.minusDays(1)
    var count = 0
    while (day in days) {
        count++
        day = day.minusDays(1)
    }
    return count
}

val sampleWords =
    listOf(
        Word(
            word = "achieve",
            meaning = "يحقق",
            definition = "To succeed in reaching a goal.",
            example = "You can achieve your goal with practice.",
            part = "verb",
            category = "Everyday",
        ),
        Word(
            word = "curious",
            meaning = "فضولي / محب للاستطلاع",
            definition = "Wanting to learn or know more.",
            example = "She is curious about other cultures.",
            part = "adjective",
            category = "People",
        ),
        Word(
            word = "resilient",
            meaning = "قادر على التعافي",
            definition = "Able to recover after difficulty.",
            example = "The team remained resilient.",
            part = "adjective",
            category = "People",
        ),
        Word(
            word = "opportunity",
            meaning = "فرصة",
            example = "This is an opportunity to learn.",
            part = "noun",
            category = "Everyday",
        ),
        Word(
            word = "improve",
            meaning = "يحسن",
            example = "Reading will improve your vocabulary.",
            part = "verb",
            category = "Learning",
        ),
        Word(
            word = "confident",
            meaning = "واثق",
            example = "I feel confident about the exam.",
            part = "adjective",
            category = "People",
        ),
        Word(
            word = "discover",
            meaning = "يكتشف",
            example = "We discover something new each day.",
            part = "verb",
            category = "Learning",
        ),
        Word(
            word = "effort",
            meaning = "جهد",
            example = "Learning takes time and effort.",
            part = "noun",
            category = "Learning",
        ),
        Word(
            word = "grateful",
            meaning = "ممتن",
            example = "I am grateful for your help.",
            part = "adjective",
            category = "People",
        ),
        Word(
            word = "journey",
            meaning = "رحلة",
            example = "Enjoy your learning journey.",
            part = "noun",
            category = "Everyday",
        ),
        Word(
            word = "remember",
            meaning = "يتذكر",
            example = "I remember this word.",
            part = "verb",
            category = "Learning",
        ),
        Word(
            word = "thoughtful",
            meaning = "مراعٍ للآخرين",
            example = "That was a thoughtful gift.",
            part = "adjective",
            category = "People",
        ),
    )
