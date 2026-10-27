package com.mufradati

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InterfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun editorValidatesAndSavesRequiredFields() {
        var saved: Word? = null
        compose.setContent { MaterialTheme { WordEditor(null, {}, { saved = it }) } }
        compose.onNodeWithText("Save word").performScrollTo().assertIsNotEnabled()
        compose
            .onNodeWithText("English word or phrase *")
            .performScrollTo()
            .performTextInput("learn")
        compose.onNodeWithText("Meaning *").performScrollTo().performTextInput("يتعلم")
        compose.onNodeWithText("Save word").performScrollTo().performClick()
        assertEquals("learn", saved?.word)
    }

    @Test
    fun flashcardRevealsBeforeRating() {
        var rating = -1
        compose.setContent {
            MaterialTheme {
                Page("Review") {
                    Flashcard(
                        Word(word = "learn", meaning = "يتعلم"),
                        false,
                        false,
                        false,
                        { rating = it },
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("Show Meaning").performScrollTo().performClick()
        compose.onNodeWithText("يتعلم").assertExists()
        compose.onNodeWithText("Know it").performScrollTo().performClick()
        assertEquals(2, rating)
    }
}
