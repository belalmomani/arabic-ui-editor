package com.arabicuieditor.data

import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.export.FlutterExporter
import com.arabicuieditor.data.export.XmlExporter
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.ExternalKinds
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.data.storage.ProjectJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalActionTest {
    private fun button(action: ClickAction): UiElement = Interactions.withClickAction(
        UiElement(
            id = newId(), type = ElementTypes.BUTTON,
            layout = ElementLayout(0f, 0f, SizeSpec.fixed(120f), SizeSpec.fixed(44f)),
            properties = mapOf(PropertyKeys.TEXT to "زر")
        ), action
    )

    private fun project(a: ClickAction) = Project(
        id = newId(), name = "p", createdAt = 0L, updatedAt = 0L, screens = listOf(UiScreen(id = newId(), name = "s", elements = listOf(button(a))))
    )

    @Test fun uriBuildersNormalizeValues() {
        assertEquals("tel:+962791234567", ExternalKinds.uri(ExternalKinds.CALL, "+962 79 123 4567"))
        assertEquals("tel:0791234567", ExternalKinds.uri(ExternalKinds.CALL, "٠٧٩١٢٣٤٥٦٧"))
        assertEquals("https://wa.me/962791234567", ExternalKinds.uri(ExternalKinds.WHATSAPP, "+962-79-123-4567"))
        assertEquals("mailto:a@b.com", ExternalKinds.uri(ExternalKinds.EMAIL, " a@b.com "))
        assertEquals("https://example.com", ExternalKinds.uri(ExternalKinds.URL, "example.com"))
        assertEquals("http://x.org", ExternalKinds.uri(ExternalKinds.URL, "http://x.org"))
        assertTrue(ExternalKinds.uri(ExternalKinds.MAP, "عمّان")!!.startsWith("geo:0,0?q="))
        assertEquals("نص", ExternalKinds.uri(ExternalKinds.SHARE, "نص"))
    }

    @Test fun invalidOrEmptyValuesGiveNull() {
        assertNull(ExternalKinds.uri(ExternalKinds.CALL, ""))
        assertNull(ExternalKinds.uri(ExternalKinds.CALL, "12"))
        assertNull(ExternalKinds.uri(ExternalKinds.EMAIL, "not an email"))
        assertNull(ExternalKinds.uri(ExternalKinds.URL, "a b"))
        assertNull(ExternalKinds.uri("unknown", "x"))
    }

    @Test fun actionRoundTripsThroughElementAndJson() {
        val p = project(ClickAction.External(ExternalKinds.CALL, "+962791234567"))
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
        assertEquals(ClickAction.External(ExternalKinds.CALL, "+962791234567"), Interactions.clickAction(back.screens[0].elements[0]))
    }

    @Test fun composeExportOpensIntentWithContext() {
        val code = ComposeExporter.export(project(ClickAction.External(ExternalKinds.CALL, "0791234567")), null)
        assertTrue(code.contains("ACTION_DIAL"))
        assertTrue(code.contains("tel:0791234567"))
        assertTrue(code.contains("val ctx = LocalContext.current"))
        val share = ComposeExporter.export(project(ClickAction.External(ExternalKinds.SHARE, "مرحبا")), null)
        assertTrue(share.contains("ACTION_SEND"))
        val web = ComposeExporter.export(project(ClickAction.External(ExternalKinds.URL, "example.com")), null)
        assertTrue(web.contains("ACTION_VIEW") && web.contains("https://example.com"))
    }

    @Test fun emptyValueExportsTodoNotCrash() {
        val a = ClickAction.External(ExternalKinds.CALL, "")
        assertTrue(ComposeExporter.export(project(a), null).contains("TODO"))
        assertTrue(FlutterExporter.export(project(ClickAction.External(ExternalKinds.URL, "example.com")), null).contains("url_launcher"))
        assertTrue(XmlExporter.export(project(a), null).isNotBlank())
    }

    @Test fun flutterHeaderExplainsUrlLauncherOnlyWhenNeeded() {
        val with = FlutterExporter.export(project(ClickAction.External(ExternalKinds.CALL, "0791234567")), null)
        assertTrue(with.contains("flutter pub add url_launcher"))
        val without = FlutterExporter.export(project(ClickAction.Back), null)
        assertFalse(without.contains("flutter pub add url_launcher"))
    }
}
