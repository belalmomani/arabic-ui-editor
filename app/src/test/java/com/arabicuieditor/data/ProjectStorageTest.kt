package com.arabicuieditor.data

import com.arabicuieditor.data.model.CURRENT_SCHEMA_VERSION
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.data.storage.ProjectJsonCodec
import com.arabicuieditor.data.storage.ProjectRepository
import com.arabicuieditor.data.storage.UnsupportedSchemaException
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectStorageTest {

    private lateinit var root: File
    private lateinit var repo: ProjectRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("projects-test").toFile()
        repo = ProjectRepository(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun projectWithDesign(): com.arabicuieditor.data.model.Project {
        val created = repo.create("تجربة", "الرئيسية")
        val screen = created.screens.first()
        val elements = ElementTypes.all.mapIndexed { i, type ->
            newElement(type, 10f + i * 7, 20f + i * 9, "نص $i")
        }
        // عنصر داخل عنصر بتخطيط مرن محجوز للمستقبل
        val nested = UiElement(
            id = newId(),
            type = ElementTypes.CONTAINER,
            layout = ElementLayout(5f, 6f, SizeSpec(SizeMode.MATCH_PARENT), SizeSpec(SizeMode.WRAP_CONTENT, 55f)),
            childLayout = ChildLayout.COLUMN,
            children = listOf(newElement(ElementTypes.TEXT, 1f, 2f, "ابن"))
        )
        return created.copy(screens = listOf(screen.copy(elements = elements + nested)))
    }

    @Test
    fun designSurvivesSaveAndReload() {
        val original = projectWithDesign()
        repo.save(original)
        val loaded = repo.load(original.id)
        assertNotNull(loaded)
        assertEquals(original.screens, loaded!!.screens)
        assertEquals(ElementTypes.all.size + 1, loaded.screens.first().elements.size)
        assertEquals(CURRENT_SCHEMA_VERSION, loaded.schemaVersion)
    }

    @Test
    fun layoutModesAndChildrenRoundTrip() {
        val original = projectWithDesign()
        val decoded = ProjectJsonCodec.decode(ProjectJsonCodec.encode(original))
        val nested = decoded.screens.first().elements.last()
        assertEquals(ChildLayout.COLUMN, nested.childLayout)
        assertEquals(SizeMode.MATCH_PARENT, nested.layout.width.mode)
        assertEquals(SizeSpec(SizeMode.WRAP_CONTENT, 55f), nested.layout.height)
        assertEquals("ابن", nested.children.single().properties["text"])
    }

    @Test
    fun phase2ProjectWithoutLayoutStillOpens() {
        val v1 = """
            {"schemaVersion":1,"id":"abcdefgh-1","name":"قديم","createdAt":1,"updatedAt":2,
             "screens":[{"id":"s1","name":"ش","elements":[{"id":"e1","type":"button","properties":{"text":"x"},"events":[],"children":[]}]}],
             "resources":[]}
        """.trimIndent()
        val p = ProjectJsonCodec.decode(v1)
        val screen = p.screens.single()
        assertEquals(360f, screen.width, 0f)
        assertEquals(640f, screen.height, 0f)
        assertEquals(ElementLayout(), screen.elements.single().layout)
        assertEquals(CURRENT_SCHEMA_VERSION, p.schemaVersion)
    }

    @Test(expected = UnsupportedSchemaException::class)
    fun newerSchemaIsRejected() {
        ProjectJsonCodec.decode("""{"schemaVersion":99,"id":"abcdefgh-1"}""")
    }

    @Test
    fun corruptedMainFileFallsBackToBackup() {
        val p = projectWithDesign()
        repo.save(p)
        repo.save(p.copy(name = "ثانٍ")) // يُنشئ .bak من الحفظ الأول
        File(root, "${p.id}/project.json").writeText("{ not json")
        val loaded = repo.load(p.id)
        assertNotNull(loaded)
        assertEquals(p.screens, loaded!!.screens)
    }

    @Test
    fun leftoverTmpFileFromInterruptedSaveIsRecovered() {
        val p = projectWithDesign()
        repo.save(p)
        val main = File(root, "${p.id}/project.json")
        // محاكاة إغلاق مفاجئ: الأصلي ضاع والمؤقت مكتمل
        File(main.path + ".tmp").writeText(ProjectJsonCodec.encode(p))
        main.delete()
        File(main.path + ".bak").delete()
        assertNotNull(repo.load(p.id))
    }

    @Test
    fun duplicateCopiesDesignWithNewId() {
        val p = projectWithDesign()
        repo.save(p)
        val copy = repo.duplicate(p.id, "نسخة")!!
        assertTrue(copy.id != p.id)
        assertEquals(p.screens, repo.load(copy.id)!!.screens)
    }
}
