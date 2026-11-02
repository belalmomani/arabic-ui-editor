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
}
