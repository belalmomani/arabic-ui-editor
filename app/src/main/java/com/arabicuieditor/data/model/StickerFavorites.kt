package com.arabicuieditor.data.model

/** مفضلة الملصقات: قائمة معرّفات بلاطات (مثل p01_03) بترتيب الإضافة، تُحفظ نصًا مفصولًا بفواصل. */
object StickerFavorites {
    fun decode(raw: String?): List<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { Stickers.tile(it) != null }.distinct()

    fun encode(ids: List<String>): String = ids.joinToString(",")

    /** يضيف المعرّف في النهاية إن لم يكن موجودًا، وإلا يحذفه. */
    fun toggle(ids: List<String>, id: String): List<String> =
        if (id in ids) ids - id else if (Stickers.tile(id) != null) ids + id else ids
}
