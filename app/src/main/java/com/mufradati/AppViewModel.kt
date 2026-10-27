package com.mufradati

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = VocabularyRepository(VocabularyDatabase.get(app))
    private val preferences = PreferencesRepository(app)
    val loaded = MutableStateFlow(false)
    val loadError = MutableStateFlow(false)
    val message = MutableStateFlow("")
    private val reload = MutableStateFlow(0)
    val words =
        reload
            .flatMapLatest {
                repository.words
                    .onEach {
                        loaded.value = true
                        loadError.value = false
                    }
                    .catch {
                        loadError.value = true
                        loaded.value = true
                        emit(emptyList())
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history =
        reload
            .flatMapLatest { repository.history.catch { emit(emptyList()) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings =
        preferences.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            Settings(),
        )
    var study by mutableStateOf<StudySession?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    fun retryLoad() {
        loaded.value = false
        loadError.value = false
        reload.value++
    }

    private fun job(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notify(
                    if (settings.value.arabic)
                        "تعذر تنفيذ العملية. تحقق من المدخلات أو تكرار الكلمة."
                    else "Could not complete operation. Check input or duplicate words."
                )
            }
        }
    }

    fun notify(text: String) {
        message.value = text
    }

    fun save(word: Word, done: () -> Unit) = job {
        repository.save(word)
        done()
    }

    fun delete(word: Word, done: () -> Unit) = job {
        repository.delete(word)
        done()
    }

    fun favorite(id: Long) = job { repository.favorite(id) }

    fun settings(s: Settings) = job { preferences.save(s) }

    fun samples() = job {
        val n = repository.importWords(sampleWords)
        notify(if (settings.value.arabic) "تمت إضافة $n كلمة" else "Added $n words")
    }

    fun reset() = job { repository.reset() }

    fun start(mode: String, scope: String, pairs: Int) {
        if (busy) return
        val selected =
            selectWords(words.value, scope, settings.value.sort, System.currentTimeMillis())
        if (selected.isEmpty()) {
            notify(
                if (settings.value.arabic) "لا توجد كلمات في هذه المجموعة"
                else "No words in this selection"
            )
            return
        }
        if (
            mode == "QUIZ" &&
                selected.any { Quiz.question(it, words.value, settings.value.reverse) == null }
        ) {
            notify(
                if (settings.value.arabic) "أضف كلمات أكثر لتوفير أربع إجابات غير ملتبسة"
                else "Add more vocabulary: each question needs four unambiguous answers"
            )
            return
        }
        if (mode == "MATCH" && selected.distinctBy { it.meaning.trim().lowercase() }.size < 2) {
            notify(
                if (settings.value.arabic) "تحتاج إلى معنيين مختلفين على الأقل"
                else "At least two distinct meanings are needed"
            )
            return
        }
        study = StudySession(mode, selected, words.value, settings.value.reverse, pairs)
    }

    fun closeStudy() {
        study = null
    }

    fun shuffle() {
        if (busy) return
        study = study?.let { it.copy(cards = it.cards.shuffled(), index = 0) }
    }

    fun move(delta: Int) {
        if (busy) return
        study = study?.let { it.copy(index = (it.index + delta).coerceIn(0, it.cards.lastIndex)) }
    }

    fun finish() {
        if (busy) return
        study = study?.copy(finished = true)
    }

    fun rate(grade: Int, seconds: Int, advance: Boolean = true) {
        val s = study ?: return
        if (busy || s.cards[s.index].id in s.results) return
        busy = true
        viewModelScope.launch {
            try {
                val w = s.cards[s.index]
                repository.review(w.id, grade, s.mode, s.id, seconds)
                val results = s.results + (w.id to grade)
                if (study?.id == s.id)
                    study =
                        s.copy(
                            results = results,
                            index =
                                if (advance && s.index < s.cards.lastIndex) s.index + 1
                                else s.index,
                            finished = advance && s.index == s.cards.lastIndex,
                        )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notify(if (settings.value.arabic) "تعذر حفظ المراجعة" else "Could not save review")
            } finally {
                busy = false
            }
        }
    }

    fun match(left: Long, right: Long, seconds: Int) {
        val s = study ?: return
        if (busy) return
        val board = s.board.choose(left, right)
        if (board == s.board) return
        busy = true
        viewModelScope.launch {
            try {
                repository.review(left, if (left == right) 2 else 0, "MATCH", s.id, seconds)
                if (study?.id == s.id)
                    study =
                        s.copy(
                            board = board,
                            results = s.results + (left to if (left == right) 2 else 0),
                            finished = board.complete,
                        )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notify(if (settings.value.arabic) "تعذر حفظ المحاولة" else "Could not save attempt")
            } finally {
                busy = false
            }
        }
    }

    fun export(uri: Uri, csv: Boolean) = job {
        withContext(Dispatchers.IO) {
            val text = if (csv) Exchange.csv(words.value) else Exchange.json(words.value)
            getApplication<Application>()
                .contentResolver
                .openOutputStream(uri, "wt")
                ?.bufferedWriter()
                ?.use { it.write(text) } ?: error("Output unavailable")
        }
        notify(if (settings.value.arabic) "تم التصدير" else "Export complete")
    }

    fun import(uri: Uri) = job {
        val imported =
            withContext(Dispatchers.IO) {
                val text =
                    getApplication<Application>()
                        .contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { r ->
                            val b = CharArray(2_000_001)
                            var n = 0
                            while (n < b.size) {
                                val k = r.read(b, n, b.size - n)
                                if (k < 0) break
                                n += k
                            }
                            require(n <= 2_000_000)
                            String(b, 0, n)
                        } ?: error("Input unavailable")
                Exchange.parse(text)
            }
        val n = repository.importWords(imported)
        notify(
            if (settings.value.arabic) "تم استيراد $n كلمة جديدة"
            else "Imported $n new words; duplicates skipped"
        )
    }
}

data class StudySession(
    val mode: String,
    val cards: List<Word>,
    val pool: List<Word>,
    val reverse: Boolean,
    val pairs: Int,
    val id: String = UUID.randomUUID().toString(),
    val index: Int = 0,
    val results: Map<Long, Int> = emptyMap(),
    val finished: Boolean = false,
    val board: MatchBoard = MatchBoard.create(cards, pairs),
)
