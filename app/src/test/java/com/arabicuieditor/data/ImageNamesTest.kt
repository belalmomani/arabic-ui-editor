package com.arabicuieditor.data

import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.ImageNames
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageNamesTest {

    @Test
    fun validatesNames() {
        assertTrue(ImageNames.isValid("img_1.png"))
        assertTrue(ImageNames.isValid("img_12.jpg"))
        assertFalse(ImageNames.isValid("../x.png"))
        assertFalse(ImageNames.isValid("IMG.png"))
        assertFalse(ImageNames.isValid("a b.png"))
        assertFalse(ImageNames.isValid("img_1.gif"))
        assertFalse(ImageNames.isValid(""))
    }

    @Test
    fun nextNameSkipsUsed() {
        assertEquals("img_1.png", ImageNames.nextName(emptySet(), "png"))
        assertEquals("img_3.jpg", ImageNames.nextName(setOf("img_1.png", "img_2.jpg"), "jpg"))
        assertEquals("img_2.png", ImageNames.nextName(setOf("img_1.jpg"), "png"))
    }

    @Test
    fun drawableNameStripsExtension() {
        assertEquals("img_7", ImageNames.drawableName("img_7.png"))
    }

    @Test
    fun sampleSizeIsPowerOfTwo() {
        assertEquals(1, ImageNames.sampleSize(1000, 800, 1600))
        assertEquals(2, ImageNames.sampleSize(4000, 3000, 1600))
        assertEquals(4, ImageNames.sampleSize(8000, 6000, 1600))
        assertEquals(1, ImageNames.sampleSize(0, 0, 1600))
    }

    @Test
    fun registryAcceptsOnlyValidImageSrc() {
        assertEquals("img_1.png", PropertyRegistry.normalize(PropertyKeys.IMAGE_SRC, " img_1.png "))
        assertEquals("", PropertyRegistry.normalize(PropertyKeys.IMAGE_SRC, ""))
        assertNull(PropertyRegistry.normalize(PropertyKeys.IMAGE_SRC, "../../etc/passwd"))
    }

    @Test
    fun exporterUsesPainterForImages() {
        val image = UiElement(
            id = "i1", type = ElementTypes.IMAGE,
            layout = ElementLayout(0f, 0f, SizeSpec(SizeMode.FIXED, 100f), SizeSpec(SizeMode.FIXED, 80f)),
            properties = mapOf(PropertyKeys.IMAGE_SRC to "img_1.png")
        )
        val base = newProject("ت", "أ")
        val screen = base.screens.first().copy(elements = listOf(image))
        val src = ComposeExporter.export(base.copy(screens = listOf(screen), startScreenId = screen.id), null)
        assertTrue(src.contains("painterResource(R.drawable.img_1)"))
        assertTrue(src.contains("import androidx.compose.ui.res.painterResource"))
        assertTrue(src.contains("contentScale = ContentScale.Crop"))
    }

    @Test
    fun iconsValidateAndExport() {
        assertTrue(com.arabicuieditor.data.model.IconCatalog.isValid("Home"))
        assertFalse(com.arabicuieditor.data.model.IconCatalog.isValid("home"))
        assertEquals("Home", PropertyRegistry.normalize(PropertyKeys.ICON, "Home"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.ICON, "Evil.run()"))
        val image = UiElement(
            id = "i2", type = ElementTypes.IMAGE,
            layout = ElementLayout(0f, 0f, SizeSpec(SizeMode.FIXED, 48f), SizeSpec(SizeMode.FIXED, 48f)),
            properties = mapOf(PropertyKeys.ICON to "Star")
        )
        val base = newProject("ت", "أ")
        val screen = base.screens.first().copy(elements = listOf(image))
        val src = ComposeExporter.export(base.copy(screens = listOf(screen), startScreenId = screen.id), null)
        assertTrue(src.contains("Icons.Filled.Star"))
        assertTrue(src.contains("import androidx.compose.material.icons.Icons"))
    }

    @Test
    fun presetIconsAreAllValid() {
        val ids = com.arabicuieditor.data.model.ComponentLibrary.presets
            .flatMap { it.build { _ -> "x" } }
            .flatMap { com.arabicuieditor.data.model.ElementTree.flatten(listOf(it)) }
            .mapNotNull { it.properties[PropertyKeys.ICON] }
        assertTrue(ids.isNotEmpty())
        ids.forEach { assertTrue(it, com.arabicuieditor.data.model.IconCatalog.isValid(it)) }
    }

    @Test
    fun imageFitExportsAndValidates() {
        assertEquals("fit", PropertyRegistry.normalize(PropertyKeys.IMAGE_FIT, "fit"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.IMAGE_FIT, "zoom"))
        val image = UiElement(
            id = "i3", type = ElementTypes.IMAGE,
            layout = ElementLayout(0f, 0f, SizeSpec(SizeMode.FIXED, 100f), SizeSpec(SizeMode.FIXED, 80f)),
            properties = mapOf(PropertyKeys.IMAGE_SRC to "img_2.jpg", PropertyKeys.IMAGE_FIT to "fill")
        )
        val base = newProject("ت", "أ")
        val screen = base.screens.first().copy(elements = listOf(image))
        val src = ComposeExporter.export(base.copy(screens = listOf(screen), startScreenId = screen.id), null)
        assertTrue(src.contains("ContentScale.FillBounds"))
    }
}
