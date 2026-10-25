package com.arabicuieditor.ui.editor.canvas

import com.arabicuieditor.data.model.Aligns
import com.arabicuieditor.data.model.Arrangements
import com.arabicuieditor.data.model.Axis
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.Pins
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.SCREEN_TYPE
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.spec
import kotlin.math.max

/**
 * نتيجة حساب التخطيط لشاشة بحجم معيّن. كل الإطارات بإحداثيات الشاشة المطلقة (dp) كي يسهل
 * الرسم والاختيار، حتى للعناصر المتداخلة داخل حاويات.
 */
class LayoutResult(
    val width: Float,
    val height: Float,
    /** إطار كل عنصر (على كل الأعماق) بإحداثيات الشاشة. */
    val frames: Map<String, DesignRect>,
    /** ترتيب الرسم: الأب قبل أبنائه، والإخوة اللاحقون فوق السابقين. */
    val paintOrder: List<String>,
    /** معرّف أب كل عنصر؛ null يعني جذر الشاشة. */
    val parentOf: Map<String, String?>,
    /** العناصر التي موضعها (x, y) حر لأن تخطيط أبيها ABSOLUTE (قابلة للسحب مباشرة). */
    val freeIds: Set<String>,
    /** العناصر القادرة على احتواء أبناء. */
    val containers: Set<String>,
    /** منطقة محتوى كل أب (مفتاحها معرّف الأب، أو [LayoutEngine.ROOT] للشاشة). */
    val contents: Map<String, DesignRect>,
    /** تخطيط كل أب (مفتاحها معرّف الأب، أو [LayoutEngine.ROOT] للشاشة). */
    val layouts: Map<String, ChildLayout>
) {
    fun frameOf(id: String): DesignRect? = frames[id]

    /** تخطيط أب العنصر. */
    fun parentLayoutOf(id: String): ChildLayout = layouts[parentOf[id] ?: LayoutEngine.ROOT] ?: ChildLayout.ABSOLUTE

    /** منطقة محتوى أب العنصر (المتاح لأبعاده النسبية). */
    fun parentContentOf(id: String): DesignRect =
        contents[parentOf[id] ?: LayoutEngine.ROOT] ?: DesignRect(0f, 0f, width, height)
}

/**
 * محرّك التخطيط: يحوّل شجرة العناصر وخصائص التخطيط إلى إطارات فعلية لحجم شاشة معيّن
 * (هذا ما تعرضه المعاينة بأحجام مختلفة). نقي بلا أي اعتماد على Android أو Compose.
 *
 * القواعد:
 * - ABSOLUTE (حر): (x, y) نسبةً إلى الأب. عند اختلاف حجم المعاينة عن حجم التصميم يطبّق العنصر
 *   سلوك التثبيت pinH/pinV (من البداية، من النهاية، وسط، تمدّد، تناسبي).
 * - COLUMN/ROW: ترتيب متتابع مع الهوامش والمسافة والوزن والتوزيع والمحاذاة.
 * - BOX: أبناء متراكبون، كل منهم بمحاذاته داخل الصندوق.
 * - الأبعاد: ثابت، ملء الأب، حسب المحتوى، أو نسبة مئوية من المتاح.
 * - الهوامش تعمل في COLUMN/ROW/BOX وتُتجاهل في التخطيط الحر. المسافة الداخلية تُطبَّق على
 *   الحاويات المرنة وجذر الشاشة المرن، وتُتجاهل في التخطيط الحر.
 */
object LayoutEngine {
    const val ROOT = ""

    private val MIN = CanvasMath.MIN_ELEMENT_SIZE
    private val STACKED = setOf(ChildLayout.COLUMN, ChildLayout.ROW, ChildLayout.BOX)

    /** يحسب التخطيط عند [width]×[height]؛ الافتراضي هو حجم تصميم الشاشة نفسه. */
    fun layout(screen: UiScreen, width: Float = screen.width, height: Float = screen.height): LayoutResult {
        val sameAsDesign = width == screen.width && height == screen.height
        val base = if (sameAsDesign) null else Pass(screen, screen.width, screen.height, null).compute()
        return Pass(screen, width, height, base).compute()
    }

    // ---- أدوات للمحرر ----

    /** أعمق حاوية (الأعلى رسمًا) تحت النقطة، باستثناء [exclude]؛ null يعني جذر الشاشة. */
    fun containerAt(result: LayoutResult, x: Float, y: Float, exclude: Set<String> = emptySet()): String? {
        for (id in result.paintOrder.asReversed()) {
            if (id in exclude || id !in result.containers) continue
            val f = result.frames[id] ?: continue
            if (f.contains(x, y)) return id
        }
        return null
    }

    /**
     * ترتيب الإفلات بين [siblingIds] (الإخوة دون العنصر المسحوب): عدد الإخوة الذين يقع مركزهم قبل
     * النقطة على المحور الرئيسي.
     */
    fun dropIndex(result: LayoutResult, siblingIds: List<String>, vertical: Boolean, px: Float, py: Float): Int {
        var index = 0
        siblingIds.forEachIndexed { i, id ->
            val f = result.frames[id] ?: return@forEachIndexed
            val center = if (vertical) f.y + f.h / 2f else f.x + f.w / 2f
            val point = if (vertical) py else px
            if (point > center) index = i + 1
        }
        return index
    }

    /**
     * يطبّق سلوك التثبيت على محور واحد في التخطيط الحر. يعيد (الموضع، الحجم).
     * [parentBase]/[childBase] حجما الأب والعنصر عند حجم التصميم؛ null (أو ملء الأب) يعني بلا تثبيت.
     */
    fun pinAxis(
        pin: String,
        pos: Float,
        size: Float,
        parentSize: Float,
        parentBase: Float?,
        childBase: Float?,
        fill: Boolean
    ): Pair<Float, Float> {
        if (parentBase == null || childBase == null || fill) return pos to size
        return when (pin) {
            Pins.END -> {
                val gap = parentBase - (pos + childBase)
                (parentSize - gap - size) to size
            }

            Pins.CENTER -> {
                val offset = pos + childBase / 2f - parentBase / 2f
                (parentSize / 2f + offset - size / 2f) to size
            }

            Pins.STRETCH -> {
                val gap = parentBase - (pos + childBase)
                pos to max(MIN, parentSize - pos - gap)
            }

            Pins.SCALE -> {
                if (parentBase <= 0f) pos to size
                else {
                    val k = parentSize / parentBase
                    (pos * k) to max(MIN, childBase * k)
                }
            }

            else -> pos to size
        }
    }

    // ---- الحساب ----

    class Margins(val start: Float, val top: Float, val end: Float, val bottom: Float)

    class Props(private val type: String, private val map: Map<String, String>) {
        fun str(key: String): String = map[key] ?: PropertyRegistry.defaultOf(type, key)
        fun int(key: String): Int = str(key).toIntOrNull() ?: 0
    }

    private fun propsOf(e: UiElement) = Props(e.type, e.properties)

    private fun marginsOf(p: Props) = Margins(
        p.int(PropertyKeys.MARGIN_START).toFloat(),
        p.int(PropertyKeys.MARGIN_TOP).toFloat(),
        p.int(PropertyKeys.MARGIN_END).toFloat(),
        p.int(PropertyKeys.MARGIN_BOTTOM).toFloat()
    )

    private fun paddingOf(p: Props) = Margins(
        p.int(PropertyKeys.PADDING_START).toFloat(),
        p.int(PropertyKeys.PADDING_TOP).toFloat(),
        p.int(PropertyKeys.PADDING_END).toFloat(),
        p.int(PropertyKeys.PADDING_BOTTOM).toFloat()
    )

    /** حجم العنصر على محور حسب نمطه، علمًا أن [intrinsicSize] يُحسب فقط عند الحاجة. */
    private fun sizeOf(spec: SizeSpec, available: Float, intrinsicSize: () -> Float): Float {
        val raw = when (spec.mode) {
            SizeMode.FIXED -> spec.value
            SizeMode.MATCH_PARENT -> available
            SizeMode.FRACTION -> available * spec.value / 100f
            SizeMode.WRAP_CONTENT -> intrinsicSize()
        }
        return max(MIN, raw)
    }

    /** الحجم «التصريحي» لابن داخل أب حجمه حسب المحتوى (لا متاح معروف: ملء الأب يعامل كقيمته الاحتياطية). */
    private fun declared(child: UiElement, axis: Axis): Float {
        val s = child.layout.spec(axis)
        return when (s.mode) {
            SizeMode.FIXED -> max(MIN, s.value)
            SizeMode.WRAP_CONTENT -> intrinsic(child, axis)
            SizeMode.MATCH_PARENT -> max(MIN, s.value)
            SizeMode.FRACTION -> MIN
        }
    }

    /** حجم العنصر على محور عندما يكون «حسب المحتوى». الورقة: قيمتها الاحتياطية. الحاوية: من أبنائها. */
    private fun intrinsic(el: UiElement, axis: Axis): Float {
        val s = el.layout.spec(axis)
        val fallback = if (s.value > 0f) s.value else MIN * 4f
        if (el.children.isEmpty()) return max(MIN, fallback)

        val p = propsOf(el)
        val horizontal = axis == Axis.HORIZONTAL
        val result = when (el.childLayout) {
            ChildLayout.COLUMN, ChildLayout.ROW, ChildLayout.BOX -> {
                val pad = paddingOf(p)
                val padAlong = if (horizontal) pad.start + pad.end else pad.top + pad.bottom
                val sizes = el.children.map { c ->
                    val m = marginsOf(propsOf(c))
                    val marginAlong = if (horizontal) m.start + m.end else m.top + m.bottom
                    declared(c, axis) + marginAlong
                }
                val stacksAlongAxis = (el.childLayout == ChildLayout.COLUMN && !horizontal) ||
                    (el.childLayout == ChildLayout.ROW && horizontal)
                val content = if (stacksAlongAxis) {
                    sizes.fold(0f) { a, b -> a + b } + p.int(PropertyKeys.SPACING) * (sizes.size - 1)
                } else {
                    sizes.maxOrNull() ?: 0f
                }
                content + padAlong
            }

            else -> el.children.maxOf { c ->
                val offset = if (horizontal) c.layout.x else c.layout.y
                offset + declared(c, axis)
            }
        }
        return max(MIN, result)
    }

    private class Pass(
        val screen: UiScreen,
        val width: Float,
        val height: Float,
        val base: LayoutResult?
    ) {
        val frames = LinkedHashMap<String, DesignRect>()
        val order = ArrayList<String>()
        val parentOf = HashMap<String, String?>()
        val free = HashSet<String>()
        val containers = HashSet<String>()
        val contents = HashMap<String, DesignRect>()
        val layouts = HashMap<String, ChildLayout>()

        fun compute(): LayoutResult {
            val props = Props(SCREEN_TYPE, screen.properties)
            val frame = DesignRect(0f, 0f, width, height)
            val content = contentRect(screen.childLayout, props, frame)
            layoutChildren(ROOT, null, screen.childLayout, props, screen.elements, content, DesignRect(0f, 0f, screen.width, screen.height))
            return LayoutResult(width, height, frames, order, parentOf, free, containers, contents, layouts)
        }

        private fun contentRect(layout: ChildLayout, p: Props, frame: DesignRect): DesignRect {
            if (layout !in STACKED) return frame
            val pad = paddingOf(p)
            return DesignRect(
                frame.x + pad.start,
                frame.y + pad.top,
                max(0f, frame.w - pad.start - pad.end),
                max(0f, frame.h - pad.top - pad.bottom)
            )
        }

        private fun place(child: UiElement, parentId: String?, frame: DesignRect) {
            frames[child.id] = frame
            order += child.id
            parentOf[child.id] = parentId
            if (ElementTree.canContain(child.type)) containers += child.id
            if (child.children.isNotEmpty()) {
                val p = propsOf(child)
                val content = contentRect(child.childLayout, p, frame)
                val baseFrame = base?.frames?.get(child.id) ?: frame
                layoutChildren(child.id, child.id, child.childLayout, p, child.children, content, baseFrame)
            } else {
                layouts[child.id] = child.childLayout
                contents[child.id] = contentRect(child.childLayout, propsOf(child), frame)
            }
        }

        private fun layoutChildren(
            key: String,
            parentId: String?,
            layout: ChildLayout,
            props: Props,
            children: List<UiElement>,
            content: DesignRect,
            parentBase: DesignRect
        ) {
            layouts[key] = layout
            contents[key] = content
            when (layout) {
                ChildLayout.COLUMN -> flow(parentId, true, props, children, content)
                ChildLayout.ROW -> flow(parentId, false, props, children, content)
                ChildLayout.BOX -> box(parentId, props, children, content)
                else -> freeLayout(parentId, children, content, parentBase)
            }
        }

        // ---- تخطيط حر ----

        private fun freeLayout(parentId: String?, children: List<UiElement>, content: DesignRect, parentBase: DesignRect) {
            for (child in children) {
                free += child.id
                val l = child.layout
                val p = propsOf(child)
                val fillW = l.width.mode == SizeMode.MATCH_PARENT
                val fillH = l.height.mode == SizeMode.MATCH_PARENT
                val w = sizeOf(l.width, content.w - (if (fillW) l.x else 0f)) { intrinsic(child, Axis.HORIZONTAL) }
                val h = sizeOf(l.height, content.h - (if (fillH) l.y else 0f)) { intrinsic(child, Axis.VERTICAL) }
                val childBase = base?.frames?.get(child.id)
                val (left, width) = pinAxis(
                    p.str(PropertyKeys.PIN_H), l.x, w, content.w,
                    if (childBase == null) null else parentBase.w, childBase?.w, fillW
                )
                val (top, height) = pinAxis(
                    p.str(PropertyKeys.PIN_V), l.y, h, content.h,
                    if (childBase == null) null else parentBase.h, childBase?.h, fillH
                )
                place(child, parentId, DesignRect(content.x + left, content.y + top, width, height))
            }
        }

        // ---- صف وعمود ----

        private class Item(val el: UiElement, val m: Margins, val weight: Int) {
            var main = 0f
            var cross = 0f
        }

        private fun flow(parentId: String?, vertical: Boolean, props: Props, children: List<UiElement>, content: DesignRect) {
            if (children.isEmpty()) return
            val mainAxis = if (vertical) Axis.VERTICAL else Axis.HORIZONTAL
            val crossAxis = if (vertical) Axis.HORIZONTAL else Axis.VERTICAL
            val mainContent = if (vertical) content.h else content.w
            val crossContent = if (vertical) content.w else content.h
            val spacing = props.int(PropertyKeys.SPACING).toFloat()

            val items = children.map { Item(it, marginsOf(propsOf(it)), propsOf(it).int(PropertyKeys.WEIGHT)) }
            fun mainStart(i: Item) = if (vertical) i.m.top else i.m.start
            fun mainEnd(i: Item) = if (vertical) i.m.bottom else i.m.end
            fun crossStart(i: Item) = if (vertical) i.m.start else i.m.top
            fun crossEnd(i: Item) = if (vertical) i.m.end else i.m.bottom

            for (item in items) {
                val l = item.el.layout
                val mainSpec = if (vertical) l.height else l.width
                val crossSpec = if (vertical) l.width else l.height
                item.cross = sizeOf(crossSpec, crossContent - crossStart(item) - crossEnd(item)) { intrinsic(item.el, crossAxis) }
                if (item.weight == 0) {
                    item.main = sizeOf(mainSpec, mainContent - mainStart(item) - mainEnd(item)) { intrinsic(item.el, mainAxis) }
                }
            }

            val gaps = spacing * (items.size - 1)
            val fixedTotal = items.fold(0f) { acc, i ->
                acc + mainStart(i) + mainEnd(i) + (if (i.weight == 0) i.main else 0f)
            } + gaps
            val totalWeight = items.fold(0) { acc, i -> acc + i.weight }
            if (totalWeight > 0) {
                val remaining = max(0f, mainContent - fixedTotal)
                for (item in items) {
                    if (item.weight > 0) item.main = max(MIN, remaining * item.weight / totalWeight)
                }
            }

            val used = items.fold(0f) { acc, i -> acc + mainStart(i) + i.main + mainEnd(i) } + gaps
            val freeSpace = max(0f, mainContent - used)
            var offset = 0f
            var gap = spacing
            val n = items.size
            when (props.str(PropertyKeys.ARRANGEMENT)) {
                Arrangements.CENTER -> offset = freeSpace / 2f
                Arrangements.END -> offset = freeSpace
                Arrangements.BETWEEN -> if (n > 1) gap = spacing + freeSpace / (n - 1)
                Arrangements.AROUND -> {
                    val s = freeSpace / n
                    gap = spacing + s
                    offset = s / 2f
                }

                Arrangements.EVENLY -> {
                    val s = freeSpace / (n + 1)
                    gap = spacing + s
                    offset = s
                }
            }

            val crossKey = if (vertical) PropertyKeys.CHILD_ALIGN_H else PropertyKeys.CHILD_ALIGN_V
            val selfKey = if (vertical) PropertyKeys.SELF_ALIGN_H else PropertyKeys.SELF_ALIGN_V
            var cursor = offset
            for (item in items) {
                val mainPos = cursor + mainStart(item)
                cursor += mainStart(item) + item.main + mainEnd(item) + gap
                val own = propsOf(item.el).str(selfKey)
                val align = if (own == Aligns.INHERIT) props.str(crossKey) else own
                val crossPos = alignedOffset(align, crossContent, item.cross, crossStart(item), crossEnd(item))
                val frame = if (vertical) {
                    DesignRect(content.x + crossPos, content.y + mainPos, item.cross, item.main)
                } else {
                    DesignRect(content.x + mainPos, content.y + crossPos, item.main, item.cross)
                }
                place(item.el, parentId, frame)
            }
        }

        // ---- صندوق ----

        private fun box(parentId: String?, props: Props, children: List<UiElement>, content: DesignRect) {
            for (child in children) {
                val p = propsOf(child)
                val m = marginsOf(p)
                val l = child.layout
                val w = sizeOf(l.width, content.w - m.start - m.end) { intrinsic(child, Axis.HORIZONTAL) }
                val h = sizeOf(l.height, content.h - m.top - m.bottom) { intrinsic(child, Axis.VERTICAL) }
                val ownH = p.str(PropertyKeys.SELF_ALIGN_H)
                val ownV = p.str(PropertyKeys.SELF_ALIGN_V)
                val alignH = if (ownH == Aligns.INHERIT) props.str(PropertyKeys.CHILD_ALIGN_H) else ownH
                val alignV = if (ownV == Aligns.INHERIT) props.str(PropertyKeys.CHILD_ALIGN_V) else ownV
                val x = alignedOffset(alignH, content.w, w, m.start, m.end)
                val y = alignedOffset(alignV, content.h, h, m.top, m.bottom)
                place(child, parentId, DesignRect(content.x + x, content.y + y, w, h))
            }
        }

        /** موضع عنصر حجمه [size] داخل مساحة [container] مع هوامش، بمحاذاة بداية/وسط/نهاية. */
        private fun alignedOffset(align: String, container: Float, size: Float, startMargin: Float, endMargin: Float): Float {
            val outer = size + startMargin + endMargin
            return when (align) {
                Aligns.CENTER -> (container - outer) / 2f + startMargin
                Aligns.END -> container - outer + startMargin
                else -> startMargin
            }
        }
    }
}
