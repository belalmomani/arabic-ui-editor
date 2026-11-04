package com.arabicuieditor.data

import com.arabicuieditor.data.storage.ProjectJsonCodec
import com.arabicuieditor.data.storage.ProjectRepository
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectImportTest {
    private lateinit var root: File
    private lateinit var repo: ProjectRepository

    @Before fun setUp() {
        root = Files.createTempDirectory("import-test").toFile()
        repo = ProjectRepository(root)
    }

    @After fun tearDown() { root.deleteRecursively() }

    @Test
    fun backupRoundTripCreatesNewProject() {
        val original = repo.create("تجربة", "الأولى")
        val text = ProjectJsonCodec.encode(original)
        val imported = repo.importJson(text)
        assertNotNull(imported)
        assertNotEquals(original.id, imported!!.id)
        assertEquals(original.screens.size, imported.screens.size)
        assertEquals(2, repo.list().size)
    }

    @Test
    fun garbageIsRejected() {
        assertNull(repo.importJson("ليس JSON"))
        assertNull(repo.importJson("{\"x\":1}"))
        assertEquals(0, repo.list().size)
    }

    @Test
    fun zipRoundTripKeepsImages() {
        val original = repo.create("بصور", "الأولى")
        val res = repo.resourcesDir(original.id)
        res.mkdirs()
        java.io.File(res, "img_1.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        java.io.File(res, "evil.txt").writeBytes(byteArrayOf(9))
        val bytes = java.io.ByteArrayOutputStream().also { assertTrue(repo.exportZip(original.id, it)) }.toByteArray()

        val imported = repo.importZip(bytes.inputStream())
        assertNotNull(imported)
        assertNotEquals(original.id, imported!!.id)
        val copy = java.io.File(repo.resourcesDir(imported.id), "img_1.png")
        assertTrue(copy.exists())
        assertEquals(listOf<Byte>(1, 2, 3, 4), copy.readBytes().toList())
        assertTrue(!java.io.File(repo.resourcesDir(imported.id), "evil.txt").exists())
    }

    @Test
    fun zipRejectsPathTraversalAndGarbage() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("project.json"))
            z.write(com.arabicuieditor.data.storage.ProjectJsonCodec.encode(repo.create("x", "y")).toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("resources/../../escape.png"))
            z.write(byteArrayOf(7))
            z.closeEntry()
        }
        val imported = repo.importZip(out.toByteArray().inputStream())
        assertNotNull(imported)
        assertTrue(!java.io.File(root, "escape.png").exists())
        assertNull(repo.importZip("not a zip".byteInputStream()))
    }
}
