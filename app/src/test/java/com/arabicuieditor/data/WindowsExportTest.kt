package com.arabicuieditor.data

import com.arabicuieditor.data.export.DesktopHtmlExporter
import com.arabicuieditor.data.export.WindowsProjectExporter
import com.arabicuieditor.data.export.XamlExporter
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.Platforms
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.data.model.newProject
import com.arabicuieditor.data.model.withProp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class WindowsExportTest {
    private fun sample(tech: String = Platforms.TECH_HTML): Project {
        val base = newProject("لوحة <تحكم>", "الرئيسية", platform = Platforms.WINDOWS, uiTech = tech)
        val second = UiScreen(id = newId(), name = "الإعدادات", width = 1280f, height = 720f)
        val title = newElement(ElementTypes.TEXT, 24f, 20f, "{مرحبًا} & \"أهلًا\"")
        val btn = Interactions.withClickAction(newElement(ElementTypes.BUTTON, 24f, 80f, "إعدادات"), ClickAction.Navigate(second.id))
        val back = Interactions.withClickAction(newElement(ElementTypes.BUTTON, 24f, 80f, "رجوع"), ClickAction.Back)
        val input = newElement(ElementTypes.INPUT, 24f, 140f, "بحث")
        val card = newElement(ElementTypes.CARD, 300f, 20f, "").let {
            it.copy(children = listOf(newElement(ElementTypes.TEXT, 10f, 10f, "داخل البطاقة")))
        }
        val line = newElement(ElementTypes.DIVIDER, 24f, 200f, "")
        val img = newElement(ElementTypes.IMAGE, 24f, 220f, "").withProp(PropertyKeys.IMAGE_SRC, "img_1.png")
        val list = newElement(ElementTypes.LIST, 24f, 340f, "").withProp(PropertyKeys.ITEMS, "أ\nب")
        val first = base.screens.first().copy(elements = listOf(title, btn, input, card, line, img, list))
        return base.copy(screens = listOf(first, second.copy(elements = listOf(back))), startScreenId = first.id)
    }

    private fun text(m: Map<String, ByteArray>, path: String) = String(m.getValue(path), Charsets.UTF_8)

    @Test fun htmlHasScreensNavigationAndLogicalPositions() {
        val files = DesktopHtmlExporter.files(sample(), DesktopHtmlExporter.Options(), mapOf("img_1.png" to byteArrayOf(1)))
        val html = text(files, "index.html")
        assertTrue(html.contains("id=\"screen-1\"") && html.contains("id=\"screen-2\""))
        assertTrue(html.contains("data-nav=\"screen-2\""))
        assertTrue(html.contains("data-back"))
        assertTrue(html.contains("dir=\"rtl\""))
        assertTrue(html.contains("&lt;تحكم&gt;"))
        assertTrue(html.contains("src=\"assets/img_1.png\""))
        assertTrue(files.containsKey("assets/img_1.png"))
        assertTrue(text(files, "css/styles.css").contains("inset-inline-start"))
        assertTrue(text(files, "css/styles.css").contains("width:1280px"))
    }

    @Test fun tokensHaveLightAndDarkThemes() {
        val css = text(DesktopHtmlExporter.files(sample()), "css/tokens.css")
        assertTrue(css.contains("--c-primary"))
        assertTrue(css.contains("[data-theme=\"dark\"]"))
        assertTrue(css.contains("prefers-color-scheme: dark"))
    }

    @Test fun ltrAndNoScriptOptions() {
        val files = DesktopHtmlExporter.files(sample(), DesktopHtmlExporter.Options(DesktopHtmlExporter.Script.NONE, rtl = false))
        assertTrue(text(files, "index.html").contains("dir=\"ltr\""))
        assertFalse(files.containsKey("js/app.js"))
        assertFalse(text(files, "index.html").contains("<script"))
    }

    @Test fun typeScriptSourceTypedAndJavaScriptClean() {
        val files = DesktopHtmlExporter.files(sample(), DesktopHtmlExporter.Options(DesktopHtmlExporter.Script.TS))
        val ts = text(files, "src/app.ts")
        val js = text(files, "js/app.js")
        assertTrue(ts.contains("history: string[]"))
        assertFalse(js.contains("⟦") || js.contains(": string") || js.contains(" as HTMLElement"))
        assertTrue(files.containsKey("tsconfig.json"))
    }

    @Test fun generatedJavaScriptCompiles() {
        val js = text(DesktopHtmlExporter.files(sample()), "js/app.js")
        val cx = org.mozilla.javascript.Context.enter()
        try {
            cx.optimizationLevel = -1
            assertNotNull(cx.compileString(js, "app.js", 1, null))
        } finally {
            org.mozilla.javascript.Context.exit()
        }
    }

    private fun assertWellFormed(xml: String, name: String) {
        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            throw AssertionError("$name غير سليم: ${e.message}\n$xml")
        }
    }

    @Test fun xamlIsWellFormedForBothFlavors() {
        for (flavor in XamlExporter.Flavor.values()) {
            val files = XamlExporter.files(sample(Platforms.TECH_WINUI), XamlExporter.Options(flavor), mapOf("img_1.png" to byteArrayOf(1)))
            files.filterKeys { it.endsWith(".xaml") }.forEach { (n, b) -> assertWellFormed(String(b, Charsets.UTF_8), n) }
            assertTrue(files.keys.any { it == "Views/Screen1Page.xaml" })
        }
    }

    @Test fun xamlNavigationThemesAndEscaping() {
        val winui = XamlExporter.files(sample())
        val page = text(winui, "Views/Screen1Page.xaml")
        assertTrue(page.contains("Tag=\"nav:Screen2Page\""))
        assertTrue(page.contains("Canvas.Left="))
        assertTrue(page.contains("FlowDirection=\"RightToLeft\""))
        assertTrue(page.contains("Text=\"{}{مرحبًا}"))
        assertTrue(text(winui, "Themes/Colors.xaml").contains("ThemeDictionaries"))
        assertTrue(text(winui, "Views/Screen2Page.xaml").contains("nav:back"))
        val wpf = XamlExporter.files(sample(), XamlExporter.Options(XamlExporter.Flavor.WPF, rtl = false))
        assertTrue(wpf.containsKey("Themes/Colors.Dark.xaml"))
        assertTrue(text(wpf, "Views/Screen1Page.xaml").contains("FlowDirection=\"LeftToRight\""))
        assertFalse(page.contains("x:Class"))
    }

    @Test fun everyTechnologyZipsAndUnzips() {
        for (tech in Platforms.WINDOWS_TECHS) {
            val p = sample(tech)
            val out = ByteArrayOutputStream()
            WindowsProjectExporter.writeZip(p, null, out)
            val names = ArrayList<String>()
            ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    names += e.name
                    assertTrue(e.name, z.readBytes().isNotEmpty())
                    e = z.nextEntry
                }
            }
            assertTrue("$tech: $names", names.contains("README.md"))
            assertEquals("$tech: $names", names.size, names.toSet().size)
            assertTrue(WindowsProjectExporter.zipName(p).endsWith(".zip"))
        }
    }

    @Test fun flutterProjectHasMainAndPubspec() {
        val files = WindowsProjectExporter.files(sample(Platforms.TECH_FLUTTER))
        assertTrue(text(files, "lib/main.dart").contains("runApp"))
        assertTrue(text(files, "pubspec.yaml").contains("flutter:"))
        assertTrue(text(files, "README.md").contains("flutter create --platforms=windows"))
    }

    @Test fun androidProjectsAreUnaffected() {
        val p = newProject("م", "ش")
        assertEquals(Platforms.ANDROID, p.platform)
        val files = com.arabicuieditor.data.export.AndroidProjectExporter.files(p, "com.example.app", emptyMap())
        assertTrue(files.containsKey("app/build.gradle.kts") || files.keys.any { it.endsWith("build.gradle.kts") })
    }
}
