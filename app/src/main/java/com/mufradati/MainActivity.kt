package com.mufradati

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

val LocalArabic = staticCompositionLocalOf { false }

@Composable fun tr(en: String, ar: String) = if (LocalArabic.current) ar else en

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            Mufradati(vm)
        }
    }
}

@Composable
fun Mufradati(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val words by vm.words.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speech = remember { Speech(context) }
    DisposableEffect(Unit) { onDispose { speech.close() } }
    val dark = settings.theme == "DARK" || (settings.theme == "SYSTEM" && isSystemInDarkTheme())
    SideEffect {
        (context as? ComponentActivity)?.let { a ->
            androidx.core.view.WindowCompat.getInsetsController(a.window, a.window.decorView)
                .apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
        }
    }
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFF60D5C3),
                secondary = Color(0xFF92CAD5),
                background = Color(0xFF0B1825),
                surface = Color(0xFF122334),
            )
        else
            lightColorScheme(
                primary = Color(0xFF087F82),
                secondary = Color(0xFF244D6C),
                background = Color(0xFFF4F7F8),
                surface = Color.White,
            )
    CompositionLocalProvider(
        LocalArabic provides settings.arabic,
        LocalLayoutDirection provides
            if (settings.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        ) {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var detail by rememberSaveable { mutableStateOf<Long?>(null) }
            var edit by rememberSaveable { mutableStateOf<Long?>(null) }
            val snackbar = remember { SnackbarHostState() }
            val message by vm.message.collectAsStateWithLifecycle()
            LaunchedEffect(message) {
                if (message.isNotEmpty()) {
                    snackbar.showSnackbar(message)
                    vm.message.value = ""
                }
            }
            fun say(text: String) {
                speech.speak(text, settings) {
                    vm.notify(
                        if (settings.arabic) "ثبّت صوتاً إنجليزياً دون اتصال من إعدادات الهاتف."
                        else "Install an offline English voice in Android TTS settings."
                    )
                }
            }
            val session = vm.study
            BackHandler(enabled = session != null || detail != null || edit != null) {
                when {
                    edit != null -> edit = null
                    detail != null -> detail = null
                    else -> vm.closeStudy()
                }
            }
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (session == null && edit == null && detail == null) {
                        val labels =
                            listOf(
                                tr("Home", "الرئيسية"),
                                tr("Vocabulary", "مفرداتي"),
                                tr("Practice", "تدريب"),
                                tr("Statistics", "إحصائيات"),
                                tr("Settings", "الإعدادات"),
                            )
                        val icons =
                            listOf(
                                Icons.Default.Home,
                                Icons.AutoMirrored.Filled.MenuBook,
                                Icons.Default.School,
                                Icons.Default.BarChart,
                                Icons.Default.Settings,
                            )
                        NavigationBar {
                            labels.forEachIndexed { i, label ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(icons[i], label) },
                                    label = { Text(label, maxLines = 1) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Surface(
                    Modifier.fillMaxSize().padding(padding).imePadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (!loaded)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    else if (loadError)
                        Page(tr("Could not load your notebook", "تعذر تحميل دفتر المفردات")) {
                            Text(
                                tr(
                                    "Your saved data has not been deleted. Try again or restart the app.",
                                    "لم تُحذف بياناتك. حاول مرة أخرى أو أعد تشغيل التطبيق.",
                                )
                            )
                            Action(tr("Try again", "حاول مرة أخرى"), vm::retryLoad)
                        }
                    else
                        when {
                            session != null -> StudyScreen(vm, session, settings, speech)
                            edit != null ->
                                WordEditor(
                                    words.firstOrNull { it.id == edit },
                                    { edit = null },
                                    { vm.save(it) { edit = null } },
                                )
                            detail != null ->
                                words
                                    .firstOrNull { it.id == detail }
                                    ?.let { w ->
                                        WordDetails(
                                            w,
                                            history.filter { it.wordId == w.id },
                                            { detail = null },
                                            { edit = w.id },
                                            { vm.delete(w) { detail = null } },
                                            { vm.favorite(w.id) },
                                            ::say,
                                        )
                                    }
                            tab == 0 ->
                                Home(
                                    words,
                                    history,
                                    settings,
                                    { vm.start("BASIC", "ALL", 4) },
                                    { vm.start("BASIC", "DUE", 4) },
                                    { vm.start(it, "ALL", 4) },
                                    { edit = 0L },
                                    vm::samples,
                                )
                            tab == 1 ->
                                VocabularyScreen(
                                    words,
                                    settings,
                                    { vm.settings(settings.copy(sort = it)) },
                                    { detail = it },
                                    { edit = 0L },
                                )
                            tab == 2 -> PracticeSelection(vm, settings)
                            tab == 3 -> Statistics(words, history, settings)
                            else -> SettingsScreen(vm, settings, speech)
                        }
                }
            }
        }
    }
}

@Composable
fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun Action(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) {
        Text(label)
    }
}

@Composable
fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun modes() =
    listOf(
        "BASIC" to tr("Basic Review", "مراجعة البطاقات"),
        "QUIZ" to tr("Multiple Answers", "اختيار من متعدد"),
        "MATCH" to tr("Match Cards", "مطابقة البطاقات"),
        "BOARD" to tr("Whiteboard Review", "مراجعة بالكتابة"),
    )

@Composable
fun sortChoices() =
    listOf(
        "DEFAULT" to tr("Default order", "الترتيب الأصلي"),
        "NEWEST" to tr("Newest first", "الأحدث أولاً"),
        "OLDEST" to tr("Oldest first", "الأقدم أولاً"),
        "AZ" to tr("A to Z", "أبجدي تصاعدي"),
        "ZA" to tr("Z to A", "أبجدي تنازلي"),
    )

@Composable
fun Choice(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    change: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ open = true }, Modifier.fillMaxWidth()) {
                Text(options.firstOrNull { it.first == value }?.second ?: value)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { (k, t) ->
                    DropdownMenuItem(
                        text = { Text(t) },
                        onClick = {
                            change(k)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun Home(
    words: List<Word>,
    history: List<Review>,
    s: Settings,
    study: () -> Unit,
    due: () -> Unit,
    mode: (String) -> Unit,
    add: () -> Unit,
    samples: () -> Unit,
) {
    val today = LocalDate.now()
    val count = history.count { epochDay(it.at) == today }
    val known = words.count { it.status == "KNOWN" }
    Page(tr("Welcome to Mufradati", "مرحباً بك في مفرداتي")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                null,
                Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text("Mufradati · مفرداتي", style = MaterialTheme.typography.titleLarge)
                Text(
                    tr("Your Personal Vocabulary Notebook", "دفتر مفرداتك الشخصي"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Panel {
            Text(
                tr("A little practice, every day", "قليل من التدريب كل يوم"),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(tr("$count / ${s.goal} daily reviews", "$count / ${s.goal} مراجعات اليوم"))
            LinearProgressIndicator(
                progress = { (count.toFloat() / s.goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                tr(
                    "${streak(history.map{epochDay(it.at)}.toSet(),today)} day streak",
                    "سلسلة ${streak(history.map{epochDay(it.at)}.toSet(),today)} أيام",
                )
            )
            Action(tr("Continue Learning", "متابعة التعلم"), study)
            OutlinedButton(due, Modifier.fillMaxWidth()) {
                Text(
                    tr(
                        "Review Due Words (${words.count{it.due<=System.currentTimeMillis()}})",
                        "مراجعة الكلمات المستحقة (${words.count{it.due<=System.currentTimeMillis()}})",
                    )
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(words.size.toString(), tr("Saved", "محفوظة"))
            Metric(known.toString(), tr("Learned", "متقنة"))
            Metric((words.size - known).toString(), tr("Learning", "قيد التعلم"))
        }
        if (words.isEmpty())
            Panel {
                Text(
                    tr(
                        "Add your first word or try 12 sample words.",
                        "أضف كلمتك الأولى أو جرّب ١٢ كلمة نموذجية.",
                    )
                )
                Action(tr("Add your first word", "أضف كلمتك الأولى"), add)
                OutlinedButton(samples) { Text(tr("Add sample vocabulary", "إضافة كلمات نموذجية")) }
            }
        Text(tr("Practice your way", "تدرّب بطريقتك"), style = MaterialTheme.typography.titleLarge)
        modes().forEach { (key, label) ->
            OutlinedButton({ mode(key) }, Modifier.fillMaxWidth()) { Text(label) }
        }
        if (words.isNotEmpty()) {
            val w = words[(today.toEpochDay() % words.size).toInt()]
            Panel {
                Text(tr("Today's word · from your notebook", "كلمة اليوم · من دفترك"))
                Text(w.word, style = MaterialTheme.typography.headlineSmall)
                Text(w.meaning)
            }
        }
    }
}

@Composable
fun VocabularyScreen(
    words: List<Word>,
    s: Settings,
    sort: (String) -> Unit,
    open: (Long) -> Unit,
    add: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(tr("My Vocabulary", "مفرداتي"), style = MaterialTheme.typography.headlineSmall)
            IconButton(add) { Icon(Icons.Default.Add, tr("Add word", "إضافة كلمة")) }
        }
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Search word or meaning", "ابحث بالكلمة أو المعنى")) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Choice(tr("Sort", "الترتيب"), s.sort, sortChoices(), sort) }
            Box(Modifier.weight(1f)) {
                Choice(
                    tr("Category", "الفئة"),
                    category,
                    listOf("" to tr("All", "الكل")) +
                        words
                            .map { it.category }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                            .map { it to it },
                    { category = it },
                )
            }
        }
        FilterChip(
            favorites,
            { favorites = !favorites },
            label = { Text(tr("Favorites only", "المفضلة فقط")) },
        )
        val filtered = sortedWords(searchWords(words, query, category, favorites), s.sort)
        if (filtered.isEmpty())
            Text(
                tr(
                    "No words here. Add a word or change your filters.",
                    "لا توجد كلمات. أضف كلمة أو غيّر عوامل التصفية.",
                )
            )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(filtered, key = { it.id }) { w ->
                Card(onClick = { open(w.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.word, style = MaterialTheme.typography.titleMedium)
                            Text(w.meaning)
                            Text(statusLabel(w.status), style = MaterialTheme.typography.labelSmall)
                        }
                        if (w.favorite) Icon(Icons.Default.Star, tr("Favorite", "مفضلة"))
                    }
                }
            }
        }
    }
}

@Composable
fun statusLabel(status: String) =
    when (status) {
        "KNOWN" -> tr("Known", "متقنة")
        "DIFFICULT" -> tr("Difficult", "صعبة")
        "LEARNING" -> tr("Learning", "قيد التعلم")
        else -> tr("New", "جديدة")
    }

@Composable
fun gradeLabel(grade: Int) =
    when (grade) {
        2 -> tr("Know it", "أعرفها")
        1 -> tr("Almost", "تقريباً")
        else -> tr("Don't know / Difficult", "لا أعرف / صعبة")
    }

@Composable
fun WordEditor(original: Word?, onCancel: () -> Unit, onSave: (Word) -> Unit) {
    var word by rememberSaveable(original?.id) { mutableStateOf(original?.word.orEmpty()) }
    var meaning by rememberSaveable(original?.id) { mutableStateOf(original?.meaning.orEmpty()) }
    var definition by rememberSaveable { mutableStateOf(original?.definition.orEmpty()) }
    var example by rememberSaveable { mutableStateOf(original?.example.orEmpty()) }
    var synonyms by rememberSaveable { mutableStateOf(original?.synonyms.orEmpty()) }
    var part by rememberSaveable { mutableStateOf(original?.part.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(original?.notes.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(original?.category.orEmpty()) }
    Page(
        if (original == null) tr("Add a word", "إضافة كلمة") else tr("Edit word", "تعديل الكلمة")
    ) {
        OutlinedTextField(
            word,
            { word = it.take(200) },
            Modifier.fillMaxWidth(),
            label = { Text(tr("English word or phrase *", "الكلمة أو العبارة الإنجليزية *")) },
        )
        OutlinedTextField(
            meaning,
            { meaning = it.take(2000) },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Meaning *", "المعنى *")) },
        )
        OutlinedTextField(
            definition,
            { definition = it },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Definition", "التعريف")) },
        )
        OutlinedTextField(
            example,
            { example = it },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Example sentence", "جملة مثال")) },
        )
        OutlinedTextField(
            synonyms,
            { synonyms = it },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Synonyms", "المرادفات")) },
        )
        OutlinedTextField(
            part,
            { part = it },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Part of speech", "نوع الكلمة")) },
        )
        OutlinedTextField(
            category,
            { category = it },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Category / tag", "الفئة / الوسم")) },
        )
        OutlinedTextField(
            notes,
            { notes = it },
            Modifier.fillMaxWidth(),
            label = { Text(tr("Notes", "ملاحظات")) },
        )
        Action(
            tr("Save word", "حفظ الكلمة"),
            {
                onSave(
                    (original ?: Word(word = word, meaning = meaning)).copy(
                        word = word,
                        meaning = meaning,
                        definition = definition,
                        example = example,
                        synonyms = synonyms,
                        part = part,
                        category = category,
                        notes = notes,
                    )
                )
            },
            word.isNotBlank() && meaning.isNotBlank(),
        )
        OutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text(tr("Cancel", "إلغاء")) }
    }
}

@Composable
fun WordBack(w: Word) {
    listOf(
            tr("Definition", "التعريف") to w.definition,
            tr("Example", "المثال") to w.example,
            tr("Synonyms", "المرادفات") to w.synonyms,
            tr("Part of speech", "نوع الكلمة") to w.part,
            tr("Notes", "ملاحظات") to w.notes,
            tr("Category", "الفئة") to w.category,
        )
        .filter { it.second.isNotBlank() }
        .forEach { (label, value) ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(value)
        }
}

@Composable
fun WordDetails(
    w: Word,
    history: List<Review>,
    back: () -> Unit,
    edit: () -> Unit,
    delete: () -> Unit,
    favorite: () -> Unit,
    say: (String) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Page(w.word) {
        TextButton(back) { Text(tr("Back", "رجوع")) }
        Panel {
            Text(w.meaning, style = MaterialTheme.typography.headlineSmall)
            WordBack(w)
            Row {
                IconButton({ say(w.word) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, tr("Pronounce word", "نطق الكلمة"))
                }
                IconButton(favorite) {
                    Icon(
                        if (w.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        tr("Toggle favorite", "تبديل المفضلة"),
                    )
                }
            }
            if (w.example.isNotBlank())
                OutlinedButton({ say(w.example) }) { Text(tr("Read example", "قراءة المثال")) }
            Text(tr("Added: ", "أضيفت: ") + epochDay(w.added))
            Text(statusLabel(w.status))
            Text(
                tr("Next review: ", "المراجعة التالية: ") +
                    if (w.due == 0L) tr("Now", "الآن") else epochDay(w.due).toString()
            )
        }
        Action(tr("Edit", "تعديل"), edit)
        OutlinedButton({ confirm = true }) { Text(tr("Delete word", "حذف الكلمة")) }
        Text(tr("Review history", "سجل المراجعات"), style = MaterialTheme.typography.titleLarge)
        if (history.isEmpty()) Text(tr("Not reviewed yet", "لم تتم مراجعتها بعد"))
        history.take(100).forEach { r ->
            Text(
                "${epochDay(r.at)} · ${gradeLabel(r.grade)} · ${modes().firstOrNull{it.first==r.mode}?.second.orEmpty()}"
            )
        }
    }
    if (confirm)
        Confirm(
            tr("Delete this word and its review history?", "حذف الكلمة وسجل مراجعاتها؟"),
            { confirm = false },
            {
                confirm = false
                delete()
            },
        )
}

@Composable
fun Confirm(text: String, cancel: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(tr("Please confirm", "تأكيد")) },
        text = { Text(text) },
        confirmButton = { TextButton(confirm) { Text(tr("Confirm", "تأكيد")) } },
        dismissButton = { TextButton(cancel) { Text(tr("Cancel", "إلغاء")) } },
    )
}
