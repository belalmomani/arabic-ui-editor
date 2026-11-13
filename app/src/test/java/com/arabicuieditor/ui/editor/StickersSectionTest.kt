package com.arabicuieditor.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.arabicuieditor.data.model.ComponentPreset
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
class StickersSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val added = mutableListOf<ComponentPreset>()

    /** كما في اللوحة الحقيقية: المحتوى داخل تمرير رأسي. */
    @Composable
    private fun Scrolling(content: @Composable () -> Unit) {
        Column(Modifier.verticalScroll(rememberScrollState())) { content() }
    }

    @Test
    fun clickingAStickerAddsItsPreset() {
        rule.setContent { Scrolling { StickersSection(onAdd = { added += it }) } }
        rule.onNodeWithTag("sticker_p01_01").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("stk_p01_01"), added.map { it.id })
    }

    @Test
    fun switchingPackShowsItsStickers() {
        rule.setContent { Scrolling { StickersSection(onAdd = { added += it }) } }
        rule.onNodeWithTag("sticker_pack_p13").performScrollTo().performClick()
        rule.onNodeWithTag("sticker_p13_20").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("stk_p13_20"), added.map { it.id })
    }

    @Test
    fun longPressAddsToFavoritesAndShowsThemInTheFavoritesTab() {
        rule.setContent { Scrolling { StickersSection(onAdd = { added += it }) } }
        rule.onNodeWithTag("sticker_p01_02").performScrollTo().performTouchInput { longClick() }
        rule.onNodeWithTag("fav_p01_02").assertExists()
        assertEquals(emptyList<String>(), added.map { it.id })
        rule.onNodeWithTag("sticker_pack_p05").performScrollTo().performClick()
        rule.onNodeWithTag("sticker_pack_fav").performScrollTo().performClick()
        rule.onNodeWithTag("sticker_p01_02").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun blankImageButtonIsOffered() {
        rule.setContent { Scrolling { StickersSection(onAdd = { added += it }) } }
        rule.onNodeWithTag("sticker_blank_button").performScrollTo().performClick()
        assertEquals(listOf("stk_blank"), added.map { it.id })
    }
}
