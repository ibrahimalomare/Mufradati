package com.mufradati

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, change)
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, s: Settings, speech: Speech) {
    val context = LocalContext.current
    var reset by remember { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf("JSON") }
    val importing =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.import(it) }
        }
    val jsonExport =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let { vm.export(it, false) }
        }
    val csvExport =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri
            ->
            uri?.let { vm.export(it, true) }
        }
    val voiceError =
        tr(
            "Install English voice data in your phone's TTS settings.",
            "ثبّت بيانات الصوت الإنجليزي من إعدادات الهاتف.",
        )
    Page(tr("Settings", "الإعدادات")) {
        Panel {
            Choice(
                tr("Interface language", "لغة الواجهة"),
                if (s.arabic) "AR" else "EN",
                listOf("EN" to "English", "AR" to "العربية"),
                { vm.settings(s.copy(arabic = it == "AR")) },
            )
            Choice(
                tr("Appearance", "المظهر"),
                s.theme,
                listOf(
                    "SYSTEM" to tr("System", "النظام"),
                    "LIGHT" to tr("Light", "فاتح"),
                    "DARK" to tr("Dark", "داكن"),
                ),
                { vm.settings(s.copy(theme = it)) },
            )
            Text(tr("Daily goal: ${s.goal} reviews", "الهدف اليومي: ${s.goal} مراجعات"))
            Slider(
                s.goal.toFloat(),
                { vm.settings(s.copy(goal = it.toInt().coerceIn(5, 100))) },
                valueRange = 5f..100f,
                steps = 18,
            )
            Choice(
                tr("Default sorting", "الترتيب الافتراضي"),
                s.sort,
                sortChoices(),
                { vm.settings(s.copy(sort = it)) },
            )
            Toggle(
                tr("Reverse quiz / writing direction", "عكس اتجاه الاختبار / الكتابة"),
                s.reverse,
            ) {
                vm.settings(s.copy(reverse = it))
            }
            Toggle(tr("Flashcard flip animation", "حركة قلب البطاقة"), s.animation) {
                vm.settings(s.copy(animation = it))
            }
        }
        Panel {
            Text(
                tr("English pronunciation", "النطق الإنجليزي"),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                tr(
                    "Uses installed offline Android TTS voices. Voice availability and quality depend on your engine and downloaded English language data.",
                    "يستخدم أصوات أندرويد المثبتة دون اتصال. يعتمد توفر الصوت وجودته على المحرك وبيانات اللغة الإنجليزية المثبتة.",
                )
            )
            if (speech.voices.isNotEmpty())
                Choice(
                    tr("English voice", "الصوت الإنجليزي"),
                    s.voice,
                    listOf("" to tr("Device default", "الافتراضي")) +
                        speech.voices.map { it.name to "${it.locale.displayName} · ${it.name}" },
                    { vm.settings(s.copy(voice = it)) },
                )
            else
                Text(
                    tr(
                        "No offline English voice ready. Install voice data, then reopen Mufradati.",
                        "لا يوجد صوت إنجليزي جاهز دون اتصال. ثبّت بيانات الصوت ثم أعد فتح مفرداتي.",
                    )
                )
            Text(
                tr("Speech rate: ${"%.2f".format(s.rate)}", "سرعة النطق: ${"%.2f".format(s.rate)}")
            )
            Slider(s.rate, { vm.settings(s.copy(rate = it)) }, valueRange = 0.5f..1.5f)
            Text(tr("Pitch: ${"%.2f".format(s.pitch)}", "طبقة الصوت: ${"%.2f".format(s.pitch)}"))
            Slider(s.pitch, { vm.settings(s.copy(pitch = it)) }, valueRange = 0.5f..1.5f)
            OutlinedButton({ speech.speak("Welcome to Mufradati", s) { vm.notify(voiceError) } }) {
                Text(tr("Test pronunciation", "اختبار النطق"))
            }
            OutlinedButton({
                try {
                    context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (_: Exception) {
                    vm.notify(voiceError)
                }
            }) {
                Text(tr("Install voice data", "تثبيت بيانات الصوت"))
            }
        }
        Panel {
            Text(tr("Your data", "بياناتك"), style = MaterialTheme.typography.titleLarge)
            Text(
                tr(
                    "Import adds new vocabulary and skips duplicate words. JSON and CSV export vocabulary only; review history stays on this device.",
                    "يضيف الاستيراد كلمات جديدة ويتجاهل المكررة. تصدير JSON وCSV يشمل الكلمات فقط؛ يبقى سجل المراجعات على الجهاز.",
                )
            )
            Choice(
                tr("Export format", "صيغة التصدير"),
                format,
                listOf("JSON" to "JSON", "CSV" to "CSV"),
                { format = it },
            )
            Action(
                tr("Export vocabulary", "تصدير المفردات"),
                {
                    if (format == "JSON") jsonExport.launch("mufradati.json")
                    else csvExport.launch("mufradati.csv")
                },
            )
            OutlinedButton(
                {
                    importing.launch(
                        arrayOf("application/json", "text/*", "application/octet-stream")
                    )
                },
                Modifier.fillMaxWidth(),
            ) {
                Text(tr("Import JSON / CSV", "استيراد JSON / CSV"))
            }
            OutlinedButton({ vm.samples() }, Modifier.fillMaxWidth()) {
                Text(tr("Add sample vocabulary", "إضافة كلمات نموذجية"))
            }
            TextButton({ reset = true }) {
                Text(
                    tr("Reset learning progress", "إعادة ضبط التقدم"),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Panel {
            Text(tr("About Mufradati", "حول مفرداتي"), style = MaterialTheme.typography.titleLarge)
            Text("Mufradati · مفرداتي 1.0.0")
            Text(
                tr(
                    "Your Personal Vocabulary Notebook. Offline learning. No accounts, ads, or analytics.",
                    "دفتر مفرداتك الشخصي. تعلم دون اتصال، دون حسابات أو إعلانات أو تحليلات.",
                )
            )
            Text(
                tr(
                    "Schedule: incorrect = 1 day; almost = half the last interval (minimum 1 day); known = 1, 3, 7 days, then double up to 180. Difficult due words appear first.",
                    "جدول المراجعة: خطأ = يوم؛ تقريباً = نصف المدة السابقة (يوم على الأقل)؛ أعرفها = ١ ثم ٣ ثم ٧ أيام، ثم تتضاعف حتى ١٨٠ يوماً. تظهر الكلمات الصعبة المستحقة أولاً.",
                )
            )
        }
    }
    if (reset)
        Confirm(
            tr(
                "Reset all review history, ratings, streaks and statistics? Vocabulary will be kept.",
                "حذف جميع التقييمات وسجل المراجعات والإحصائيات؟ ستبقى المفردات.",
            ),
            { reset = false },
            {
                vm.reset()
                reset = false
            },
        )
}

@Composable
fun Statistics(words: List<Word>, history: List<Review>, s: Settings) {
    val today = LocalDate.now()
    val days = history.groupBy { epochDay(it.at) }
    val quiz = history.filter { it.mode == "QUIZ" }
    val accuracy = if (quiz.isEmpty()) 0 else quiz.count { it.grade == 2 } * 100 / quiz.size
    Page(tr("Your progress", "تقدمك")) {
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric(words.size.toString(), tr("Words", "كلمات"))
                Metric(words.count { it.status == "KNOWN" }.toString(), tr("Known", "متقنة"))
                Metric(words.count { it.status == "DIFFICULT" }.toString(), tr("Difficult", "صعبة"))
            }
            Text(
                tr(
                    "${history.size} reviews · ${history.map{it.wordId}.distinct().size} distinct words reviewed",
                    "${history.size} مراجعة · ${history.map{it.wordId}.distinct().size} كلمات مختلفة",
                )
            )
            Text(
                tr(
                    "${history.map{it.session}.distinct().size} study sessions",
                    "${history.map{it.session}.distinct().size} جلسات دراسة",
                )
            )
            Text(
                tr(
                    "${history.sumOf{it.seconds}/60} minutes of recorded study time",
                    "${history.sumOf{it.seconds}/60} دقيقة دراسة مسجلة",
                )
            )
            Text(
                tr(
                    "Quiz accuracy: $accuracy% (${quiz.count{it.grade==2}} / ${quiz.size})",
                    "دقة الاختبار: $accuracy% (${quiz.count{it.grade==2}} / ${quiz.size})",
                )
            )
            Text(
                tr("${streak(days.keys,today)} day streak", "سلسلة ${streak(days.keys,today)} أيام")
            )
            Text(
                tr(
                    "${days.count{it.value.size>=s.goal}} days meeting your current daily goal",
                    "${days.count{it.value.size>=s.goal}} أيام حققت هدفك اليومي الحالي",
                )
            )
        }
        Panel {
            Text(
                tr("Last 7 days · reviews", "آخر ٧ أيام · المراجعات"),
                style = MaterialTheme.typography.titleLarge,
            )
            val max = (0L..6L).maxOf { days[today.minusDays(it)]?.size ?: 0 }.coerceAtLeast(s.goal)
            (6L downTo 0L).forEach { offset ->
                val day = today.minusDays(offset)
                val n = days[day]?.size ?: 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(day.format(DateTimeFormatter.ofPattern("MM/dd")), Modifier.width(52.dp))
                    LinearProgressIndicator(
                        progress = { n.toFloat() / max },
                        modifier = Modifier.weight(1f),
                    )
                    Text(n.toString())
                }
            }
        }
        if (history.isEmpty())
            Text(
                tr(
                    "No progress recorded yet. Start your first review.",
                    "لم يُسجل أي تقدم بعد. ابدأ مراجعتك الأولى.",
                )
            )
        Text(
            tr(
                "Time is recorded when you submit an answer, capped at ten minutes per answer. Sessions without answers are not counted.",
                "يُسجل الوقت عند تقديم الإجابة بحد أقصى عشر دقائق لكل إجابة. لا تُحتسب الجلسات دون إجابات.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
