package com.arabicuieditor.data

import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ComponentLibrary
import com.arabicuieditor.data.model.Templates
import com.arabicuieditor.data.model.TemplateCategory
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.ElementTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    private val text: (Int) -> String = { "ن$it" }

    @Test fun everyTemplatePresetIdExists() {
        val ids = ComponentLibrary.presets.map { it.id }.toSet()
        for (t in Templates.all) for (s in t.screens) for (p in s.presetIds) assertTrue("$p in ${t.id}", p in ids)
    }

    @Test fun templateIdsUniqueAndEveryCategoryCovered() {
        assertEquals(Templates.all.size, Templates.all.map { it.id }.toSet().size)
        for (c in TemplateCategory.entries) assertTrue(c.name, Templates.all.any { it.category == c })
    }

    @Test fun buildMakesFilledColumnScreens() {
        for (t in Templates.all) {
            val screens = Templates.build(t, emptyList(), text)
            assertEquals(t.screens.size, screens.size)
            for (s in screens) {
                assertEquals(ChildLayout.COLUMN, s.childLayout)
                assertTrue(s.elements.isNotEmpty())
            }
        }
    }

    @Test fun idsAreUniqueAcrossBuilds() {
        val t = Templates.all.first { it.id == "tpl_store" }
        val a = Templates.build(t, emptyList(), text)
        val b = Templates.build(t, a, text)
        val ids = (a + b).flatMap { s -> ElementTree.flatten(s.elements).map { it.id } } + (a + b).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun screenNamesStayUnique() {
        val t = Templates.all.first { it.id == "tpl_chat" }
        val existing = listOf(UiScreen("x", "ن${t.screens[0].nameRes}"))
        val built = Templates.build(t, existing, text)
        val names = (existing + built).map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun filterByCategoryAndQuery() {
        val label: (com.arabicuieditor.data.model.AppTemplate) -> String = { it.id }
        assertEquals(Templates.all.size, Templates.filter(null, "  ", label).size)
        assertEquals(1, Templates.filter(TemplateCategory.CHAT, "", label).size)
        assertEquals(listOf("tpl_news"), Templates.filter(null, "NEWS", label).map { it.id })
        assertTrue(Templates.filter(null, "zzz", label).isEmpty())
    }
}
