package com.arabicuieditor.data.model

/** نقطة ارتكاز الدوران والقلب كنسبة مئوية من حجم العنصر (50،50 = المركز، وهو الافتراضي). */
fun UiElement.pivotX(): Float = intProp(PropertyKeys.PIVOT_X).coerceIn(0, 100) / 100f

fun UiElement.pivotY(): Float = intProp(PropertyKeys.PIVOT_Y).coerceIn(0, 100) / 100f

object PivotPresets {
    /** (مفتاح الاسم، X%، Y%) بالترتيب الذي تُعرض به. */
    val all: List<Triple<Int, Int, Int>> = listOf(
        Triple(com.arabicuieditor.R.string.pivot_center, 50, 50),
        Triple(com.arabicuieditor.R.string.pivot_top_left, 0, 0),
        Triple(com.arabicuieditor.R.string.pivot_top, 50, 0),
        Triple(com.arabicuieditor.R.string.pivot_top_right, 100, 0),
        Triple(com.arabicuieditor.R.string.pivot_left, 0, 50),
        Triple(com.arabicuieditor.R.string.pivot_right, 100, 50),
        Triple(com.arabicuieditor.R.string.pivot_bottom_left, 0, 100),
        Triple(com.arabicuieditor.R.string.pivot_bottom, 50, 100),
        Triple(com.arabicuieditor.R.string.pivot_bottom_right, 100, 100)
    )
}
