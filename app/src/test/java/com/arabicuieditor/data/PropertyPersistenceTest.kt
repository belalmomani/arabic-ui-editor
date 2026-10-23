package com.arabicuieditor.data

import com.arabicuieditor.data.model.ComponentLibrary
import com.arabicuieditor.data.model.ElementOps
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.boolProp
import com.arabicuieditor.data.model.colorProp
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.data.model.withProp
import com.arabicuieditor.data.storage.ProjectJsonCodec
import com.arabicuieditor.data.storage.ProjectRepository
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/** معيار المرحلة 4: تعديل خصائص عنصر، حفظ المشروع، إعادة فتحه، فتبقى القيم صحيحة. */
class PropertyPersistenceTest {

    private lateinit var root: File
    private lateinit var repo: ProjectRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("props-test").toFile()
        repo = ProjectRepository(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun editedPropertiesSurviveSaveAndReopen() {
        val created = repo.create("خصائص", "الرئيسية")
        val button = newElement(ElementTypes.BUTTON, 20f, 30f, "حفظ")
            .withProp(PropertyKeys.NAME, "زر الحفظ")
            .withProp(PropertyKeys.BG_COLOR, "#FFE8710A")
            .withProp(PropertyKeys.TEXT_COLOR, "#FF000000")
            .withProp(PropertyKeys.FONT_SIZE, "22")
            .withProp(PropertyKeys.FONT_WEIGHT, "bold")
            .withProp(PropertyKeys.CORNER_RADIUS, "24")
            .withProp(PropertyKeys.BORDER_WIDTH, "3")
            .withProp(PropertyKeys.OPACITY, "60")
            .withProp(PropertyKeys.VISIBLE, "false")
            .withProp(PropertyKeys.PADDING_START, "20")
            .withProp(PropertyKeys.MARGIN_TOP, "16")
        val list = newElement(ElementTypes.LIST, 10f, 100f, "x")
            .withProp(PropertyKeys.ITEMS, "الأول\nالثاني\nالثالث")
            .withProp(PropertyKeys.ITEM_HEIGHT, "52")

        val screen = created.screens.first().copy(elements = listOf(button, list))
        repo.save(created.copy(screens = listOf(screen)))

        val reopened = repo.load(created.id)!!.screens.first().elements
        val b = reopened[0]
        assertEquals("زر الحفظ", b.prop(PropertyKeys.NAME))
        assertEquals(PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "#FFE8710A")?.let { com.arabicuieditor.data.model.ColorUtil.parse(it) }, b.colorProp(PropertyKeys.BG_COLOR))
        assertEquals(22, b.intProp(PropertyKeys.FONT_SIZE))
        assertEquals("bold", b.prop(PropertyKeys.FONT_WEIGHT))
        assertEquals(24, b.intProp(PropertyKeys.CORNER_RADIUS))
        assertEquals(3, b.intProp(PropertyKeys.BORDER_WIDTH))
        assertEquals(60, b.intProp(PropertyKeys.OPACITY))
        assertFalse(b.boolProp(PropertyKeys.VISIBLE))
        assertEquals(20, b.intProp(PropertyKeys.PADDING_START))
        assertEquals(16, b.intProp(PropertyKeys.MARGIN_TOP))
        assertEquals("حفظ", b.prop(PropertyKeys.TEXT))
        // القائمة متعددة الأسطر
        assertEquals("الأول\nالثاني\nالثالث", reopened[1].prop(PropertyKeys.ITEMS))
        assertEquals(52, reopened[1].intProp(PropertyKeys.ITEM_HEIGHT))
        // وكل ما حُفظ يطابق ما كان في الذاكرة
        assertEquals(listOf(button, list), reopened)
    }

    @Test
    fun zOrderAndPresetsSurviveReopen() {
        val created = repo.create("ترتيب", "الرئيسية")
        val preset = ComponentLibrary.presets.first { it.id == "card_title_body" }
        val placed = ElementOps.placeCentered(preset.build { "نص" }, 180f, 200f, 360f, 640f)
        val named = ElementOps.withUniqueNames(emptyList(), placed) { "عنصر" }
        val reordered = ElementOps.sendToBack(ElementOps.bringToFront(named, named.first().id), named.last().id)
        repo.save(created.copy(screens = listOf(created.screens.first().copy(elements = reordered))))

        val reopened = repo.load(created.id)!!.screens.first().elements
        assertEquals(reordered.map { it.id }, reopened.map { it.id })
        assertEquals(reordered, reopened)
        assertNotNull(reopened.first().properties[PropertyKeys.NAME])
    }

    @Test
    fun undoneStateIsWhatGetsSaved() {
        // محاكاة: لقطة قبل التعديل ثم لقطة بعده؛ حفظ اللقطة القديمة يعيد القيم القديمة تمامًا.
        val created = repo.create("تراجع", "الرئيسية")
        val before = created.screens
        val edited = created.copy(
            screens = listOf(
                created.screens.first().copy(
                    elements = listOf(newElement(ElementTypes.TEXT, 0f, 0f, "س").withProp(PropertyKeys.FONT_SIZE, "40"))
                )
            )
        )
        repo.save(edited)
        repo.save(edited.copy(screens = before)) // ما يفعله التراجع
        assertEquals(before, repo.load(created.id)!!.screens)
        assertEquals(before, ProjectJsonCodec.decode(ProjectJsonCodec.encode(created)).screens)
    }
}
