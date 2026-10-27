package com.mufradati

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun PracticeSelection(vm: AppViewModel, s: Settings) {
    var scope by rememberSaveable { mutableStateOf("ALL") }
    var pairs by rememberSaveable { mutableStateOf("4") }
    Page(tr("Make it stick", "ثبّت ما تعلمته")) {
        Text(tr("Choose your words and a way to practice.", "اختر الكلمات وطريقة التدريب."))
        Choice(
            tr("Study collection", "مجموعة الدراسة"),
            scope,
            listOf(
                "ALL" to tr("All words", "كل الكلمات"),
                "FAVORITES" to tr("Favorites", "المفضلة"),
                "NEW" to tr("New words", "كلمات جديدة"),
                "DUE" to tr("Due · difficult first", "المستحقة · الصعبة أولاً"),
            ),
            { scope = it },
        )
        Choice(tr("Sort", "الترتيب"), s.sort, sortChoices(), { vm.settings(s.copy(sort = it)) })
        Toggle(tr("Reverse quiz / writing direction", "عكس اتجاه الاختبار / الكتابة"), s.reverse) {
            vm.settings(s.copy(reverse = it))
        }
        Choice(
            tr("Matching pairs", "عدد أزواج المطابقة"),
            pairs,
            listOf("4" to "4", "6" to "6", "8" to "8"),
            { pairs = it },
        )
        modes().forEach { (key, label) ->
            Panel {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Text(
                    when (key) {
                        "BASIC" ->
                            tr("Reveal, recall, and rate your memory.", "اكشف المعنى وقيّم تذكرك.")
                        "QUIZ" ->
                            tr(
                                "One question. Four unique answers.",
                                "سؤال واحد وأربع إجابات مختلفة.",
                            )
                        "MATCH" ->
                            tr(
                                "Find every word–meaning pair.",
                                "اعثر على كل زوج من الكلمة والمعنى.",
                            )
                        else ->
                            tr(
                                "Write by hand, reveal, and assess yourself. No handwriting recognition.",
                                "اكتب بيدك ثم اكشف الإجابة وقيّم نفسك. لا يوجد تعرف آلي على الكتابة.",
                            )
                    }
                )
                Action(tr("Start", "ابدأ"), { vm.start(key, scope, pairs.toInt()) })
            }
        }
    }
}

@Composable
fun StudyScreen(vm: AppViewModel, s: StudySession, settings: Settings, speech: Speech) {
    var seconds by rememberSaveable(s.id) { mutableIntStateOf(0) }
    var lastAnswer by rememberSaveable(s.id) { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(s.id, s.finished) {
        while (!s.finished) {
            delay(1000)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) seconds++
        }
    }
    fun elapsed(): Int {
        val n = seconds - lastAnswer
        lastAnswer = seconds
        return n
    }
    val noVoice =
        tr(
            "Install an offline English voice in Android TTS settings.",
            "ثبّت صوتاً إنجليزياً دون اتصال من إعدادات الهاتف.",
        )
    fun say(text: String) {
        speech.speak(text, settings) { vm.notify(noVoice) }
    }
    if (s.finished) {
        Page(tr("Session complete", "اكتملت الجلسة")) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            val known = s.results.values.count { it == 2 }
            val difficult = s.results.values.count { it == 0 }
            val accuracy =
                if (s.mode == "MATCH") s.board.matched.size * 100 / s.board.moves.coerceAtLeast(1)
                else known * 100 / s.results.size.coerceAtLeast(1)
            Panel {
                Text(tr("${s.results.size} words reviewed", "تمت مراجعة ${s.results.size} كلمة"))
                Text(
                    tr("Known: $known · Difficult: $difficult", "متقنة: $known · صعبة: $difficult")
                )
                Text(tr("Accuracy: $accuracy%", "الدقة: $accuracy%"))
                Text(tr("Active time: $seconds seconds", "الوقت النشط: $seconds ثانية"))
                if (s.mode == "MATCH") Text(tr("${s.board.moves} moves", "${s.board.moves} محاولة"))
            }
            Action(tr("Another round", "جولة أخرى"), { vm.start(s.mode, "ALL", s.pairs) })
            OutlinedButton({ vm.closeStudy() }, Modifier.fillMaxWidth()) { Text(tr("Done", "تم")) }
        }
        return
    }
    Page(modes().first { it.first == s.mode }.second) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton({ confirmExit = true }) { Text(tr("End session", "إنهاء الجلسة")) }
            Text(tr("$seconds sec", "$seconds ث"))
        }
        if (s.mode == "MATCH") MatchGame(s, vm.busy) { a, b -> vm.match(a, b, elapsed()) }
        else {
            val w = s.cards[s.index]
            Text(
                tr(
                    "${s.index+1} of ${s.cards.size} · ${s.results.size} reviewed",
                    "${s.index+1} من ${s.cards.size} · ${s.results.size} مراجعة",
                )
            )
            LinearProgressIndicator(
                progress = { s.results.size.toFloat() / s.cards.size },
                modifier = Modifier.fillMaxWidth(),
            )
            val liveWords by vm.words.collectAsStateWithLifecycle()
            Row {
                IconButton({ say(w.word) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, tr("Pronounce word", "نطق الكلمة"))
                }
                IconButton({ vm.favorite(w.id) }) {
                    Icon(
                        if (liveWords.firstOrNull { it.id == w.id }?.favorite == true)
                            Icons.Default.Star
                        else Icons.Default.StarBorder,
                        tr("Toggle favorite", "تبديل المفضلة"),
                    )
                }
                IconButton({ speech.stop() }) {
                    Icon(Icons.Default.Stop, tr("Stop audio", "إيقاف الصوت"))
                }
            }
            when (s.mode) {
                "QUIZ" ->
                    QuizCard(
                        s,
                        vm.busy,
                        { vm.rate(it, elapsed(), false) },
                        { if (s.index == s.cards.lastIndex) vm.finish() else vm.move(1) },
                    )
                "BOARD" ->
                    WhiteboardCard(w, s.reverse, vm.busy, w.id in s.results) {
                        vm.rate(it, elapsed())
                    }
                else -> {
                    Flashcard(
                        w,
                        settings.animation,
                        vm.busy,
                        w.id in s.results,
                        { vm.rate(it, elapsed()) },
                        { say(w.example) },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton({ vm.move(-1) }, enabled = s.index > 0) {
                            Text(tr("Previous", "السابق"))
                        }
                        OutlinedButton({
                            if (s.index == s.cards.lastIndex) vm.finish() else vm.move(1)
                        }) {
                            Text(tr("Next", "التالي"))
                        }
                    }
                    OutlinedButton({ vm.shuffle() }, Modifier.fillMaxWidth()) {
                        Text(tr("Shuffle cards", "خلط البطاقات"))
                    }
                }
            }
        }
    }
    if (confirmExit)
        Confirm(
            tr(
                "End this session? Saved reviews will be kept.",
                "إنهاء الجلسة؟ ستبقى المراجعات المحفوظة.",
            ),
            { confirmExit = false },
            {
                confirmExit = false
                vm.finish()
            },
        )
}

@Composable
fun Flashcard(
    w: Word,
    animation: Boolean,
    busy: Boolean,
    rated: Boolean,
    rate: (Int) -> Unit,
    sayExample: () -> Unit,
) {
    var back by rememberSaveable(w.id) { mutableStateOf(false) }
    val angle by
        animateFloatAsState(
            if (back) 180f else 0f,
            tween(if (animation) 300 else 0),
            label = "cardFlip",
        )
    Card(
        onClick = { back = !back },
        modifier =
            Modifier.fillMaxWidth().heightIn(min = 240.dp).graphicsLayer {
                rotationY = angle
                cameraDistance = 12 * density
            },
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .graphicsLayer { rotationY = if (angle > 90) 180f else 0f }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (angle <= 90) {
                Text(w.word, style = MaterialTheme.typography.headlineLarge)
                Text(tr("Tap to reveal meaning", "اضغط لإظهار المعنى"))
            } else {
                Text(w.meaning, style = MaterialTheme.typography.headlineSmall)
                WordBack(w)
            }
        }
    }
    if (!back) Action(tr("Show Meaning", "إظهار المعنى"), { back = true })
    if (back) {
        if (w.example.isNotBlank())
            TextButton(sayExample) { Text(tr("Read example aloud", "قراءة المثال بصوت عالٍ")) }
        if (rated)
            Text(
                tr(
                    "Review already saved. Use Next to continue.",
                    "تم حفظ المراجعة. اضغط التالي للمتابعة.",
                )
            )
        else RatingButtons(!busy, rate)
    }
}

@Composable
fun RatingButtons(enabled: Boolean, rate: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (2 downTo 0).forEach { g ->
            OutlinedButton({ rate(g) }, Modifier.fillMaxWidth(), enabled = enabled) {
                Text(gradeLabel(g))
            }
        }
    }
}

@Composable
fun QuizCard(s: StudySession, busy: Boolean, answer: (Int) -> Unit, next: () -> Unit) {
    val w = s.cards[s.index]
    val q = remember(s.id, w.id) { Quiz.question(w, s.pool, s.reverse)!! }
    var selected by rememberSaveable(s.id, w.id) { mutableStateOf<String?>(null) }
    val saved = w.id in s.results
    Panel {
        Text(q.prompt, style = MaterialTheme.typography.headlineMedium)
        q.options.forEach { option ->
            val color =
                if (selected != null && option == q.correct)
                    MaterialTheme.colorScheme.primaryContainer
                else if (selected == option) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surface
            OutlinedButton(
                {
                    selected = option
                    answer(if (option == q.correct) 2 else 0)
                },
                Modifier.fillMaxWidth(),
                enabled = !busy && !saved && selected == null,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = color,
                        disabledContainerColor = color,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Text(option)
            }
        }
        if (selected != null || saved) {
            Text(tr("Correct answer: ", "الإجابة الصحيحة: ") + q.correct)
            if (w.definition.isNotBlank()) Text(w.definition)
            Action(tr("Next", "التالي"), next, saved && !busy)
            if (!saved && !busy)
                TextButton({ answer(if (selected == q.correct) 2 else 0) }) {
                    Text(tr("Retry saving answer", "إعادة محاولة حفظ الإجابة"))
                }
        }
    }
}

@Composable
fun MatchGame(s: StudySession, busy: Boolean, choose: (Long, Long) -> Unit) {
    var left by rememberSaveable(s.id) { mutableStateOf<Long?>(null) }
    var wrong by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(wrong) {
        if (wrong != null) {
            delay(650)
            wrong = null
        }
    }
    val b = s.board
    Text(
        tr(
            "${b.matched.size} / ${b.left.size} pairs · ${b.moves} moves",
            "${b.matched.size} / ${b.left.size} أزواج · ${b.moves} محاولة",
        )
    )
    Text(tr("Select an English word, then its meaning.", "اختر الكلمة الإنجليزية ثم معناها."))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.left.forEach { w ->
                OutlinedButton(
                    { left = w.id },
                    Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    enabled = w.id !in b.matched && !busy,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor =
                                if (left == w.id) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                ) {
                    Text(if (w.id in b.matched) "✓ ${w.word}" else w.word)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.right.forEach { w ->
                OutlinedButton(
                    {
                        left?.let { a ->
                            if (a != w.id) wrong = w.id
                            choose(a, w.id)
                            left = null
                        }
                    },
                    Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    enabled = left != null && w.id !in b.matched && !busy,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor =
                                if (wrong == w.id) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                ) {
                    Text(if (w.id in b.matched) "✓ ${w.meaning}" else w.meaning)
                }
            }
        }
    }
    if (wrong != null)
        Text(
            tr("Not a match. Try again.", "ليست مطابقة. حاول مرة أخرى."),
            color = MaterialTheme.colorScheme.error,
        )
}

data class InkStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val eraser: Boolean = false,
)

@Composable
fun WhiteboardCard(w: Word, reverse: Boolean, busy: Boolean, rated: Boolean, rate: (Int) -> Unit) {
    var reveal by rememberSaveable(w.id) { mutableStateOf(false) }
    Text(if (reverse) w.meaning else w.word, style = MaterialTheme.typography.headlineMedium)
    Text(tr("Write your answer, then check it yourself.", "اكتب إجابتك ثم تحقق منها بنفسك."))
    key(w.id) { DrawingBoard() }
    Action(tr("Reveal answer", "إظهار الإجابة"), { reveal = true })
    if (reveal) {
        Panel {
            Text(if (reverse) w.word else w.meaning, style = MaterialTheme.typography.titleLarge)
        }
        if (!rated) RatingButtons(!busy, rate)
    }
}

@Composable
fun DrawingBoard() {
    var strokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var ink by remember { mutableStateOf(Color(0xFF10243A)) }
    var thickness by remember { mutableFloatStateOf(5f) }
    var eraser by remember { mutableStateOf(false) }
    val description =
        tr("Handwriting canvas. Use a finger or stylus.", "لوحة كتابة. استخدم إصبعك أو قلمك.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Color(0xFF10243A), Color(0xFF087F82), Color(0xFFC13550), Color(0xFF6654B9))
            .forEachIndexed { i, c ->
                val names =
                    listOf(
                        tr("Navy ink", "حبر كحلي"),
                        tr("Teal ink", "حبر فيروزي"),
                        tr("Red ink", "حبر أحمر"),
                        tr("Purple ink", "حبر بنفسجي"),
                    )
                IconButton(
                    {
                        ink = c
                        eraser = false
                    },
                    Modifier.background(
                        if (ink == c && !eraser) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    ),
                ) {
                    Icon(Icons.Default.Edit, names[i], tint = c)
                }
            }
    }
    Text(tr("Pen width", "سُمك القلم"))
    Slider(thickness, { thickness = it }, valueRange = 2f..14f, steps = 5)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(eraser, { eraser = !eraser }, label = { Text(tr("Eraser", "ممحاة")) })
        TextButton({ strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) {
            Text(tr("Undo", "تراجع"))
        }
        TextButton({
            strokes = emptyList()
            current = emptyList()
        }) {
            Text(tr("Clear", "مسح"))
        }
    }
    Canvas(
        Modifier.fillMaxWidth()
            .height(300.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
            .semantics { contentDescription = description }
            .pointerInput(ink, thickness, eraser) {
                detectDragGestures(
                    onDragStart = { current = listOf(it) },
                    onDragEnd = {
                        if (current.isNotEmpty())
                            strokes = strokes + InkStroke(current, ink, thickness, eraser)
                        current = emptyList()
                    },
                    onDragCancel = { current = emptyList() },
                    onDrag = { change, _ ->
                        change.consume()
                        current = current + change.position
                    },
                )
            }
    ) {
        fun draw(stroke: InkStroke) {
            val c = if (stroke.eraser) Color.White else stroke.color
            val width = if (stroke.eraser) stroke.width * 4 else stroke.width
            if (stroke.points.size == 1) drawCircle(c, width / 2, stroke.points.first())
            stroke.points.zipWithNext().forEach { (a, b) ->
                drawLine(c, a, b, width, StrokeCap.Round)
            }
        }
        strokes.forEach(::draw)
        draw(InkStroke(current, ink, thickness, eraser))
    }
}
