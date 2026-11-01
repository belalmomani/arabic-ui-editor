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
}
