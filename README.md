# Mufradati · مفرداتي

Your Personal Vocabulary Notebook. Native Kotlin / Jetpack Compose / Material 3 vocabulary learning for Android 8.0+ (API 26). Offline Room storage, DataStore settings, and Android TTS. No registration, ads, analytics or internet permission.

## Build in Android Studio

1. Open this directory, containing `settings.gradle.kts`, in Android Studio with AGP 8.13.2 support.
2. Select JDK 17 as the Gradle JDK. Install SDK Platform 36, Build-Tools 35.0.0 and Platform-Tools in SDK Manager.
3. Let Gradle sync. The first build needs internet to download dependencies; the installed app works offline.
4. Select the app configuration and a phone or emulator, then Run.

Command line:

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
# Device tests require a connected phone or running emulator:
./gradlew connectedDebugAndroidTest
```

Windows: use `.\gradlew.bat` instead of `./gradlew`.
Set JAVA_HOME to JDK 17 and ANDROID_HOME to the Android SDK, or add an uncommitted `local.properties` with `sdk.dir=/absolute/path/to/Android/Sdk`.

APK output: `app/build/outputs/apk/debug/app-debug.apk`. If the distribution includes `dist/Mufradati-debug.apk`, it is the prebuilt testing version. Check `VALIDATION.md` for the delivered build's status.

## Install on your phone

Enable Developer options (tap Build number seven times), enable USB debugging, connect the phone and approve the computer's key:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For the bundled build use `adb install -r dist/Mufradati-debug.apk`. Alternatively transfer the APK to your phone and open it, allowing installations from that file manager when prompted. Local builds typically use a different debug signing key. If Android reports INSTALL_FAILED_UPDATE_INCOMPATIBLE, export your vocabulary before uninstalling the previous app; uninstalling deletes review history. Release distribution should use Android Studio's Generate Signed App Bundle or APK with your own protected keystore.

## Features

- Five sections: Home, Vocabulary, Practice, Statistics, Settings.
- English/Arabic interface with RTL; light/dark/system theme; navy and teal palette; custom book/A icon; accessible icon labels and standard touch targets.
- Vocabulary CRUD with confirmation before deletion; word, meaning, definition, example, synonyms, part of speech, notes, category, favorite, added date and learning status.
- Search word/meaning, category/favorite filters, original insertion order, newest/oldest and A–Z/Z–A sorting persisted in DataStore.
- Opt-in sample vocabulary: twelve English/Arabic entries, available on empty Home or in Settings. No demo progress is seeded.
- Home and Statistics use actual saved vocabulary/history, including daily goals, streak, due count, reviewed words, known/difficult words, sessions, study time, quiz accuracy and a seven-day chart. Daily word recommendation comes from saved vocabulary.

### Four study modes

**Basic Review:** tap-to-flip animated cards, show meaning, previous/next, shuffle, favorite, word/example pronunciation and three self-ratings. Each word is rated once per session; revisiting does not inflate history. Next can skip a word.

**Multiple Answers:** four distinct randomized answers sourced from saved vocabulary, correct-answer feedback, score/progress, another round and reversed question direction. Requires sufficient distinct answers. Reversed quizzes exclude alternatives with the same meaning so synonyms do not create ambiguous choices.

**Match Cards:** independently shuffled word/meaning columns, selected states, disabled completed matches, brief wrong-answer feedback, active timer, move count, accuracy and selectable 4/6/8 pairs (or fewer if needed). Meanings are unique on each board. Each attempt updates review history.

**Whiteboard Review:** finger/stylus canvas, four colors, pen widths, erase, undo, clear, reveal answer and manual self-assessment. No handwriting recognition is claimed. Drawings are transient and never saved or uploaded; erasing paints over ink and can itself be undone.

Study collections include all, favorites, new and due words. Difficult overdue words are prioritized. Settings include direction, daily goal, sorting, animations, voice, speed, pitch, import/export and confirmed progress reset.

## Storage, review schedule and audio

Room stores words and review events; each rating update and history insert is one transaction. DataStore stores settings. Submitted results survive closing the app or reboot. The ViewModel preserves active sessions through activity recreation; an unfinished session resets after process death, while submitted history remains. Unsaved handwriting is intentionally transient. Database version 1 exports a schema; future versions require explicit migrations (no destructive migration fallback).

Schedule: incorrect = 1 day; almost = half the previous interval, minimum 1 day; known = 1, 3, 7, then doubling to 180 days. Known status reflects the latest rating, not an independently measured mastery level. Default sorting means insertion ID, not drag-reorder.

Daily goal counts reviews including incorrect answers. Streaks use local-calendar days and allow the current day to be unfinished. Sessions count distinct session IDs with a submitted answer. Study time is foreground time recorded with an answer, capped at ten minutes per answer; unsubmitted time is not counted. Goal completion history uses the currently selected goal. Deleting a word deletes its associated history after confirmation; details display the most recent 100 reviews.

TTS lists installed **offline English voices** only. Voice availability, accents, pitch support and pronunciation quality depend on the installed Android engine and downloaded language data. Install English voice data in Android settings, then reopen the app if no voice is available. Voice-data installation may require internet outside this app. Core study remains available without a voice.

## Import/export

The system document picker avoids broad storage permissions. Export is vocabulary transfer, not a full progress backup. Review history and preferences remain on-device; new imported entries start with New status. Cloud backup/device transfer are disabled by explicit rules.

CSV columns:
`word,meaning,definition,example,synonyms,part,notes,category,favorite,added`

Only word and meaning are required. UTF-8 CSV handles quoted commas, quotes, CRLF and multiline fields. JSON accepts a word array or `{ "format": "mufradati-v1", "words": [...] }`. Added dates are Unix milliseconds. Imports skip case-insensitive duplicate words. Invalid required data rolls back the whole batch. Limits: two million characters, 10,000 rows, 10,000 characters per field. Export before uninstalling.

## Source map and tests

- `Data.kt`: entities, DAO, Room setup/repository and DataStore.
- `StudyLogic.kt`: pure scheduling, sorting/search, quiz, matching, streak and sample data.
- `AppViewModel.kt`: StateFlows, operations, session state and errors.
- `MainActivity.kt`: theme, localization, navigation, home and vocabulary screens.
- `PracticeScreens.kt`: all four modes and result screens.
- `SettingsStatistics.kt`: preferences, document picker and real-data summaries.
- `Speech.kt`: TTS initialization, selection, playback and shutdown.
- `Exchange.kt`: JSON/CSV validation and serialization.

Ten unit tests cover schedule boundaries, sorting/search, due priorities, unique quiz choices, reverse ambiguity, insufficient vocabulary, matching, streaks and CSV escaping. Device tests cover Room CRUD/close-and-reopen persistence, atomic import, review/reset/deletion, JSON/CSV round trips, DataStore recreation, editor validation and flashcard reveal/rating. See `VALIDATION.md` for tests actually executed.

Pinned compatible stable dependencies: AGP 8.13.2, Gradle 8.13, Kotlin/Compose compiler 2.3.0, Compose BOM 2025.12.01, Activity 1.12.4, Lifecycle 2.10.0, Room 2.8.5, DataStore 1.2.0. The Compose BOM is a stable compatibility baseline, not the newest published BOM. Kapt is retained; KSP is a future build-performance improvement. Interface localization uses paired English/Arabic strings and Compose LayoutDirection, not Android per-app locale integration.

Official references:
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/develop/ui/compose/bom
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/reference/android/speech/tts/TextToSpeech

## Device acceptance checklist

Test all modes and empty/insufficient vocabulary; force-stop/reopen after ratings; rotate and use system back; inspect Arabic RTL, dark theme, large fonts, TalkBack and narrow screens; test TTS with/without English voice data and in airplane mode; import/export Arabic, quotes and multiline notes; reject malformed imports; confirm deletion and progress reset. An APK build is not a substitute for these device checks.
