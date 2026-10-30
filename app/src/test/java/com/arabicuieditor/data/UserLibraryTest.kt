package com.arabicuieditor.data

import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.storage.UserLibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserLibraryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "library/library.json")

    private fun card(): UiElement = UiElement(
        id = "card-1", type = ElementTypes.CARD,
        layout = ElementLayout(10f, 20f, SizeSpec.fixed(200f), SizeSpec.fixed(100f)),
        childLayout = ChildLayout.COLUMN,
        properties = mapOf(PropertyKeys.BG_COLOR to "@color/surface", PropertyKeys.NAME to "بطاقتي"),
        children = listOf(
            UiElement(
                id = "btn-1", type = ElementTypes.BUTTON,
                layout = ElementLayout(0f, 0f, SizeSpec.fixed(80f), SizeSpec.fixed(40f)),
                properties = mapOf("text" to "اضغط")
            )
        )
    )

    @Test fun emptyWhenNoFile() = assertTrue(UserLibraryRepository(file()).list().isEmpty())

    @Test fun addKeepsSubtreeAndTokens() {
        val repo = UserLibraryRepository(file())
        repo.add("بطاقة", card())
        val back = UserLibraryRepository(file()).list().single()
        assertEquals("بطاقة", back.name)
        assertEquals(card(), back.root)
        assertEquals("@color/surface", back.root.properties[PropertyKeys.BG_COLOR])
    }

    @Test fun newestFirstAndRenameDelete() {
        val repo = UserLibraryRepository(file())
        repo.add("أ", card())
        val two = repo.add("ب", card())
        assertEquals(listOf("ب", "أ"), two.map { it.name })
        val renamed = repo.rename(two[0].id, "  جديد  ")
        assertEquals("جديد", renamed[0].name)
        val after = repo.delete(two[0].id)
        assertEquals(listOf("أ"), after.map { it.name })
        assertEquals(listOf("أ"), UserLibraryRepository(file()).list().map { it.name })
    }

    @Test fun blankNameRejected() {
        val repo = UserLibraryRepository(file())
        try {
            repo.add("   ", card())
            org.junit.Assert.fail("expected failure")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(repo.list().isEmpty())
    }

    @Test fun longNameIsTrimmed() {
        val repo = UserLibraryRepository(file())
        val items = repo.add("x".repeat(100), card())
        assertEquals(UserLibraryRepository.MAX_NAME_LENGTH, items[0].name.length)
    }

    @Test fun corruptMainFallsBackToBackup() {
        val repo = UserLibraryRepository(file())
        repo.add("أ", card())
        repo.add("ب", card()) // ينشئ .bak بمحتوى الحفظ الأول
        file().writeText("{ not json")
        assertEquals(listOf("أ"), UserLibraryRepository(file()).list().map { it.name })
    }

    @Test fun corruptItemIsSkippedOthersSurvive() {
        val good = UserLibraryRepository.encode(
            listOf(com.arabicuieditor.data.storage.UserComponent("id-1", "سليم", 1L, card()))
        )
        val broken = good.replace("\"items\":[", "\"items\":[{\"id\":\"bad\"},")
        val items = UserLibraryRepository.decode(broken)
        assertEquals(listOf("سليم"), items.map { it.name })
    }

    @Test fun reIdGivesFreshIdsSameShape() {
        val original = card()
        val copy = ElementTree.reId(original)
        assertNotEquals(original.id, copy.id)
        assertNotEquals(original.children[0].id, copy.children[0].id)
        assertEquals(original.properties, copy.properties)
        assertEquals(original.children.size, copy.children.size)
    }
}
