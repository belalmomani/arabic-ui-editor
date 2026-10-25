package com.arabicuieditor.data

import com.arabicuieditor.data.model.Aligns
import com.arabicuieditor.data.model.Arrangements
import com.arabicuieditor.data.model.CURRENT_SCHEMA_VERSION
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Pins
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.withProp
import com.arabicuieditor.data.storage.ProjectJsonCodec
import com.arabicuieditor.data.storage.ProjectRepository
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** معيار المرحلة 5: مشروع بعدة شاشات وتخطيطات، حفظ، إعادة فتح، فتبقى البنية كما هي. */
class ScreensPersistenceTest {

    private lateinit var root: File
    private lateinit var repo: ProjectRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("screens-test").toFile()
        repo = ProjectRepository(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun nestedLayoutScreen(id: String, name: String): UiScreen {
        val label = newElement(ElementTypes.TEXT, 0f, 0f, "عنوان")
            .copy(layout = ElementLayout(0f, 0f, SizeSpec(SizeMode.MATCH_PARENT, 100f), SizeSpec(SizeMode.WRAP_CONTENT, 28f)))
            .withProp(PropertyKeys.WEIGHT, "0")
            .withProp(PropertyKeys.SELF_ALIGN_H, Aligns.CENTER)
        val button = newElement(ElementTypes.BUTTON, 0f, 0f, "حفظ")
            .copy(layout = ElementLayout(0f, 0f, SizeSpec(SizeMode.FRACTION, 40f), SizeSpec.fixed(44f)))
            .withProp(PropertyKeys.WEIGHT, "2")
        val row = UiElement(
            id = "row-$id",
            type = ElementTypes.CONTAINER,
            layout = ElementLayout(0f, 0f, SizeSpec(SizeMode.MATCH_PARENT, 300f), SizeSpec(SizeMode.WRAP_CONTENT, 60f)),
            childLayout = ChildLayout.ROW,
            properties = mapOf(
                PropertyKeys.ARRANGEMENT to Arrangements.BETWEEN,
                PropertyKeys.CHILD_ALIGN_V to Aligns.CENTER,
                PropertyKeys.SPACING to "12"
            ),
            children = listOf(button)
        )
        val column = UiElement(
            id = "col-$id",
            type = ElementTypes.CARD,
            layout = ElementLayout(10f, 20f, SizeSpec.fixed(300f), SizeSpec.fixed(300f)),
            childLayout = ChildLayout.COLUMN,
            properties = mapOf(PropertyKeys.PADDING_START to "16"),
            children = listOf(label, row)
        )
        val pinned = newElement(ElementTypes.BUTTON, 300f, 10f, "ثابت")
            .withProp(PropertyKeys.PIN_H, Pins.END)
        val box = UiElement(
            id = "box-$id", type = ElementTypes.CONTAINER,
            layout = ElementLayout(0f, 400f, SizeSpec.fixed(200f), SizeSpec.fixed(100f)),
            childLayout = ChildLayout.BOX,
            children = listOf(newElement(ElementTypes.TEXT, 0f, 0f, "وسط"))
        )
        return UiScreen(
            id = id, name = name, width = 411f, height = 891f,
            elements = listOf(column, pinned, box),
            childLayout = ChildLayout.ABSOLUTE,
            properties = mapOf(PropertyKeys.PADDING_TOP to "8")
        )
    }

    @Test
    fun multiScreenProjectSurvivesSaveAndReopen() {
        val created = repo.create("متعدد", "الرئيسية")
        val s1 = nestedLayoutScreen(created.screens.first().id, "الرئيسية")
        val s2 = nestedLayoutScreen("second", "التفاصيل").copy(childLayout = ChildLayout.COLUMN)
        val s3 = UiScreen("third", "الإعدادات")
        val project = created.copy(
            screens = listOf(s1, s2, s3),
            startScreenId = "second",
            previewWidth = 600f,
            previewHeight = 960f
        )
        repo.save(project)

        val reopened = repo.load(created.id)!!
        assertEquals(project.screens, reopened.screens)
        assertEquals("second", reopened.startScreenId)
        assertEquals("second", reopened.effectiveStartScreenId)
        assertEquals(600f, reopened.previewWidth, 0f)
        assertEquals(960f, reopened.previewHeight, 0f)
        assertEquals(ChildLayout.COLUMN, reopened.screens[1].childLayout)
        assertEquals(CURRENT_SCHEMA_VERSION, reopened.schemaVersion)

        // الشجرة المتداخلة والتخطيطات المرنة والأبعاد النسبية كلها استُعيدت
        val row = ElementTree.find(reopened.screens[0].elements, "row-${s1.id}")!!
        assertEquals(ChildLayout.ROW, row.childLayout)
        assertEquals(Arrangements.BETWEEN, row.properties[PropertyKeys.ARRANGEMENT])
        assertEquals(SizeSpec(SizeMode.FRACTION, 40f), row.children.single().layout.width)
        assertEquals(ChildLayout.BOX, ElementTree.find(reopened.screens[0].elements, "box-${s1.id}")!!.childLayout)
    }

    @Test
    fun reopenedProjectLaysOutIdenticallyAtEveryPreviewSize() {
        val created = repo.create("معاينة", "الرئيسية")
        val s = nestedLayoutScreen(created.screens.first().id, "الرئيسية")
        repo.save(created.copy(screens = listOf(s)))
        val reopened = repo.load(created.id)!!.screens.single()
        listOf(411f to 891f, 320f to 568f, 960f to 600f).forEach { (w, h) ->
            assertEquals(LayoutEngine.layout(s, w, h).frames, LayoutEngine.layout(reopened, w, h).frames)
        }
    }

    @Test
    fun missingStartScreenFallsBackToFirst() {
        val created = repo.create("بداية", "أولى")
        assertNull(created.startScreenId)
        assertEquals(created.screens.first().id, created.effectiveStartScreenId)
        val stale = created.copy(startScreenId = "deleted-screen")
        assertEquals(created.screens.first().id, stale.effectiveStartScreenId)
    }

    @Test
    fun schema2ProjectOpensWithAllElementsFree() {
        val v2 = """
            {"schemaVersion":2,"id":"abcdefgh-2","name":"قديم","createdAt":1,"updatedAt":2,
             "screens":[{"id":"s1","name":"ش","width":360,"height":640,"elements":[
               {"id":"e1","type":"container","layout":{"x":10,"y":20,"width":{"mode":"fixed","value":200},"height":{"mode":"fixed","value":100}},
                "childLayout":"absolute","properties":{},"events":[],
                "children":[{"id":"e2","type":"button","layout":{"x":5,"y":6,"width":{"mode":"fixed","value":80},"height":{"mode":"fixed","value":40}},
                             "properties":{"text":"x"},"events":[],"children":[]}]}]}],
             "resources":[]}
        """.trimIndent()
        val p = ProjectJsonCodec.decode(v2)
        assertEquals(CURRENT_SCHEMA_VERSION, p.schemaVersion)
        assertNull(p.startScreenId)
        assertEquals(0f, p.previewWidth, 0f)
        val screen = p.screens.single()
        assertEquals(ChildLayout.ABSOLUTE, screen.childLayout)
        assertTrue(screen.properties.isEmpty())
        val frames = LayoutEngine.layout(screen).frames
        assertEquals(15f, frames.getValue("e2").x, 0.01f)
        assertEquals(26f, frames.getValue("e2").y, 0.01f)
    }

    @Test
    fun jsonUsesReadableLowercaseNamesForNewModes() {
        val s = nestedLayoutScreen("s", "ش")
        val text = ProjectJsonCodec.encode(repo.create("x", "y").copy(screens = listOf(s), startScreenId = "s"))
        assertTrue(text.contains("\"startScreenId\""))
        assertTrue(text.contains("\"box\""))
        assertTrue(text.contains("\"fraction\""))
        assertTrue(text.contains("\"match_parent\""))
        assertNotNull(ProjectJsonCodec.decode(text))
    }
}
