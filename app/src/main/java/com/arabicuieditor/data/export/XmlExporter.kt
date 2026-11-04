package com.arabicuieditor.data.export

import com.arabicuieditor.data.model.Aligns
import com.arabicuieditor.data.model.Arrangements
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ColorUtil
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.FontWeights
import com.arabicuieditor.data.model.IconCatalog
import com.arabicuieditor.data.model.ImageFits
import com.arabicuieditor.data.model.ImageNames
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.TextAligns
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.data.model.resolved
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.LayoutResult

/**
 * يولّد ملفات Android XML (res/layout + res/drawable للخلفيات المستديرة) من المشروع.
 * الناتج نص واحد مقسَّم بتعليقات «===== المسار =====» لكل ملف، لتنسخ كل جزء إلى ملفه.
 * دالة نقية بلا اعتماد على أندرويد.
 *
 * ملاحظات: الشاشة تُرسم يسار-يمين (layoutDirection=ltr على الجذر)؛ التخطيط الحر والقيود تُصدَّر بمواضع
 * ثابتة داخل FrameLayout؛ التنقل والضغطة المطوّلة تظهر كتعليقات فقط؛ المسافة بين العناصر تُضاف كهامش.
 */
object XmlExporter {

    fun layoutName(index: Int) = "screen_${index + 1}"

    fun export(project: Project, onlyScreenId: String? = null): String {
        val screens = if (onlyScreenId == null) project.screens else project.screens.filter { it.id == onlyScreenId }
        return Gen(project).generate(screens)
    }

    private class Gen(val project: Project) {
        val ds = project.designSystem
        val out = StringBuilder()
        val drawables = ArrayList<Pair<String, String>>()
        var usesCardView = false
        var drawableCounter = 0
        var depth = 0
        lateinit var frames: LayoutResult
        val screenIndex = project.screens.mapIndexed { i, s -> s.id to i }.toMap()

        fun generate(screens: List<UiScreen>): String {
            val layouts = ArrayList<Pair<String, String>>()
            for (s in screens) {
                out.setLength(0)
                drawables.clear()
                depth = 0
                frames = LayoutEngine.layout(s)
                val idx = screenIndex[s.id] ?: 0
                screenRoot(s)
                layouts += "res/layout/${layoutName(idx)}.xml  (${s.name.replace('\n', ' ')})" to out.toString()
                drawables.forEach { layouts += "res/drawable/${it.first}.xml" to it.second }
            }
            return buildString {
                append("<!-- تصدير XML من «محرر الواجهات العربي» — مشروع: ${project.name.replace('\n', ' ')} -->\n")
                append("<!-- انسخ كل جزء إلى الملف المذكور في سطر الفاصل. -->\n")
                if (usesCardView) append("<!-- يستعمل CardView: أضف implementation(\"androidx.cardview:cardview:1.0.0\") -->\n")
                for ((path, body) in layouts) {
                    append("\n<!-- ===== $path ===== -->\n")
                    append(body)
                }
            }
        }

        // ---- أدوات ----

        fun ind() = "    ".repeat(depth)
        fun w(s: String) { out.append(ind()).append(s).append('\n') }
        fun hex(argb: Int) = String.format("#%08X", argb)
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "\\'").replace("\n", "\\n")
        fun num(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
        fun UiElement.i(key: String) = prop(key).toIntOrNull() ?: 0
        fun UiElement.color(key: String) = hex(ColorUtil.parse(prop(key)) ?: 0)
        fun isTransparent(c: String) = (ColorUtil.parse(c) ?: 0) ushr 24 == 0

        fun gravityH(a: String) = when (a) { Aligns.CENTER -> "center_horizontal"; Aligns.END -> "end"; else -> "start" }
        fun gravityV(a: String) = when (a) { Aligns.CENTER -> "center_vertical"; Aligns.END -> "bottom"; else -> "top" }
        fun arrangeGravity(a: String, vertical: Boolean) = when (a) {
            Arrangements.CENTER -> if (vertical) "center_vertical" else "center_horizontal"
            Arrangements.END -> if (vertical) "bottom" else "end"
            else -> if (vertical) "top" else "start"
        }

        // ---- الجذر ----

        fun screenRoot(s: UiScreen) {
            val bg = Tokens.resolveColor(Tokens.color(Tokens.BACKGROUND), ds) ?: 0xFFFFFFFF.toInt()
            val pad = listOf(PropertyKeys.PADDING_START, PropertyKeys.PADDING_TOP, PropertyKeys.PADDING_END, PropertyKeys.PADDING_BOTTOM)
                .map { s.properties[it]?.toIntOrNull() ?: 0 }
            val extra = ArrayList<String>()
            extra += "android:layout_width=\"match_parent\""
            extra += "android:layout_height=\"match_parent\""
            extra += "android:layoutDirection=\"ltr\""
            extra += "android:background=\"${hex(bg)}\""
            if (pad.any { it > 0 }) extra += "android:paddingStart=\"${pad[0]}dp\" android:paddingTop=\"${pad[1]}dp\" android:paddingEnd=\"${pad[2]}dp\" android:paddingBottom=\"${pad[3]}dp\""
            val tag = containerTag(s.childLayout)
            val g = groupGravity(s.childLayout, s.properties)
            if (g != null) extra += "android:gravity=\"$g\""
            if (tag == "LinearLayout") extra += "android:orientation=\"${if (s.childLayout == ChildLayout.ROW) "horizontal" else "vertical"}\""
            out.append("<$tag xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
            depth = 1
            extra.forEach { w(it) }
            out.setLength(out.length - 1)
            out.append(">\n")
            children(s.elements, s.childLayout, s.properties, null)
            depth = 0
            out.append("</$tag>\n")
        }

        fun containerTag(l: ChildLayout) = when (l) {
            ChildLayout.COLUMN, ChildLayout.ROW -> "LinearLayout"
            else -> "FrameLayout"
        }

        fun groupGravity(l: ChildLayout, p: Map<String, String>): String? {
            val ah = p[PropertyKeys.CHILD_ALIGN_H] ?: Aligns.START
            val av = p[PropertyKeys.CHILD_ALIGN_V] ?: Aligns.START
            val ar = p[PropertyKeys.ARRANGEMENT] ?: Arrangements.START
            return when (l) {
                ChildLayout.COLUMN -> "${gravityH(ah)}|${arrangeGravity(ar, true)}"
                ChildLayout.ROW -> "${arrangeGravity(ar, false)}|${gravityV(av)}"
                else -> null
            }
        }

        // ---- الأبناء ----

        fun children(list: List<UiElement>, layout: ChildLayout, parentProps: Map<String, String>, ownerId: String?) {
            val spacing = parentProps[PropertyKeys.SPACING]?.toIntOrNull() ?: 0
            if (layout == ChildLayout.ABSOLUTE) {
                // التخطيط الحر قد يحوي ترتيبًا بالأعماق: العناصر كما هي بالترتيب.
            }
            list.forEachIndexed { idx, raw ->
                element(raw.resolved(ds), layout, parentProps, ownerId, if (idx > 0) spacing else 0)
            }
        }

        fun element(e: UiElement, parent: ChildLayout, parentProps: Map<String, String>, ownerId: String?, gap: Int) {
            val label = e.prop(PropertyKeys.NAME).replace('\n', ' ').trim()
            if (e.prop(PropertyKeys.VISIBLE) == "false") {
                w("<!-- ${label.ifEmpty { e.type }} (مخفي في التصميم، لم يُصدَّر) -->")
                return
            }
            if (label.isNotEmpty()) w("<!-- ${label.replace("--", "-")} -->")
            actionComment(e)
            val attrs = ArrayList<String>()
            sizeAttrs(e, parent, ownerId, attrs)
            positionAttrs(e, parent, parentProps, ownerId, gap, attrs)
            val opacity = e.i(PropertyKeys.OPACITY)
            if (opacity in 0..99) attrs += "android:alpha=\"${opacity / 100.0}\""
            val rot = e.i(PropertyKeys.ROTATION)
            if (rot != 0) attrs += "android:rotation=\"$rot\""

            when (e.type) {
                ElementTypes.TEXT -> leaf("TextView", e, attrs, background = false) { textAttrs(e, it, e.prop(PropertyKeys.TEXT), false) }
                ElementTypes.BUTTON -> leaf("Button", e, attrs, background = true) {
                    it += "android:textAllCaps=\"false\""
                    textAttrs(e, it, e.prop(PropertyKeys.TEXT), true)
                }
                ElementTypes.INPUT -> leaf("EditText", e, attrs, background = true) {
                    it += "android:hint=\"${esc(e.prop(PropertyKeys.HINT))}\""
                    it += "android:textColorHint=\"${e.color(PropertyKeys.TEXT_COLOR)}\""
                    it += "android:inputType=\"text\""
                    textAttrs(e, it, "", true)
                }
                ElementTypes.IMAGE -> image(e, attrs)
                ElementTypes.DIVIDER -> {
                    attrs += "android:background=\"${e.color(PropertyKeys.BG_COLOR)}\""
                    tag("View", attrs, null)
                }
                ElementTypes.SPACER -> tag("View", attrs, null)
                ElementTypes.LIST -> list(e, attrs)
                ElementTypes.CARD -> card(e, attrs)
                else -> container(e, attrs)
            }
        }

        fun actionComment(e: UiElement) {
            fun describe(a: ClickAction): String? = when (a) {
                ClickAction.None -> null
                is ClickAction.Script -> "سكربت JS (لا يُصدَّر؛ اكتب المنطق في الكود)"
                ClickAction.Back -> "رجوع"
                is ClickAction.Navigate -> screenIndex[a.screenId]?.let { "انتقال إلى ${layoutName(it)}" } ?: "انتقال إلى شاشة محذوفة"
            }
            describe(Interactions.clickAction(e))?.let { w("<!-- عند النقر: $it (اربطه بـ setOnClickListener) -->") }
            describe(Interactions.actionFor(e, Interactions.TRIGGER_LONG_PRESS))?.let { w("<!-- عند الضغط المطوّل: $it -->") }
        }

        // ---- الأحجام والمواضع ----

        fun sizeAttrs(e: UiElement, parent: ChildLayout, ownerId: String?, a: MutableList<String>) {
            val f = frames.frames[e.id]
            val weight = e.i(PropertyKeys.WEIGHT)
            fun one(spec: com.arabicuieditor.data.model.SizeSpec, frameSize: Float?, horizontal: Boolean): String {
                val isMain = (parent == ChildLayout.ROW && horizontal) || (parent == ChildLayout.COLUMN && !horizontal)
                if (weight > 0 && isMain) return "0dp"
                if (parent == ChildLayout.CONSTRAINT && frameSize != null) return "${num(frameSize)}dp"
                return when (spec.mode) {
                    SizeMode.FIXED -> "${num(spec.value)}dp"
                    SizeMode.MATCH_PARENT -> "match_parent"
                    SizeMode.FRACTION -> if (frameSize != null) "${num(frameSize)}dp" else "match_parent"
                    SizeMode.WRAP_CONTENT -> if (e.type == ElementTypes.TEXT || e.type == ElementTypes.BUTTON ||
                        (e.children.isNotEmpty())) "wrap_content" else "${num(spec.value)}dp"
                }
            }
            a += "android:layout_width=\"${one(e.layout.width, f?.w, true)}\""
            a += "android:layout_height=\"${one(e.layout.height, f?.h, false)}\""
            if (weight > 0 && (parent == ChildLayout.ROW || parent == ChildLayout.COLUMN)) a += "android:layout_weight=\"$weight\""
        }

        fun positionAttrs(e: UiElement, parent: ChildLayout, pp: Map<String, String>, ownerId: String?, gap: Int, a: MutableList<String>) {
            var ms = e.i(PropertyKeys.MARGIN_START); var mt = e.i(PropertyKeys.MARGIN_TOP)
            val me = e.i(PropertyKeys.MARGIN_END); val mb = e.i(PropertyKeys.MARGIN_BOTTOM)
            when (parent) {
                ChildLayout.ABSOLUTE, ChildLayout.CONSTRAINT -> {
                    val f = frames.frames[e.id]
                    val c = frames.contents[ownerId ?: LayoutEngine.ROOT]
                    val x = if (f != null && c != null && parent == ChildLayout.CONSTRAINT) f.x - c.x else e.layout.x
                    val y = if (f != null && c != null && parent == ChildLayout.CONSTRAINT) f.y - c.y else e.layout.y
                    a += "android:layout_marginStart=\"${num(x)}dp\""
                    a += "android:layout_marginTop=\"${num(y)}dp\""
                    a += "android:layout_gravity=\"top|start\""
                    return
                }
                ChildLayout.COLUMN -> {
                    mt += gap
                    val s = e.prop(PropertyKeys.SELF_ALIGN_H)
                    if (s != Aligns.INHERIT) a += "android:layout_gravity=\"${gravityH(s)}\""
                }
                ChildLayout.ROW -> {
                    ms += gap
                    val s = e.prop(PropertyKeys.SELF_ALIGN_V)
                    if (s != Aligns.INHERIT) a += "android:layout_gravity=\"${gravityV(s)}\""
                }
                ChildLayout.BOX -> {
                    val sh = e.prop(PropertyKeys.SELF_ALIGN_H).let { if (it == Aligns.INHERIT) pp[PropertyKeys.CHILD_ALIGN_H] ?: Aligns.START else it }
                    val sv = e.prop(PropertyKeys.SELF_ALIGN_V).let { if (it == Aligns.INHERIT) pp[PropertyKeys.CHILD_ALIGN_V] ?: Aligns.START else it }
                    a += "android:layout_gravity=\"${gravityH(sh)}|${gravityV(sv)}\""
                }
            }
            if (ms > 0) a += "android:layout_marginStart=\"${ms}dp\""
            if (mt > 0) a += "android:layout_marginTop=\"${mt}dp\""
            if (me > 0) a += "android:layout_marginEnd=\"${me}dp\""
            if (mb > 0) a += "android:layout_marginBottom=\"${mb}dp\""
        }

        // ---- الخلفية ----

        /** يعيد سمة الخلفية (لون مباشر أو drawable مستدير/بحد) أو null. */
        fun background(e: UiElement): String? {
            val bg = e.prop(PropertyKeys.BG_COLOR)
            val radius = e.i(PropertyKeys.CORNER_RADIUS)
            val bw = e.i(PropertyKeys.BORDER_WIDTH)
            val hasBg = bg.isNotEmpty() && !isTransparent(bg)
            if (radius <= 0 && bw <= 0) return if (hasBg) "android:background=\"${e.color(PropertyKeys.BG_COLOR)}\"" else null
            if (!hasBg && bw <= 0) return null
            val name = "bg_${++drawableCounter}"
            val body = buildString {
                append("<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" android:shape=\"rectangle\">\n")
                if (hasBg) append("    <solid android:color=\"${e.color(PropertyKeys.BG_COLOR)}\" />\n")
                if (radius > 0) append("    <corners android:radius=\"${radius}dp\" />\n")
                if (bw > 0) append("    <stroke android:width=\"${bw}dp\" android:color=\"${e.color(PropertyKeys.BORDER_COLOR)}\" />\n")
                append("</shape>\n")
            }
            drawables += name to body
            return "android:background=\"@drawable/$name\""
        }

        fun padAttr(e: UiElement): String? {
            val p = listOf(PropertyKeys.PADDING_START, PropertyKeys.PADDING_TOP, PropertyKeys.PADDING_END, PropertyKeys.PADDING_BOTTOM).map { e.i(it) }
            return if (p.any { it > 0 }) "android:paddingStart=\"${p[0]}dp\" android:paddingTop=\"${p[1]}dp\" android:paddingEnd=\"${p[2]}dp\" android:paddingBottom=\"${p[3]}dp\"" else null
        }

        // ---- الأنواع ----

        fun tag(name: String, rawAttrs: List<String>, body: (() -> Unit)?) {
            // XML لا يقبل سمة مكررة: تُحذف التكرارات (الأولى تغلب) حسب اسم أول سمة في كل سطر.
            val seen = HashSet<String>()
            val attrs = rawAttrs.filter { a -> seen.add(a.substringBefore('=')) }
            w("<$name")
            depth++
            attrs.forEachIndexed { i, a -> w(if (body == null && i == attrs.lastIndex) "$a />" else a) }
            depth--
            if (body != null) {
                out.setLength(out.length - 1)
                out.append(">\n")
                depth++
                body()
                depth--
                w("</$name>")
            }
        }

        fun leaf(name: String, e: UiElement, attrs: MutableList<String>, background: Boolean, extra: (MutableList<String>) -> Unit) {
            if (background) background(e)?.let { attrs += it }
            else if (e.prop(PropertyKeys.BG_COLOR).let { it.isNotEmpty() && !isTransparent(it) } || e.i(PropertyKeys.BORDER_WIDTH) > 0 || e.i(PropertyKeys.CORNER_RADIUS) > 0)
                background(e)?.let { attrs += it }
            padAttr(e)?.let { attrs += it }
            extra(attrs)
            tag(name, attrs, null)
        }

        fun textAttrs(e: UiElement, a: MutableList<String>, text: String, centerV: Boolean) {
            if (text.isNotEmpty()) a += "android:text=\"${esc(text)}\""
            a += "android:textColor=\"${e.color(PropertyKeys.TEXT_COLOR)}\""
            a += "android:textSize=\"${e.i(PropertyKeys.FONT_SIZE)}sp\""
            if (e.prop(PropertyKeys.FONT_WEIGHT) == FontWeights.BOLD) a += "android:textStyle=\"bold\""
            val h = when (e.prop(PropertyKeys.TEXT_ALIGN)) { TextAligns.CENTER -> "center_horizontal"; TextAligns.END -> "end"; else -> "start" }
            a += "android:gravity=\"$h${if (centerV) "|center_vertical" else ""}\""
        }

        fun image(e: UiElement, attrs: MutableList<String>) {
            background(e)?.let { attrs += it }
            val icon = e.prop(PropertyKeys.ICON)
            val src = e.prop(PropertyKeys.IMAGE_SRC)
            if (IconCatalog.isValid(icon)) {
                val res = "ic_" + icon.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
                w("<!-- أيقونة $icon: أضف Vector Asset (Material) باسم $res -->")
                attrs += "android:src=\"@drawable/$res\""
                attrs += "android:tint=\"${e.color(PropertyKeys.ICON_COLOR)}\""
                attrs += "android:padding=\"${(minOf(e.layout.width.value, e.layout.height.value) * 0.125f).toInt()}dp\""
            } else if (src.isNotEmpty() && ImageNames.isValid(src)) {
                w("<!-- انسخ $src إلى res/drawable/ -->")
                attrs += "android:src=\"@drawable/${ImageNames.drawableName(src)}\""
                attrs += "android:scaleType=\"${when (e.prop(PropertyKeys.IMAGE_FIT)) { ImageFits.FIT -> "fitCenter"; ImageFits.FILL -> "fitXY"; else -> "centerCrop" }}\""
                val z = e.i(PropertyKeys.IMAGE_ZOOM)
                if (z > 100) { attrs += "android:scaleX=\"${z / 100.0}\""; attrs += "android:scaleY=\"${z / 100.0}\"" }
            } else {
                w("<!-- TODO: ضع صورتك هنا (android:src) -->")
            }
            tag("ImageView", attrs, null)
        }

        fun list(e: UiElement, attrs: MutableList<String>) {
            background(e)?.let { attrs += it }
            padAttr(e)?.let { attrs += it }
            attrs += "android:orientation=\"vertical\""
            val items = e.prop(PropertyKeys.ITEMS).split('\n').filter { it.isNotEmpty() }
            val h = e.i(PropertyKeys.ITEM_HEIGHT)
            tag("LinearLayout", attrs) {
                items.forEach { item ->
                    val a = arrayListOf(
                        "android:layout_width=\"match_parent\"",
                        "android:layout_height=\"${h}dp\""
                    )
                    textAttrs(e, a, item, true)
                    tag("TextView", a, null)
                }
            }
        }

        fun container(e: UiElement, attrs: MutableList<String>) {
            background(e)?.let { attrs += it }
            padAttr(e)?.let { attrs += it }
            if (e.childLayout == ChildLayout.COLUMN || e.childLayout == ChildLayout.ROW) {
                attrs += "android:orientation=\"${if (e.childLayout == ChildLayout.ROW) "horizontal" else "vertical"}\""
                groupGravity(e.childLayout, e.properties)?.let { attrs += "android:gravity=\"$it\"" }
            }
            tag(containerTag(e.childLayout), attrs) { children(e.children, e.childLayout, e.properties, e.id) }
        }

        fun card(e: UiElement, attrs: MutableList<String>) {
            usesCardView = true
            val radius = e.i(PropertyKeys.CORNER_RADIUS)
            attrs += "app:cardCornerRadius=\"${radius}dp\""
            attrs += "app:cardElevation=\"3dp\""
            attrs += "app:cardBackgroundColor=\"${e.color(PropertyKeys.BG_COLOR)}\""
            tag("androidx.cardview.widget.CardView", attrs + "xmlns:app=\"http://schemas.android.com/apk/res-auto\"") {
                val inner = arrayListOf("android:layout_width=\"match_parent\"", "android:layout_height=\"match_parent\"")
                padAttr(e)?.let { inner += it }
                if (e.childLayout == ChildLayout.COLUMN || e.childLayout == ChildLayout.ROW) {
                    inner += "android:orientation=\"${if (e.childLayout == ChildLayout.ROW) "horizontal" else "vertical"}\""
                    groupGravity(e.childLayout, e.properties)?.let { inner += "android:gravity=\"$it\"" }
                }
                tag(containerTag(e.childLayout), inner) { children(e.children, e.childLayout, e.properties, e.id) }
            }
        }
    }
}
