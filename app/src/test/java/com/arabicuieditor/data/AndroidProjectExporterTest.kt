package com.arabicuieditor.data

import com.arabicuieditor.data.export.AndroidProjectExporter
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.Templates
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newProject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidProjectExporterTest {
    private val text: (Int) -> String = { "نص$it" }

    private fun imageProject() = newProject("تطبيقي", "الرئيسية").let { p ->
        val img = UiElement(
            id = "i", type = ElementTypes.IMAGE,
            layout = ElementLayout(0f, 0f, SizeSpec.fixed(80f), SizeSpec.fixed(80f)),
            properties = mapOf(PropertyKeys.IMAGE_SRC to "img_1.png")
        )
        val txt = UiElement(
            id = "t", type = ElementTypes.TEXT,
            layout = ElementLayout(0f, 100f, SizeSpec.fixed(80f), SizeSpec.fixed(30f)),
            properties = mapOf(PropertyKeys.TEXT to "مرحبا", PropertyKeys.FONT_FAMILY to "my_custom_font")
        )
        p.copy(screens = listOf(p.screens[0].copy(elements = listOf(img, txt))))
    }

    @Test
    fun packageValidation() {
        assertTrue(AndroidProjectExporter.isValidPackage("com.example.app"))
        assertTrue(AndroidProjectExporter.isValidPackage("my.app_1"))
        assertFalse(AndroidProjectExporter.isValidPackage("app"))
        assertFalse(AndroidProjectExporter.isValidPackage("Com.Example"))
        assertFalse(AndroidProjectExporter.isValidPackage("com.1app"))
        assertFalse(AndroidProjectExporter.isValidPackage("com.class.app"))
        assertFalse(AndroidProjectExporter.isValidPackage("com..app"))
    }

    @Test
    fun containsAllProjectFiles() {
        val f = AndroidProjectExporter.files(imageProject(), "com.example.myapp", mapOf("img_1.png" to byteArrayOf(1, 2, 3)))
        for (p in listOf(
            "settings.gradle.kts", "build.gradle.kts", "gradle.properties", "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml", "app/src/main/res/values/strings.xml", "app/src/main/res/values/themes.xml",
            "app/src/main/java/com/example/myapp/MainActivity.kt", "app/src/main/java/com/example/myapp/Screens.kt",
            "app/src/main/res/drawable/img_1.png", ".github/workflows/build-apk.yml", ".gitignore", "README.md"
        )) assertTrue("missing $p", f.containsKey(p))
        assertEquals(3, f.getValue("app/src/main/res/drawable/img_1.png").size)
    }

    @Test
    fun xmlFilesAreWellFormed() {
        val f = AndroidProjectExporter.files(newProject("تطبيق & <خاص> 'x'", "ش"), "com.example.myapp", emptyMap())
        for (p in listOf("app/src/main/AndroidManifest.xml", "app/src/main/res/values/strings.xml", "app/src/main/res/values/themes.xml")) {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f.getValue(p).inputStream())
        }
    }

    @Test
    fun customFontsAreNotReferencedAndPackageIsConsistent() {
        val f = AndroidProjectExporter.files(imageProject(), "com.example.myapp", mapOf("img_1.png" to byteArrayOf(1)))
        val screens = String(f.getValue("app/src/main/java/com/example/myapp/Screens.kt"))
        assertFalse(screens.contains("R.font"))
        assertTrue(screens.contains("package com.example.myapp"))
        assertTrue(screens.contains("R.drawable.img_1"))
        assertTrue(screens.contains("import com.example.myapp.R"))
        assertTrue(String(f.getValue("app/build.gradle.kts")).contains("namespace = \"com.example.myapp\""))
        assertTrue(String(f.getValue("app/src/main/java/com/example/myapp/MainActivity.kt")).contains("AppRoot()"))
    }

    @Test
    fun missingImagesAreWarnedInReadme() {
        val f = AndroidProjectExporter.files(imageProject(), "com.example.myapp", emptyMap())
        assertFalse(f.containsKey("app/src/main/res/drawable/img_1.png"))
        assertTrue(String(f.getValue("README.md")).contains("img_1.png"))
    }

    @Test
    fun zipRoundTrip() {
        val bos = ByteArrayOutputStream()
        AndroidProjectExporter.writeZip(imageProject(), "com.example.myapp", null, bos)
        val names = ArrayList<String>()
        ZipInputStream(bos.toByteArray().inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; names += e.name }
        }
        assertTrue(names.contains("app/build.gradle.kts"))
        assertTrue(names.none { it.startsWith("/") || it.contains("..") })
    }

    /**
     * يكتب مشروعًا تجريبيًا من كل القوالب إلى app/build/exported-sample؛ وظيفة CI «verify-export»
     * تبنيه بـ Gradle فعليًا لاكتشاف أخطاء ترجمة الكود المُصدَّر.
     */
    @Test
    fun writeSampleForCi() {
        val base = newProject("عينة", "أولى")
        val screens = Templates.all.flatMap { Templates.build(it, emptyList(), text) }
        val project = base.copy(screens = screens, startScreenId = screens.first().id)
        val files = AndroidProjectExporter.files(project, "com.example.sample", emptyMap())
        val dir = File("build/exported-sample")
        dir.deleteRecursively()
        for ((path, bytes) in files) {
            val f = File(dir, path)
            f.parentFile.mkdirs()
            f.writeBytes(bytes)
        }
        assertTrue(File(dir, "app/build.gradle.kts").isFile)
    }
}
