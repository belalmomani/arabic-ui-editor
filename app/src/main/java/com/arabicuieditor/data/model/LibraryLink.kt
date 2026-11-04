package com.arabicuieditor.data.model

/**
 * ربط نسخة في المشروع بمكوّن في المكتبة الشخصية: خاصية إضافية "libSrc" على جذر النسخة
 * (بلا ترقية للمخطط). تُتيح «تحديث كل النسخ» عند تعديل المكوّن في المكتبة.
 */
object LibraryLink {
    const val KEY = "libSrc"

    fun linkedId(e: UiElement): String? = e.properties[KEY]?.takeIf { it.isNotEmpty() }

    fun tag(e: UiElement, componentId: String): UiElement = e.copy(properties = e.properties + (KEY to componentId))

    fun untag(e: UiElement): UiElement = if (KEY in e.properties) e.copy(properties = e.properties - KEY) else e

    /**
     * يستبدل كل نسخة مرتبطة بـ [componentId] بنسخة جديدة من [source] مع الإبقاء على معرّف النسخة
     * ومكانها (x, y) كي لا تنكسر قيود الإخوة. لا يدخل داخل نسخة تم استبدالها.
     */
    fun refresh(roots: List<UiElement>, componentId: String, source: UiElement): List<UiElement> =
        roots.map { e ->
            if (linkedId(e) == componentId) {
                val fresh = tag(ElementTree.reId(untag(source)), componentId)
                fresh.copy(id = e.id, layout = fresh.layout.copy(x = e.layout.x, y = e.layout.y))
            } else if (e.children.isEmpty()) e
            else e.copy(children = refresh(e.children, componentId, source))
        }

    fun count(roots: List<UiElement>, componentId: String): Int =
        ElementTree.flatten(roots).count { linkedId(it) == componentId }
}
