package com.arabicuieditor.data.model

import com.arabicuieditor.R

/**
 * ملصقات جاهزة (بطاقات مصوّرة) تُضاف كأزرار: عنصر «صورة» بتأثير ضغط وإجراء نقر قابل للضبط.
 * الملفات WebP مضمّنة في assets/stickers باسم stk_<حزمة>_<رقم>.webp؛ والاسم نفسه صالح كاسم
 * drawable في المشروع المُصدَّر، فتمرّ بمسار الصور المعتاد (ImageNames).
 */
object Stickers {
    const val ASSET_DIR = "stickers"
    const val PREFIX = "stk_"
    /** أكبر ضلع للملصق عند إضافته (dp). */
    const val ADD_MAX_DP = 130f

    class Pack(val id: String, val nameRes: Int, val tiles: List<Tile>)
    class Tile(val id: String, val w: Int, val h: Int) {
        val fileName: String get() = fileNameOf(id)
    }

    fun fileNameOf(id: String) = "$PREFIX$id.webp"
    fun isSticker(name: String) = name.startsWith(PREFIX) && name.endsWith(".webp")
    fun assetPath(name: String) = "$ASSET_DIR/$name"

    private fun pack(id: String, nameRes: Int, sizes: String) = Pack(
        id, nameRes,
        sizes.split(',').mapIndexed { i, s ->
            val (w, h) = s.split('x').map { it.toInt() }
            Tile("${id}_${(i + 1).toString().padStart(2, '0')}", w, h)
        }
    )

    val packs: List<Pack> = listOf(
        pack("p01", R.string.stk_pack_p01, "256x190,256x201,256x192,256x193,256x195,256x184,256x194,256x185,256x187,256x189,256x172,256x182,256x174,256x175,256x177,256x151,256x160,256x153,256x154,256x155,256x121,256x128,256x122,256x123,256x124"),
        pack("p02", R.string.stk_pack_p02, "256x229,241x256,235x256,220x256,223x256,256x235,249x256,256x254,218x256,239x256,239x256,226x256,247x256,238x256,188x256"),
        pack("p03", R.string.stk_pack_p03, "256x205,256x204,256x228,256x212,256x219,256x193,256x193,256x215,256x200,256x207,256x217,256x216,256x242,256x224,256x232,256x189,256x189,256x211,256x196,256x202"),
        pack("p04", R.string.stk_pack_p04, "256x205,256x251,256x204,256x212,256x215,256x205,256x251,256x204,256x212,256x215,256x186,256x227,256x185,256x192,256x195,256x205,256x251,256x204,256x212,256x215"),
        pack("p05", R.string.stk_pack_p05, "256x222,256x231,256x241,256x231,256x234,256x181,256x189,256x197,256x189,256x191,256x207,256x216,256x225,256x216,256x218,256x193,256x202,256x210,256x202,256x204"),
        pack("p06", R.string.stk_pack_p06, "256x212,256x216,256x226,256x217,256x221,256x203,256x207,256x217,256x208,256x211,256x201,256x205,256x215,256x206,256x210,256x200,256x204,256x214,256x205,256x209"),
        pack("p07", R.string.stk_pack_p07, "256x180,256x180,256x176,256x178,256x159,256x159,256x156,256x157,256x171,256x171,256x168,256x170,256x168,256x168,256x165,256x167"),
        pack("p08", R.string.stk_pack_p08, "256x203,256x214,256x219,256x217,256x214,256x202,256x213,256x218,256x216,256x213,256x191,256x201,256x206,256x204,256x201,256x199,256x210,256x214,256x213,256x209"),
        pack("p09", R.string.stk_pack_p09, "256x223,256x233,256x240,256x238,256x228,256x227,256x237,256x244,256x242,256x232,256x196,256x205,256x211,256x209,256x201,256x201,256x210,256x216,256x215,256x206"),
        pack("p10", R.string.stk_pack_p10, "256x228,256x253,256x239,256x235,256x243,256x175,256x194,256x183,256x180,256x186,256x183,256x203,256x192,256x188,256x195,256x202,256x224,256x211,256x208,256x215"),
        pack("p11", R.string.stk_pack_p11, "256x204,256x211,256x218,256x215,256x215,256x197,256x204,256x211,256x207,256x207,256x194,256x200,256x207,256x204,256x204,256x201,256x208,256x215,256x211,256x211"),
        pack("p12", R.string.stk_pack_p12, "256x215,256x219,256x216,256x213,256x216,256x207,256x211,256x209,256x206,256x209,256x201,256x205,256x203,256x200,256x203,256x205,256x210,256x207,256x204,256x207"),
        pack("p13", R.string.stk_pack_p13, "256x215,256x217,256x222,256x221,256x228,256x208,256x209,256x215,256x213,256x220,256x205,256x207,256x212,256x211,256x217,256x191,256x192,256x198,256x196,256x202")
    )

    val allTiles: List<Tile> = packs.flatMap { it.tiles }

    fun tile(id: String): Tile? = allTiles.firstOrNull { it.id == id }

    /** مواصفات الزر الجاهز لملصق: صورة بأطول ضلع [ADD_MAX_DP]، تأثير ضغط «تصغير»، وبلا إجراء حتى يختاره المستخدم. */
    private fun element(t: Tile): UiElement {
        val k = ADD_MAX_DP / maxOf(t.w, t.h)
        return UiElement(
            id = newId(),
            type = ElementTypes.IMAGE,
            layout = ElementLayout(0f, 0f, SizeSpec.fixed(Math.round(t.w * k).toFloat()), SizeSpec.fixed(Math.round(t.h * k).toFloat())),
            properties = mapOf(
                PropertyKeys.IMAGE_SRC to t.fileName,
                PropertyKeys.IMAGE_FIT to ImageFits.FIT,
                PropertyKeys.PRESS_EFFECT to PressEffects.SHRINK
            )
        )
    }

    private fun indexIn(pack: Pack, t: Tile) = pack.tiles.indexOf(t) + 1

    /** المكوّن الجاهز لكل ملصق، بمعرّف البلاطة (مثل p01_03). */
    private val byTileId: Map<String, ComponentPreset> = LinkedHashMap<String, ComponentPreset>().also { m ->
        for (pk in packs) for (t in pk.tiles) {
            m[t.id] = ComponentPreset("stk_${t.id}", R.string.sticker_label, PresetCategory.STICKERS, labelArg = indexIn(pk, t)) { _ -> listOf(element(t)) }
        }
    }

    val presets: List<ComponentPreset> = byTileId.values.toList()

    fun presetFor(t: Tile): ComponentPreset = byTileId.getValue(t.id)

    /** زر صورة فارغ: يختار المستخدم صورته من المعرض في «الخصائص»، وله تأثير ضغط جاهز. */
    val blankImageButton: ComponentPreset = ComponentPreset("stk_blank", R.string.stk_blank_button, PresetCategory.STICKERS) { _ ->
        listOf(
            UiElement(
                id = newId(),
                type = ElementTypes.IMAGE,
                layout = ElementLayout(0f, 0f, SizeSpec.fixed(120f), SizeSpec.fixed(120f)),
                properties = mapOf(
                    PropertyKeys.IMAGE_FIT to ImageFits.CROP,
                    PropertyKeys.CORNER_RADIUS to "16",
                    PropertyKeys.PRESS_EFFECT to PressEffects.SHRINK
                )
            )
        )
    }
}
