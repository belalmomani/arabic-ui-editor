package com.arabicuieditor.data.model

import kotlin.math.roundToInt

enum class Axis { HORIZONTAL, VERTICAL }

/**
 * عمليات بنيوية نقية على شجرة العناصر لبناء التخطيطات: نقل بين الحاويات، تغليف، فك حاوية،
 * وتغيير نمط الأبعاد. كلها تعيد قائمة جديدة (أو null إن كانت العملية غير صالحة).
 */
object LayoutEditing {

    /** الخصائص التي تصف «مكان العنصر في أبيه»؛ تنتقل من العنصر إلى الحاوية عند التغليف. */
    private val SLOT_KEYS = listOf(
        PropertyKeys.WEIGHT, PropertyKeys.SELF_ALIGN_H, PropertyKeys.SELF_ALIGN_V,
        PropertyKeys.PIN_H, PropertyKeys.PIN_V,
        PropertyKeys.MARGIN_START, PropertyKeys.MARGIN_END, PropertyKeys.MARGIN_TOP, PropertyKeys.MARGIN_BOTTOM
    )

    /**
     * ينقل [id] إلى أبناء [newParentId] (null = جذر الشاشة) عند [index] (null = النهاية).
     * [localX]/[localY] إحداثيات العنصر داخل الأب الجديد (تُستعمل في التخطيط الحر).
     * يعيد null إن كان النقل غير صالح: العنصر غير موجود، أو الوجهة العنصر نفسه أو أحد أحفاده،
     * أو الوجهة لا تقبل أبناء.
     * [index] يُفسَّر بعد إزالة العنصر من مكانه القديم.
     */
    fun moveTo(
        roots: List<UiElement>,
        id: String,
        newParentId: String?,
        index: Int?,
        localX: Float? = null,
        localY: Float? = null
    ): List<UiElement>? {
        val element = ElementTree.find(roots, id) ?: return null
        if (newParentId != null) {
            if (newParentId == id || newParentId in ElementTree.descendantIds(element)) return null
            val parent = ElementTree.find(roots, newParentId) ?: return null
            if (!ElementTree.canContain(parent.type)) return null
        }
        val moved = element.copy(
            layout = element.layout.copy(
                x = localX ?: element.layout.x,
                y = localY ?: element.layout.y
            )
        )
        val without = ElementTree.remove(roots, id)
        return ElementTree.insert(without, newParentId, index, moved)
    }

    /**
     * يغلّف [id] بحاوية جديدة بالتخطيط المطلوب تأخذ مكانه ومساحته. تنتقل إليها خصائص «المكان»
     * (الوزن، المحاذاة الذاتية، التثبيت، الهوامش)، ويبقى الابن عند (0, 0) داخلها.
     * [frameW]/[frameH] الحجم الحالي للعنصر (احتياطي للحاوية ذات الحجم حسب المحتوى).
     */
    fun wrap(
        roots: List<UiElement>,
        id: String,
        layout: ChildLayout,
        containerId: String,
        containerName: String,
        frameW: Float,
        frameH: Float
    ): List<UiElement>? {
        val element = ElementTree.find(roots, id) ?: return null
        val slot = element.properties.filterKeys { it in SLOT_KEYS }
        val child = element.copy(
            layout = element.layout.copy(x = 0f, y = 0f),
            properties = element.properties - SLOT_KEYS.toSet()
        )
        val container = UiElement(
            id = containerId,
            type = ElementTypes.CONTAINER,
            layout = ElementLayout(
                x = element.layout.x,
                y = element.layout.y,
                width = SizeSpec(SizeMode.WRAP_CONTENT, frameW),
                height = SizeSpec(SizeMode.WRAP_CONTENT, frameH)
            ),
            childLayout = layout,
            properties = slot + (PropertyKeys.NAME to containerName),
            children = listOf(child)
        )
        return ElementTree.update(roots, id) { container }
    }

    /**
     * يفك الحاوية: أبناؤها يحلّون محلها في قائمة أبيها بالترتيب نفسه.
     * [parentIsAbsolute] هل أب الحاوية بتخطيط حر؛ إن كان وحاويتُها حرة أيضًا تُزاح إحداثيات الأبناء
     * بموضع الحاوية كي يبقوا في مكانهم.
     */
    fun unwrap(roots: List<UiElement>, id: String, parentIsAbsolute: Boolean): List<UiElement>? {
        val container = ElementTree.find(roots, id) ?: return null
        if (container.children.isEmpty()) return null
        val freed = container.children.map { c ->
            if (parentIsAbsolute && container.childLayout == ChildLayout.ABSOLUTE) {
                c.copy(layout = c.layout.copy(x = c.layout.x + container.layout.x, y = c.layout.y + container.layout.y))
            } else c
        }
        return ElementTree.transformSiblings(roots, id) { siblings ->
            siblings.flatMap { if (it.id == id) freed else listOf(it) }
        }
    }

    /**
     * يغيّر نمط بُعد عنصر مع حفظ قيمة منطقية: [frameSize] الحجم الحالي المرسوم، و[available] المتاح داخل الأب.
     * - ثابت: يُثبَّت على الحجم الحالي. حسب المحتوى وملء الأب: يُحفظ الحجم الحالي كاحتياط.
     * - نسبة: النسبة الحالية إلى المتاح، بين 1 و100.
     */
    fun withSizeMode(element: UiElement, axis: Axis, mode: SizeMode, frameSize: Float, available: Float): UiElement {
        val size = frameSize.roundToInt().toFloat()
        val spec = when (mode) {
            SizeMode.FIXED -> SizeSpec.fixed(size)
            SizeMode.MATCH_PARENT -> SizeSpec(SizeMode.MATCH_PARENT, size)
            SizeMode.WRAP_CONTENT -> SizeSpec(SizeMode.WRAP_CONTENT, size)
            SizeMode.FRACTION -> {
                val percent = if (available > 0f) (frameSize / available * 100f).roundToInt() else 100
                SizeSpec(SizeMode.FRACTION, percent.coerceIn(1, 100).toFloat())
            }
        }
        return element.copy(layout = element.layout.withSpec(axis, spec))
    }

    /** يضبط قيمة النسبة المئوية (1..100) لعنصر بنمط نسبة؛ لا أثر لغيره. */
    fun withPercent(element: UiElement, axis: Axis, percent: Int): UiElement {
        val current = element.layout.spec(axis)
        if (current.mode != SizeMode.FRACTION) return element
        return element.copy(layout = element.layout.withSpec(axis, current.copy(value = percent.coerceIn(1, 100).toFloat())))
    }
}

fun ElementLayout.spec(axis: Axis): SizeSpec = if (axis == Axis.HORIZONTAL) width else height

fun ElementLayout.withSpec(axis: Axis, spec: SizeSpec): ElementLayout =
    if (axis == Axis.HORIZONTAL) copy(width = spec) else copy(height = spec)
