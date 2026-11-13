package com.arabicuieditor.data

import com.arabicuieditor.data.export.AndroidProjectExporter
import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.model.ComponentLibrary
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.ImageFits
import com.arabicuieditor.data.model.ImageNames
import com.arabicuieditor.data.model.PresetCategory
import com.arabicuieditor.data.model.PressEffects
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.StickerFavorites
import com.arabicuieditor.data.model.Stickers
import com.arabicuieditor.data.model.newProject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickersTest {
    private val assets = File("src/main/assets/${Stickers.ASSET_DIR}")

    @Test
    fun catalogHas13PacksAnd256UniqueTiles() {
        assertEquals(13, Stickers.packs.size)
        assertEquals(256, Stickers.allTiles.size)
        assertEquals(256, Stickers.allTiles.map { it.id }.toSet().size)
        assertEquals(listOf(25, 15, 20, 20, 20, 20, 16, 20, 20, 20, 20, 20, 20), Stickers.packs.map { it.tiles.size })
    }

    @Test
    fun everyTileHasAnAssetAndAValidImageName() {
        for (t in Stickers.allTiles) {
            assertTrue(t.fileName, ImageNames.isValid(t.fileName))
            assertTrue(t.fileName, Stickers.isSticker(t.fileName))
            val f = File(assets, t.fileName)
            assertTrue("missing asset ${f.path}", f.isFile && f.length() > 1000)
            assertTrue(t.w in 1..256 && t.h in 1..256 && maxOf(t.w, t.h) == 256)
        }
    }

    @Test
    fun assetsFolderHoldsExactlyTheCatalogAndStaysSmall() {
        val files = assets.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(Stickers.allTiles.map { it.fileName }.toSet(), files)
        assertTrue("assets too big", assets.listFiles().orEmpty().sumOf { it.length() } < 6_000_000)
    }

    @Test
    fun presetsAreReadyMadeButtons() {
        val presets = ComponentLibrary.byCategory(PresetCategory.STICKERS)
        assertEquals(256, presets.size)
        for (p in presets) {
            val e = p.build { "x" }.single()
            assertEquals(ElementTypes.IMAGE, e.type)
            assertTrue(Stickers.isSticker(e.properties.getValue(PropertyKeys.IMAGE_SRC)))
            assertEquals(ImageFits.FIT, e.properties[PropertyKeys.IMAGE_FIT])
            assertEquals(PressEffects.SHRINK, e.properties[PropertyKeys.PRESS_EFFECT])
            val big = maxOf(e.layout.width.value, e.layout.height.value)
            assertEquals(Stickers.ADD_MAX_DP, big, 1f)
        }
        // كل ملصق له مكوّنه المطابق
        assertEquals("stk_p01_01", Stickers.presetFor(Stickers.packs[0].tiles[0]).id)
    }

    @Test
    fun composeExportUsesDrawableAndPressEffect() {
        val e = Stickers.presetFor(Stickers.tile("p01_03")!!).build { "x" }.single()
        val p = newProject("م", "الرئيسية")
        val code = ComposeExporter.export(p.copy(screens = listOf(p.screens[0].copy(elements = listOf(e)))))
        assertTrue(code.contains("painterResource(R.drawable.stk_p01_03)"))
        assertTrue(code.contains(".pressEffect(\"${PressEffects.SHRINK}\")"))
        assertTrue(code.contains("ملصق مضمّن"))
    }

    @Test
    fun projectZipBundlesTheStickerFile() {
        val e = Stickers.presetFor(Stickers.tile("p13_20")!!).build { "x" }.single()
        val p = newProject("م", "الرئيسية")
        val project = p.copy(screens = listOf(p.screens[0].copy(elements = listOf(e))))
        assertEquals(listOf("stk_p13_20.webp"), AndroidProjectExporter.usedImages(project))
        val bytes = ByteArray(2048) { 7 }
        val bos = ByteArrayOutputStream()
        AndroidProjectExporter.writeZip(project, "com.example.app", null, bos, stickerLoader = { name ->
            if (name == "stk_p13_20.webp") bytes else null
        })
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(bos.toByteArray())).use { z ->
            var en = z.nextEntry
            while (en != null) { names += en.name; en = z.nextEntry }
        }
        assertTrue(names.contains("app/src/main/res/drawable/stk_p13_20.webp"))
        // بلا محمّل: لا ملف، ويُذكر في README كصورة ناقصة
        val files = AndroidProjectExporter.files(project, "com.example.app", emptyMap())
        assertFalse(files.containsKey("app/src/main/res/drawable/stk_p13_20.webp"))
    }

    @Test
    fun favoritesToggleKeepOrderAndIgnoreUnknownIds() {
        var f = emptyList<String>()
        f = StickerFavorites.toggle(f, "p02_03")
        f = StickerFavorites.toggle(f, "p01_01")
        f = StickerFavorites.toggle(f, "nope")
        assertEquals(listOf("p02_03", "p01_01"), f)
        f = StickerFavorites.toggle(f, "p02_03")
        assertEquals(listOf("p01_01"), f)
        assertEquals(listOf("p01_01", "p13_20"), StickerFavorites.decode("p01_01, bad ,p13_20,p01_01"))
        assertEquals("p01_01,p13_20", StickerFavorites.encode(listOf("p01_01", "p13_20")))
        assertEquals(emptyList<String>(), StickerFavorites.decode(null))
    }

    @Test
    fun blankImageButtonHasPressEffectAndNoSource() {
        val e = Stickers.blankImageButton.build { "x" }.single()
        assertEquals(ElementTypes.IMAGE, e.type)
        assertEquals(PressEffects.SHRINK, e.properties[PropertyKeys.PRESS_EFFECT])
        assertFalse(e.properties.containsKey(PropertyKeys.IMAGE_SRC))
    }
}
