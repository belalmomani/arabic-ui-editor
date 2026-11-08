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
 * يولّد كود Flutter (Dart) في ملف main.dart واحد: تطبيق Material مع مسارات مسماة لكل شاشة.
 * دالة نقية بلا اعتماد على أندرويد. الألوان تُصدَّر قيمًا ثابتة (Color(0x...)) لا مراجع للثيم.
 * التخطيط الحر والقيود: Stack + Positioned بالمواضع المحسوبة. التمرير والاتجاه RTL غير مضافين.
 */
object FlutterExporter {

    fun routeName(index: Int) = "/screen${index + 1}"
    fun className(index: Int) = "Screen${index + 1}"

    fun export(project: Project, onlyScreenId: String? = null): String {
        val screens = if (onlyScreenId == null) project.screens else project.screens.filter { it.id == onlyScreenId }
        return Gen(project).generate(screens, onlyScreenId == null)
    }

    private class Gen(val project: Project) {
        val ds = project.designSystem
        val screenIndex = project.screens.mapIndexed { i, s -> s.id to i }.toMap()
        var frames: LayoutResult? = null
        var usesMath = false

        fun ind(n: Int) = "  ".repeat(n)
        fun num(v: Float): String = if (v == v.toInt().toFloat()) "${v.toInt()}.0" else v.toString()
        fun esc(s: String) = s.replace("\\", "\\\\").replace("'", "\\'").replace("$", "\\$").replace("\n", "\\n")
        fun colorOf(hexText: String): String {
            val v = ColorUtil.parse(hexText) ?: 0
            return "Color(0x${String.format("%08X", v)})"
        }
        fun UiElement.i(key: String) = prop(key).toIntOrNull() ?: 0

        /** يكتب استدعاء بمعاملات مسماة، كل معامل في سطر، بإزاحة العمق [d]. */
        fun call(name: String, args: List<String>, d: Int): String =
            if (args.isEmpty()) "$name()" else "$name(\n" + args.joinToString("") { "${ind(d + 1)}$it,\n" } + "${ind(d)})"

        fun generate(screens: List<UiScreen>, withApp: Boolean): String {
            val bodies = StringBuilder()
            for (s in screens) {
                frames = LayoutEngine.layout(s)
                val idx = screenIndex[s.id] ?: 0
                bodies.append(screenClass(s, idx)).append("\n")
            }
            return buildString {
                append("// تصدير Flutter من «محرر الواجهات العربي» — مشروع: ${project.name.replace('\n', ' ')}\n")
                append("// ضع هذا الملف في lib/main.dart. الصور: ضعها في assets/ وسجّلها في pubspec.yaml.\n")
                if (usesMath) append("import 'dart:math' as math;\n")
                append("import 'package:flutter/material.dart';\n\n")
                if (withApp) {
                    append("void main() => runApp(const MyApp());\n\n")
                    append("class MyApp extends StatelessWidget {\n  const MyApp({super.key});\n\n  @override\n  Widget build(BuildContext context) {\n    return MaterialApp(\n")
                    append("      debugShowCheckedModeBanner: false,\n")
                    val first = project.screens.indexOfFirst { it.id == project.effectiveStartScreenId }.coerceAtLeast(0)
                    append("      initialRoute: '${routeName(first)}',\n      routes: {\n")
                    project.screens.forEachIndexed { i, _ -> append("        '${routeName(i)}': (context) => const ${className(i)}(),\n") }
                    append("      },\n    );\n  }\n}\n\n")
                }
                append(bodies)
            }.trimEnd() + "\n"
        }

        fun screenClass(s: UiScreen, idx: Int): String {
            val bg = Tokens.resolveColor(Tokens.color(Tokens.BACKGROUND), ds) ?: 0xFFFFFFFF.toInt()
            val pad = listOf(PropertyKeys.PADDING_START, PropertyKeys.PADDING_TOP, PropertyKeys.PADDING_END, PropertyKeys.PADDING_BOTTOM)
                .map { s.properties[it]?.toIntOrNull() ?: 0 }
            var body = group(s.childLayout, s.properties, s.elements, null, 3, expand = true)
            if (pad.any { it > 0 }) {
                body = call("Padding", listOf(
                    "padding: EdgeInsets.fromLTRB(${pad[0]}, ${pad[1]}, ${pad[2]}, ${pad[3]})",
                    "child: $body"
                ), 2)
            }
            return buildString {
                append("// الشاشة: ${s.name.replace('\n', ' ')}\n")
                append("class ${className(idx)} extends StatelessWidget {\n  const ${className(idx)}({super.key});\n\n")
                append("  @override\n  Widget build(BuildContext context) {\n    return Scaffold(\n")
                append("      backgroundColor: Color(0x${String.format("%08X", bg)}),\n")
                append("      body: SizedBox.expand(child: ").append(body.replace("\n", "\n    ")).append("),\n")
                append("    );\n  }\n}\n")
            }
        }

        // ---- مجموعات الأبناء ----

        fun mainAxis(a: String) = when (a) {
            Arrangements.CENTER -> "MainAxisAlignment.center"
            Arrangements.END -> "MainAxisAlignment.end"
            Arrangements.BETWEEN -> "MainAxisAlignment.spaceBetween"
            Arrangements.AROUND -> "MainAxisAlignment.spaceAround"
            Arrangements.EVENLY -> "MainAxisAlignment.spaceEvenly"
            else -> "MainAxisAlignment.start"
        }
        fun crossAxis(a: String) = when (a) {
            Aligns.CENTER -> "CrossAxisAlignment.center"
            Aligns.END -> "CrossAxisAlignment.end"
            else -> "CrossAxisAlignment.start"
        }
        fun alignment(h: String, v: String): String {
            val x = when (h) { Aligns.CENTER -> "0.0"; Aligns.END -> "1.0"; else -> "-1.0" }
            val y = when (v) { Aligns.CENTER -> "0.0"; Aligns.END -> "1.0"; else -> "-1.0" }
            return "Alignment($x, $y)"
        }

        fun group(layout: ChildLayout, p: Map<String, String>, kids: List<UiElement>, ownerId: String?, d: Int, expand: Boolean = false): String {
            val spacing = p[PropertyKeys.SPACING]?.toIntOrNull() ?: 0
            val ah = p[PropertyKeys.CHILD_ALIGN_H] ?: Aligns.START
            val av = p[PropertyKeys.CHILD_ALIGN_V] ?: Aligns.START
            val ar = p[PropertyKeys.ARRANGEMENT] ?: Arrangements.START
            val visible = kids.map { it.resolved(ds) }.filter { it.prop(PropertyKeys.VISIBLE) != "false" }
            fun list(items: List<String>, vertical: Boolean): String {
                val withGaps = ArrayList<String>()
                items.forEachIndexed { i, s ->
                    if (i > 0 && spacing > 0) withGaps += if (vertical) "SizedBox(height: ${spacing}.0)" else "SizedBox(width: ${spacing}.0)"
                    withGaps += s
                }
                return "[\n" + withGaps.joinToString("") { "${ind(d + 2)}${it.replace("\n", "\n" + ind(0))},\n" } + "${ind(d + 1)}]"
            }
            return when (layout) {
                ChildLayout.COLUMN, ChildLayout.ROW -> {
                    val vertical = layout == ChildLayout.COLUMN
                    val items = visible.map { child(it, layout, p, ownerId, d + 2) }
                    call(if (vertical) "Column" else "Row", listOf(
                        "mainAxisAlignment: ${mainAxis(ar)}",
                        "crossAxisAlignment: ${crossAxis(if (vertical) ah else av)}",
                        "children: ${list(items, vertical)}"
                    ), d)
                }
                else -> {
                    val items = visible.map { child(it, layout, p, ownerId, d + 2) }
                    call("Stack", listOf("children: ${list(items, true)}"), d)
                }
            }
        }

        // ---- العنصر ----

        fun child(e: UiElement, parent: ChildLayout, pp: Map<String, String>, ownerId: String?, d: Int): String {
            var w = widget(e, d)
            w = sized(e, parent, w, d)
            val op = e.i(PropertyKeys.OPACITY)
            if (op in 0..99) w = call("Opacity", listOf("opacity: ${op / 100.0}", "child: $w"), d)
            // القلب (حول المركز) داخل الدوران، كما في المحرر.
            val fx = e.prop(PropertyKeys.FLIP_X) == "true"
            val fy = e.prop(PropertyKeys.FLIP_Y) == "true"
            if (fx || fy) {
                w = call("Transform", listOf(
                    "alignment: Alignment.center",
                    "transform: Matrix4.diagonal3Values(${if (fx) "-1.0" else "1.0"}, ${if (fy) "-1.0" else "1.0"}, 1.0)",
                    "child: $w"
                ), d)
            }
            val rot = e.i(PropertyKeys.ROTATION)
            if (rot != 0) {
                usesMath = true
                val ax = e.i(PropertyKeys.PIVOT_X).coerceIn(0, 100) / 50.0 - 1.0
                val ay = e.i(PropertyKeys.PIVOT_Y).coerceIn(0, 100) / 50.0 - 1.0
                val args = arrayListOf("angle: $rot * math.pi / 180")
                if (ax != 0.0 || ay != 0.0) args += "alignment: Alignment($ax, $ay)"
                args += "child: $w"
                w = call("Transform.rotate", args, d)
            }
            w = withActions(e, w, d)
            w = placed(e, parent, pp, ownerId, w, d)
            return w
        }

        fun sized(e: UiElement, parent: ChildLayout, inner: String, d: Int): String {
            if (parent == ChildLayout.CONSTRAINT) {
                val f = frames?.frames?.get(e.id)
                if (f != null) return call("SizedBox", listOf("width: ${num(f.w)}", "height: ${num(f.h)}", "child: $inner"), d)
            }
            val l = e.layout
            val weight = e.i(PropertyKeys.WEIGHT)
            fun dim(spec: com.arabicuieditor.data.model.SizeSpec, skip: Boolean): String? {
                if (skip) return null
                return when (spec.mode) {
                    SizeMode.FIXED -> num(spec.value)
                    SizeMode.MATCH_PARENT -> "double.infinity"
                    SizeMode.FRACTION -> null
                    SizeMode.WRAP_CONTENT ->
                        if (e.type == ElementTypes.TEXT || e.type == ElementTypes.BUTTON || e.children.isNotEmpty()) null else num(spec.value)
                }
            }
            val skipW = weight > 0 && parent == ChildLayout.ROW
            val skipH = weight > 0 && parent == ChildLayout.COLUMN
            val wv = dim(l.width, skipW)
            val hv = dim(l.height, skipH)
            var out = inner
            if (wv != null || hv != null) {
                val args = ArrayList<String>()
                wv?.let { args += "width: $it" }
                hv?.let { args += "height: $it" }
                args += "child: $out"
                out = call("SizedBox", args, d)
            }
            val fw = if (l.width.mode == SizeMode.FRACTION) l.width.value / 100f else null
            val fh = if (l.height.mode == SizeMode.FRACTION) l.height.value / 100f else null
            if (fw != null || fh != null) {
                val args = ArrayList<String>()
                fw?.let { args += "widthFactor: ${num(it)}" }
                fh?.let { args += "heightFactor: ${num(it)}" }
                args += "child: $out"
                out = call("FractionallySizedBox", args, d)
            }
            return out
        }

        fun placed(e: UiElement, parent: ChildLayout, pp: Map<String, String>, ownerId: String?, w: String, d: Int): String {
            when (parent) {
                ChildLayout.ABSOLUTE, ChildLayout.CONSTRAINT -> {
                    val f = frames?.frames?.get(e.id)
                    val c = frames?.contents?.get(ownerId ?: LayoutEngine.ROOT)
                    val x = if (parent == ChildLayout.CONSTRAINT && f != null && c != null) f.x - c.x else e.layout.x
                    val y = if (parent == ChildLayout.CONSTRAINT && f != null && c != null) f.y - c.y else e.layout.y
                    return call("Positioned", listOf("left: ${num(x)}", "top: ${num(y)}", "child: $w"), d)
                }
                ChildLayout.BOX -> {
                    val h = e.prop(PropertyKeys.SELF_ALIGN_H).let { if (it == Aligns.INHERIT) pp[PropertyKeys.CHILD_ALIGN_H] ?: Aligns.START else it }
                    val v = e.prop(PropertyKeys.SELF_ALIGN_V).let { if (it == Aligns.INHERIT) pp[PropertyKeys.CHILD_ALIGN_V] ?: Aligns.START else it }
                    val m = margin(e, w, d)
                    return call("Align", listOf("alignment: ${alignment(h, v)}", "child: $m"), d)
                }
                else -> {
                    var out = margin(e, w, d)
                    val weight = e.i(PropertyKeys.WEIGHT)
                    if (weight > 0) out = call("Expanded", listOf("flex: $weight", "child: $out"), d)
                    return out
                }
            }
        }

        fun margin(e: UiElement, w: String, d: Int): String {
            val m = listOf(PropertyKeys.MARGIN_START, PropertyKeys.MARGIN_TOP, PropertyKeys.MARGIN_END, PropertyKeys.MARGIN_BOTTOM).map { e.i(it) }
            return if (m.any { it > 0 }) call("Padding", listOf("padding: EdgeInsets.fromLTRB(${m[0]}, ${m[1]}, ${m[2]}, ${m[3]})", "child: $w"), d) else w
        }

        fun withActions(e: UiElement, w: String, d: Int): String {
            fun code(a: ClickAction): String? = when (a) {
                ClickAction.None -> null
                is ClickAction.Blocks -> "() {} /* TODO: منطق المحرر لا يُصدَّر */"
                is ClickAction.Script -> "() {} /* TODO: سكربت JS في المحرر لا يُصدَّر */"
                ClickAction.Back -> "() => Navigator.pop(context)"
                is ClickAction.Navigate -> screenIndex[a.screenId]?.let { "() => Navigator.pushNamed(context, '${routeName(it)}')" }
                    ?: "() {} /* TODO: الشاشة المستهدفة محذوفة */"
            }
            val tap = code(Interactions.clickAction(e))
            val long = code(Interactions.actionFor(e, Interactions.TRIGGER_LONG_PRESS))
            if (tap == null && long == null) return w
            val args = ArrayList<String>()
            tap?.let { args += "onTap: $it" }
            long?.let { args += "onLongPress: $it" }
            args += "child: $w"
            return call("GestureDetector", args, d)
        }

        fun decoration(e: UiElement, shadow: Boolean, d: Int): String? {
            val bg = e.prop(PropertyKeys.BG_COLOR)
            val hasBg = bg.isNotEmpty() && ((ColorUtil.parse(bg) ?: 0) ushr 24) != 0
            val r = e.i(PropertyKeys.CORNER_RADIUS)
            val bw = e.i(PropertyKeys.BORDER_WIDTH)
            if (!hasBg && r <= 0 && bw <= 0 && !shadow) return null
            val a = ArrayList<String>()
            if (hasBg) a += "color: ${colorOf(bg)}"
            if (r > 0) a += "borderRadius: BorderRadius.circular(${r}.0)"
            if (bw > 0) a += "border: Border.all(color: ${colorOf(e.prop(PropertyKeys.BORDER_COLOR))}, width: ${bw}.0)"
            if (shadow) a += "boxShadow: const [BoxShadow(color: Color(0x33000000), blurRadius: 6.0, offset: Offset(0.0, 2.0))]"
            return call("BoxDecoration", a, d + 1)
        }

        fun padding(e: UiElement): String? {
            val p = listOf(PropertyKeys.PADDING_START, PropertyKeys.PADDING_TOP, PropertyKeys.PADDING_END, PropertyKeys.PADDING_BOTTOM).map { e.i(it) }
            return if (p.any { it > 0 }) "EdgeInsets.fromLTRB(${p[0]}, ${p[1]}, ${p[2]}, ${p[3]})" else null
        }

        fun textStyle(e: UiElement): String {
            val a = arrayListOf(
                "color: ${colorOf(e.prop(PropertyKeys.TEXT_COLOR))}",
                "fontSize: ${e.i(PropertyKeys.FONT_SIZE)}.0"
            )
            if (e.prop(PropertyKeys.FONT_WEIGHT) == FontWeights.BOLD) a += "fontWeight: FontWeight.bold"
            return "TextStyle(" + a.joinToString(", ") + ")"
        }

        fun textAlign(e: UiElement) = when (e.prop(PropertyKeys.TEXT_ALIGN)) {
            TextAligns.CENTER -> "TextAlign.center"
            TextAligns.END -> "TextAlign.end"
            else -> "TextAlign.start"
        }

        fun boxed(e: UiElement, inner: String, d: Int, shadow: Boolean = false, align: String? = null): String {
            val a = ArrayList<String>()
            decoration(e, shadow, d)?.let { a += "decoration: $it" }
            padding(e)?.let { a += "padding: $it" }
            align?.let { a += "alignment: $it" }
            if (a.isEmpty()) return inner
            a += "child: $inner"
            return call("Container", a, d)
        }

        fun widget(e: UiElement, d: Int): String = when (e.type) {
            ElementTypes.TEXT -> boxed(e, call("Text", listOf("'${esc(e.prop(PropertyKeys.TEXT))}'", "textAlign: ${textAlign(e)}", "style: ${textStyle(e)}"), d + 1), d)
            ElementTypes.BUTTON -> boxed(
                e, call("Text", listOf("'${esc(e.prop(PropertyKeys.TEXT))}'", "textAlign: ${textAlign(e)}", "style: ${textStyle(e)}"), d + 1), d,
                align = "Alignment.center"
            )
            ElementTypes.INPUT -> boxed(
                e,
                call("TextField", listOf(
                    "style: ${textStyle(e)}",
                    "decoration: InputDecoration(border: InputBorder.none, hintText: '${esc(e.prop(PropertyKeys.HINT))}', " +
                        "hintStyle: ${textStyle(e)})"
                ), d + 1),
                d, align = "Alignment.centerLeft"
            )
            ElementTypes.IMAGE -> image(e, d)
            ElementTypes.DIVIDER -> call("Container", listOf("height: 1.0", "color: ${colorOf(e.prop(PropertyKeys.BG_COLOR))}"), d)
            ElementTypes.SPACER -> "const SizedBox.shrink()"
            ElementTypes.LIST -> {
                val items = e.prop(PropertyKeys.ITEMS).split('\n').filter { it.isNotEmpty() }
                val h = e.i(PropertyKeys.ITEM_HEIGHT)
                val rows = items.joinToString("") {
                    "${ind(d + 3)}SizedBox(height: ${h}.0, child: Align(alignment: Alignment.centerLeft, child: Text('${esc(it)}', style: ${textStyle(e)}))),\n"
                }
                boxed(e, "Column(\n${ind(d + 2)}crossAxisAlignment: CrossAxisAlignment.stretch,\n${ind(d + 2)}children: [\n$rows${ind(d + 2)}],\n${ind(d + 1)})", d)
            }
            ElementTypes.CARD -> boxed(e, group(e.childLayout, e.properties, e.children, e.id, d + 1), d, shadow = true)
            else -> boxed(e, group(e.childLayout, e.properties, e.children, e.id, d + 1), d)
        }

        fun image(e: UiElement, d: Int): String {
            val icon = e.prop(PropertyKeys.ICON)
            val src = e.prop(PropertyKeys.IMAGE_SRC)
            val inner = when {
                IconCatalog.isValid(icon) -> {
                    val name = icon.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
                    "Icon(Icons.$name, color: ${colorOf(e.prop(PropertyKeys.ICON_COLOR))}, size: ${num(minOf(e.layout.width.value, e.layout.height.value) * 0.75f)})"
                }
                src.isNotEmpty() && ImageNames.isValid(src) -> {
                    val fit = when (e.prop(PropertyKeys.IMAGE_FIT)) { ImageFits.FIT -> "BoxFit.contain"; ImageFits.FILL -> "BoxFit.fill"; else -> "BoxFit.cover" }
                    "Image.asset('assets/$src', fit: $fit, width: double.infinity, height: double.infinity)"
                }
                else -> "const SizedBox.expand() /* TODO: ضع صورتك هنا */"
            }
            val clipped = if (e.i(PropertyKeys.CORNER_RADIUS) > 0 && src.isNotEmpty() && !IconCatalog.isValid(icon))
                "ClipRRect(borderRadius: BorderRadius.circular(${e.i(PropertyKeys.CORNER_RADIUS)}.0), child: $inner)" else inner
            return boxed(e, if (IconCatalog.isValid(icon)) "Center(child: $clipped)" else clipped, d)
        }
    }
}
