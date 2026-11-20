package com.arabicuieditor.data

import com.arabicuieditor.data.export.AndroidProjectExporter
import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.Sounds
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.newProject
import com.arabicuieditor.data.storage.DriveSync
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundsTest {
    private fun projectWithSound(sound: String, enter: String = Sounds.NONE) = run {
        val e = newElement(ElementTypes.BUTTON, 10f, 10f, "b")
        val e2 = e.copy(properties = e.properties + (PropertyKeys.SOUND to sound))
        val p = newProject("م", "الرئيسية")
        val sc = p.screens[0]
        p.copy(screens = listOf(sc.copy(elements = listOf(e2), properties = sc.properties + (PropertyKeys.ENTER_SOUND to enter))))
    }

    @Test
    fun everySoundHasAnAssetFile() {
        assertEquals(13, Sounds.all.size)
        for ((id, _) in Sounds.all) assertTrue(id, File("src/main/assets/${Sounds.assetPath(id)}").isFile)
        assertNull(Sounds.valid(Sounds.NONE))
        assertNull(Sounds.valid("nope"))
        assertEquals("pop", Sounds.valid("pop"))
    }

    @Test
    fun usedInCollectsElementAndScreenSounds() {
        assertEquals(listOf("tada", "pop"), Sounds.usedIn(projectWithSound("pop", "tada")))
        assertTrue(Sounds.usedIn(projectWithSound(Sounds.NONE)).isEmpty())
    }

    @Test
    fun composeExportPlaysTheSound() {
        val code = ComposeExporter.export(projectWithSound("pop", "tada"))
        assertTrue(code.contains("playSound(ctx, \"snd_pop\")"))
        assertTrue(code.contains("LaunchedEffect(Unit) { playSound(ctx, \"snd_tada\") }"))
        assertTrue(code.contains("private fun playSound"))
        assertFalse(ComposeExporter.export(projectWithSound(Sounds.NONE)).contains("playSound"))
    }

    @Test
    fun androidProjectCopiesRawSoundFiles() {
        val files = AndroidProjectExporter.files(
            projectWithSound("pop"), "com.example.app", emptyMap(), sounds = mapOf("pop" to byteArrayOf(1, 2))
        )
        assertTrue(files.containsKey("app/src/main/res/raw/snd_pop.wav"))
    }

    @Test
    fun driveFileNameIsSafe() {
        assertEquals("مشروعي.zip", DriveSync.fileName("مشروعي"))
        assertEquals("a_b_.zip", DriveSync.fileName("a/b*"))
        assertEquals("project.zip", DriveSync.fileName("   "))
    }

    @Test
    fun customSoundNamesAndFormats() {
        assertTrue(Sounds.isCustom("usr_3.mp3"))
        assertFalse(Sounds.isCustom("usr_3.exe"))
        assertFalse(Sounds.isCustom("../usr_3.mp3"))
        assertTrue(Sounds.isValid("usr_12.wav"))
        assertEquals("usr_3", Sounds.rawName("usr_3.mp3"))
        assertEquals("usr_3.mp3", Sounds.fileName("usr_3.mp3"))
        assertEquals("mp3", Sounds.customExt("audio/mpeg", null))
        assertEquals("ogg", Sounds.customExt(null, "x.OGG"))
        assertNull(Sounds.customExt("video/mp4", "x.mp4"))
        assertEquals("usr_2.wav", Sounds.nextCustomName(setOf("usr_1.mp3", "img_1.png"), "wav"))
        assertEquals("usr_1.mp3", Sounds.nextCustomName(emptySet(), "mp3"))
    }

    @Test
    fun customSoundPassesRegistryAndExports() {
        assertEquals("usr_1.mp3", PropertyRegistry.normalize(PropertyKeys.SOUND, "usr_1.mp3"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.SOUND, "usr_1.exe"))
        val p = projectWithSound("usr_1.mp3")
        assertTrue(ComposeExporter.export(p).contains("playSound(ctx, \"usr_1\")"))
        val dir = java.nio.file.Files.createTempDirectory("snd").toFile()
        File(dir, "usr_1.mp3").writeBytes(byteArrayOf(9, 9))
        val bos = java.io.ByteArrayOutputStream()
        AndroidProjectExporter.writeZip(p, "com.example.app", dir, bos)
        val names = ArrayList<String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bos.toByteArray())).use { z ->
            var e = z.nextEntry
            while (e != null) { names += e.name; e = z.nextEntry }
        }
        assertTrue(names.contains("app/src/main/res/raw/usr_1.mp3"))
    }

    @Test
    fun driveNeedsUploadOnlyWhenNewer() {
        assertTrue(DriveSync.needsUpload(10L, 0L))
        assertTrue(DriveSync.needsUpload(10L, 9L))
        assertFalse(DriveSync.needsUpload(10L, 10L))
    }

    @Test
    fun longPressSoundIsExportedAndCounted() {
        val e = newElement(ElementTypes.BUTTON, 10f, 10f, "b")
        val e2 = e.copy(properties = e.properties + (PropertyKeys.LONG_SOUND to "bell"))
        val p = newProject("م", "الرئيسية")
        val p2 = p.copy(screens = listOf(p.screens[0].copy(elements = listOf(e2))))
        assertEquals(listOf("bell"), Sounds.usedIn(p2))
        val code = ComposeExporter.export(p2)
        assertTrue(code.contains("onLongClick = { playSound(ctx, \"snd_bell\") }"))
        assertTrue(code.contains("@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)"))
        assertTrue(code.indexOf("@file:OptIn") < code.indexOf("package "))
    }

    @Test
    fun disabledElementIsDimmedAndNotClickable() {
        val e = newElement(ElementTypes.BUTTON, 10f, 10f, "b")
        val off = e.copy(properties = e.properties + (PropertyKeys.ENABLED to "false") + (PropertyKeys.SOUND to "pop"))
        assertEquals(40, com.arabicuieditor.data.model.ElementState.opacityPercent(100, off))
        assertEquals(100, com.arabicuieditor.data.model.ElementState.opacityPercent(100, e))
        val p = newProject("م", "الرئيسية")
        val proj = p.copy(screens = listOf(p.screens[0].copy(elements = listOf(off))))
        val code = ComposeExporter.export(proj)
        assertTrue(code.contains(".alpha(0.4f)"))
        assertFalse(code.contains(".clickable"))
    }

    @Test
    fun disabledColorsApplyOnlyWhenDisabled() {
        val e = newElement(ElementTypes.BUTTON, 10f, 10f, "b")
        val styled = e.copy(properties = e.properties + (PropertyKeys.DISABLED_BG to "#FF999999") + (PropertyKeys.DISABLED_TEXT_COLOR to "#FF111111"))
        val on = com.arabicuieditor.data.model.ElementState.resolved(styled)
        assertEquals(styled.properties[PropertyKeys.BG_COLOR], on.properties[PropertyKeys.BG_COLOR])
        val off = com.arabicuieditor.data.model.ElementState.resolved(styled.copy(properties = styled.properties + (PropertyKeys.ENABLED to "false")))
        assertEquals("#FF999999", off.properties[PropertyKeys.BG_COLOR])
        assertEquals("#FF111111", off.properties[PropertyKeys.TEXT_COLOR])
        // غير مضبوط (شفاف) فلا تغيير
        val plain = e.copy(properties = e.properties + (PropertyKeys.ENABLED to "false"))
        assertEquals(plain, com.arabicuieditor.data.model.ElementState.resolved(plain))
    }

    @Test
    fun selectedStateSwapsColorsAndDisabledWins() {
        val e = newElement(ElementTypes.BUTTON, 10f, 10f, "b")
        val styled = e.copy(
            properties = e.properties + (PropertyKeys.SELECTED to "true") + (PropertyKeys.SELECTED_BG to "#FF00AA00") +
                (PropertyKeys.SELECTED_TEXT_COLOR to "#FFFFFFFF") + (PropertyKeys.DISABLED_BG to "#FF999999")
        )
        val sel = com.arabicuieditor.data.model.ElementState.resolved(styled)
        assertEquals("#FF00AA00", sel.properties[PropertyKeys.BG_COLOR])
        assertEquals("#FFFFFFFF", sel.properties[PropertyKeys.TEXT_COLOR])
        val off = com.arabicuieditor.data.model.ElementState.resolved(styled.copy(properties = styled.properties + (PropertyKeys.ENABLED to "false")))
        assertEquals("#FF999999", off.properties[PropertyKeys.BG_COLOR])
        assertEquals("true", PropertyRegistry.normalize(PropertyKeys.SELECTED, "true"))
    }

    @Test
    fun pressTintIsExportedAsOverlay() {
        val e = newElement(ElementTypes.BUTTON, 10f, 10f, "b")
        val t = e.copy(properties = e.properties + (PropertyKeys.PRESS_TINT to "#40000000"))
        val p = newProject("م", "الرئيسية")
        val code = ComposeExporter.export(p.copy(screens = listOf(p.screens[0].copy(elements = listOf(t)))))
        assertTrue(code.contains(".pressEffect(\"none\", Color(android.graphics.Color.parseColor(\"#40000000\"))"))
        assertTrue(code.contains("drawWithContent"))
        assertEquals("#40000000", com.arabicuieditor.data.model.ElementState.pressTint(t))
        assertNull(com.arabicuieditor.data.model.ElementState.pressTint(e))
    }

    @Test
    fun exitSoundIsCollectedAndExported() {
        val base = projectWithSound(Sounds.NONE)
        val sc = base.screens[0]
        val p = base.copy(screens = listOf(sc.copy(properties = sc.properties + (PropertyKeys.EXIT_SOUND to "pop"))))
        assertEquals(listOf("pop"), Sounds.usedIn(p))
        val code = com.arabicuieditor.data.export.ComposeExporter.export(p)
        assertTrue(code.contains("DisposableEffect(Unit) { onDispose {"))
        assertTrue(code.contains("snd_pop"))
    }
}
