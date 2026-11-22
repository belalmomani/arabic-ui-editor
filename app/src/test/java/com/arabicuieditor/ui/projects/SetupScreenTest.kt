package com.arabicuieditor.ui.projects

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-mdpi")
class SetupScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun editorButtonOpensEditorAndFillIsHiddenWhenUnavailable() {
        var opened = 0
        rule.setContent { SetupScreen("p1", onBack = {}, onOpenEditor = { opened++ }) }
        rule.onNodeWithTag("setup_open_editor").assertIsDisplayed().performClick()
        rule.onNodeWithTag("setup_fill").assertDoesNotExist()
        assertEquals(1, opened)
    }

    @Test
    fun fillButtonShownWhenProvided() {
        var fill = 0
        rule.setContent { SetupScreen("p1", onBack = {}, onOpenEditor = {}, onOpenFill = { fill++ }) }
        rule.onNodeWithTag("setup_fill").performClick()
        assertEquals(1, fill)
    }
}
