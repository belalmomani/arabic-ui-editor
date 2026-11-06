package com.arabicuieditor.data.export

import com.arabicuieditor.data.model.Aligns
import com.arabicuieditor.data.model.IconCatalog
import com.arabicuieditor.data.model.ImageFits
import com.arabicuieditor.data.model.ImageNames
import com.arabicuieditor.data.model.Arrangements
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ColorUtil
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.FontCatalog
import com.arabicuieditor.data.model.FontWeights
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.TextAligns
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.data.script.Step
import com.arabicuieditor.data.script.Steps
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.LayoutResult

/**
 * يولّد كود Jetpack Compose (Kotlin) من شجرة العناصر. دالة نقية بلا اعتماد على أندرويد.
 *
 * مبادئ التوليد:
 * - الكود يعكس ما في المحرر قدر الإمكان: الأحجام والإزاحات والهوامش والحشوات والألوان والزوايا.
 * - الألوان وأحجام الخط والزوايا المربوطة برموز نظام التصميم تصبح مراجع (`AppColors.primary`...)
 *   لا أرقامًا، فيبقى تغيير المظهر من مكان واحد.
 * - الشاشة تُرسم يسار-يمين (LTR) كما في مساحة التصميم؛ الغلاف مشروح بتعليق ويمكن حذفه.
 * - الأيقونات والصور: موضع فارغ مع TODO (لا استيراد صور بعد).
 * - الدوران يُصدَّر كما هو. سلوك التثبيت/التحجيم عند تغيّر حجم الشاشة (Pins) غير مُصدَّر.
 */
object ComposeExporter {

    const val DEFAULT_PACKAGE = "com.example.app"

    /** @param onlyScreenId إن حُدّد يُصدَّر هذه الشاشة وحدها (مع كائنات التصميم) بلا جذر التنقل. */
    fun export(
        project: Project, onlyScreenId: String? = null, packageName: String = DEFAULT_PACKAGE,
        customFonts: Boolean = true
    ): String {
        val screens = if (onlyScreenId == null) project.screens else project.screens.filter { it.id == onlyScreenId }
        return Generator(project, packageName, onlyScreenId == null, customFonts).generate(screens)
    }

    /** اسم دالة الشاشة رقم [index] (من 0): Screen1، Screen2... (الأسماء العربية لا تصلح معرّفات). */
    fun screenFunction(index: Int) = "Screen${index + 1}"
    fun screenRoute(index: Int) = "screen${index + 1}"

    // ---------------------------------------------------------------------------------------

    private class Generator(val project: Project, val pkg: String, val withRoot: Boolean, val customFonts: Boolean = true) {
        private val ds: DesignSystem = project.designSystem
        private val sb = StringBuilder()
        private var depth = 0
        private var inputCounter = 0
        private var frames: LayoutResult? = null

        private val screenIndex: Map<String, Int> =
            project.screens.mapIndexed { i, s -> s.id to i }.toMap()

        // ---- المنطق (منطق الأزرار) ----
        private fun nameOf(e: UiElement) = e.properties[PropertyKeys.NAME].orEmpty()
        private val allElements: List<UiElement> by lazy { project.screens.flatMap { ElementTree.flatten(it.elements) } }
        private val clickBlocks: List<List<Step>> by lazy {
            allElements.mapNotNull { (Interactions.clickAction(it) as? ClickAction.Blocks)?.steps }.filter { it.isNotEmpty() }
        }
        private val usesLogic get() = clickBlocks.isNotEmpty()
        /** أسماء العناصر التي تغيّرها خطوات المنطق؛ هذه وحدها تُرسم بقيم ديناميكية. */
        private val targets: Set<String> by lazy {
            clickBlocks.flatten().filter { Steps.usesElement(it.kind) && it.a.isNotBlank() }.map { it.a }.toSet()
        }
        private val usesToast: Boolean by lazy { clickBlocks.flatten().any { it.kind == Steps.TOAST } }
        private val usesInterp: Boolean by lazy {
            allElements.any { (it.type == ElementTypes.TEXT || it.type == ElementTypes.BUTTON) && it.prop(PropertyKeys.TEXT).contains("{{") }
        }
        private fun isTarget(e: UiElement) = nameOf(e).let { it.isNotEmpty() && it in targets }
        private fun initialVisible(name: String) =
            allElements.firstOrNull { nameOf(it) == name }?.let { it.prop(PropertyKeys.VISIBLE) != "false" } ?: true

        private fun dbl(raw: String, fallback: Double): String =
            (raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: fallback).toString()

        private fun cond(s: Step): String {
            val parts = s.b.split("|", limit = 2)
            val opRaw = parts.getOrElse(0) { "==" }
            val value = parts.getOrElse(1) { "" }
            val op = if (opRaw in Steps.ops) opRaw else "=="
            val n = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            return if (n != null) "AppLogic.num(\"${esc(s.a)}\") $op $n"
            else "AppLogic.str(\"${esc(s.a)}\") ${if (op == "!=") "!=" else "=="} \"${esc(value)}\""
        }

        private fun stepStatement(s: Step): String? {
            val a = esc(s.a)
            return when (s.kind) {
                Steps.INC -> "AppLogic.add(\"$a\", ${dbl(s.b, 1.0)})"
                Steps.DEC -> "AppLogic.add(\"$a\", -${dbl(s.b, 1.0)})"
                Steps.SET_VAR -> {
                    val n = s.b.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
                    val v = when {
                        n != null -> n.toString()
                        s.b == "true" || s.b == "false" -> s.b
                        else -> "\"${esc(s.b)}\""
                    }
                    "AppLogic.vars[\"$a\"] = $v"
                }
                Steps.TEXT -> "AppLogic.props[\"$a|text\"] = \"${esc(s.b)}\""
                Steps.SHOW -> "AppLogic.props[\"$a|visible\"] = \"true\""
                Steps.HIDE -> "AppLogic.props[\"$a|visible\"] = \"false\""
                Steps.TOGGLE ->
                    "AppLogic.props[\"$a|visible\"] = if (AppLogic.visible(\"$a\", ${initialVisible(s.a)})) \"false\" else \"true\""
                Steps.COLOR -> "AppLogic.props[\"$a|textColor\"] = \"${esc(s.b)}\""
                Steps.BG -> "AppLogic.props[\"$a|bgColor\"] = \"${esc(s.b)}\""
                Steps.TOAST -> "Toast.makeText(ctx, \"${esc(s.b)}\", Toast.LENGTH_SHORT).show()"
                Steps.NAV -> project.screens.indexOfFirst { it.name == s.a || it.id == s.a }.takeIf { it >= 0 }
                    ?.let { "onNavigate(\"${screenRoute(it)}\")" } ?: "/* الشاشة غير موجودة */ Unit"
                Steps.BACK -> "onBack()"
                else -> null
            }
        }

        /** تعليمات Kotlin لخطوات المنطق؛ «إذا» تحكم الخطوة التالية فقط (كما في المعاينة). */
        private fun blocksBody(steps: List<Step>): String {
            val out = ArrayList<String>()
            var pending: String? = null
            for (st in steps.take(Steps.MAX_STEPS)) {
                if (st.kind == Steps.IF) { if (st.a.isNotBlank()) pending = cond(st); continue }
                val code = stepStatement(st) ?: continue
                out += if (pending != null) "if ($pending) { $code }" else code
                pending = null
            }
            return out.joinToString("; ")
        }

        private fun appLogic() {
            line("// ---- حالة المنطق (متغيرات وخصائص العناصر المتغيّرة، بمفاتيح أسماء العناصر) ----")
            line("object AppLogic {")
            depth++
            line("val vars = mutableStateMapOf<String, Any>()")
            line("val props = mutableStateMapOf<String, String>()")
            line("private val template = Regex(\"\"\"\\{\\{\\s*(?:vars\\.)?([\\p{L}_][\\p{L}\\p{N}_]*)\\s*\\}\\}\"\"\")")
            line("fun fmt(d: Double): String = if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()")
            line("fun num(n: String): Double = when (val v = vars[n]) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull() ?: 0.0; else -> 0.0 }")
            line("fun str(n: String): String = when (val v = vars[n]) { null -> \"\"; is Double -> fmt(v); else -> v.toString() }")
            line("fun add(n: String, delta: Double) { vars[n] = num(n) + delta }")
            line("fun interp(s: String): String = template.replace(s) { m -> vars[m.groupValues[1]]?.let { v -> if (v is Double) fmt(v) else v.toString() } ?: m.value }")
            line("fun prop(el: String, key: String, def: String): String = props[el + \"|\" + key] ?: def")
            line("fun visible(el: String, def: Boolean): Boolean = props[el + \"|visible\"]?.let { it != \"false\" } ?: def")
            line("fun color(el: String, key: String, def: Color): Color =")
            depth++
            line("props[el + \"|\" + key]?.let { try { Color(android.graphics.Color.parseColor(it)) } catch (e: IllegalArgumentException) { null } } ?: def")
            depth--
            depth--
            line("}")
            line()
        }

        // ---- كتابة ----

        private fun ind(n: Int = depth) = "    ".repeat(n)
        private fun line(text: String = "") {
            if (text.isEmpty()) sb.append('\n') else sb.append(ind()).append(text).append('\n')
        }

        /** سلسلة معدّلات: قصيرة في سطر، وطويلة في أسطر (بإزاحة تناسب موضعها كوسيط مسمّى). */
        private fun modStr(parts: List<String>): String {
            if (parts.isEmpty()) return "Modifier"
            if (parts.size <= 2) return "Modifier" + parts.joinToString("")
            return "Modifier" + parts.joinToString("") { "\n" + ind(depth + 2) + it }
        }

        private fun call(name: String, args: List<String> = emptyList(), mods: List<String>? = null, body: (() -> Unit)? = null) {
            val all = ArrayList<String>()
            if (mods != null) all += "modifier = ${modStr(mods)}"
            all += args
            if (all.isEmpty()) {
                line(if (body != null) "$name {" else "$name()")
            } else {
                line("$name(")
                depth++
                all.forEach { line("$it,") }
                depth--
                line(if (body != null) ") {" else ")")
            }
            if (body != null) {
                depth++
                body()
                depth--
                line("}")
            }
        }

        // ---- قيم ----

        private fun num(v: Float): String =
            if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

        private fun dp(v: Float) = "${num(v)}.dp"
        private fun dp(v: Int) = "$v.dp"

        private fun esc(s: String): String = buildString {
            for (c in s) when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> {}
                '\t' -> append("\\t")
                else -> append(c)
            }
        }

        private fun colorValue(raw: String): Int =
            Tokens.resolveColor(raw, ds) ?: ColorUtil.parse(raw) ?: 0

        private fun colorExpr(raw: String): String {
            if (Tokens.isColorRef(raw)) return "AppColors.${raw.removePrefix(Tokens.COLOR)}"
            val v = ColorUtil.parse(raw) ?: 0
            if ((v ushr 24) == 0) return "Color.Transparent"
            return "Color(0x" + "%08X".format(v) + ")"
        }

        private fun isTransparent(raw: String) = (colorValue(raw) ushr 24) == 0

        private fun radiusExpr(raw: String): String =
            if (Tokens.isRadiusRef(raw)) "AppRadius.${raw.removePrefix(Tokens.RADIUS)}" else dp(raw.toIntOrNull() ?: 0)

        private fun radiusValue(raw: String): Int = Tokens.resolveInt(raw, ds) ?: raw.toIntOrNull() ?: 0

        private fun textSizeExpr(raw: String): String =
            if (Tokens.isTextRef(raw)) "AppText.${raw.removePrefix(Tokens.TEXT)}" else "${raw.toIntOrNull() ?: 14}.sp"

        private fun intOf(e: UiElement, key: String): Int = e.prop(key).toIntOrNull() ?: 0

        private fun padding(s: Int, t: Int, e: Int, b: Int): String? = when {
            s == 0 && t == 0 && e == 0 && b == 0 -> null
            s == t && t == e && e == b -> ".padding(${dp(s)})"
            s == e && t == b -> ".padding(horizontal = ${dp(s)}, vertical = ${dp(t)})"
            else -> ".padding(start = ${dp(s)}, top = ${dp(t)}, end = ${dp(e)}, bottom = ${dp(b)})"
        }

        private fun alignH(v: String) = when (v) {
            Aligns.CENTER -> "Alignment.CenterHorizontally"
            Aligns.END -> "Alignment.End"
            else -> "Alignment.Start"
        }

        private fun alignV(v: String) = when (v) {
            Aligns.CENTER -> "Alignment.CenterVertically"
            Aligns.END -> "Alignment.Bottom"
            else -> "Alignment.Top"
        }

        private fun boxAlign(h: String, v: String): String {
            val row = when (v) { Aligns.CENTER -> "Center"; Aligns.END -> "Bottom"; else -> "Top" }
            val col = when (h) { Aligns.CENTER -> "Center"; Aligns.END -> "End"; else -> "Start" }
            return if (row == "Center" && col == "Center") "Alignment.Center"
            else if (row == "Center") "Alignment.Center$col"
            else "Alignment.$row$col"
        }

        // ---- التوليد ----

        fun generate(screens: List<UiScreen>): String {
            line("// تصدير من «محرر الواجهات العربي» — مشروع: ${project.name.replace('\n', ' ')}")
            line("// الأسماء العربية للشاشات في تعليقات؛ الدوال Screen1، Screen2... (معرّفات لاتينية).")
            line("// التخطيط الحر والقيود تُصدَّر بمواضع وأبعاد التصميم الثابتة؛ لا يُصدَّر سلوك التثبيت أو القيود الحيّة عند تغيّر حجم الشاشة.")
            line("package $pkg")
            line()
            line("import androidx.activity.compose.BackHandler")
            if (usesToast) line("import android.widget.Toast")
            val usesCustomFont = project.screens.any { sc ->
                ElementTree.flatten(sc.elements).any { fontExpr(it)?.startsWith("FontFamily(Font(") == true }
            }
            val usesImages = project.screens.any { sc ->
                ElementTree.flatten(sc.elements).any {
                    it.type == ElementTypes.IMAGE && ImageNames.isValid(it.prop(PropertyKeys.IMAGE_SRC))
                }
            }
            if (usesCustomFont) {
                line("// يستعمل خطوطًا مخصصة: انسخ ملفاتها نفسها إلى res/font في مشروعك، واستورد R الخاص بحزمتك.")
            }
            if (usesImages) {
                line("// يستعمل صورًا: انسخ ملفاتها إلى res/drawable في مشروعك (الأسماء في التعليقات أدناه).")
                line("import androidx.compose.ui.res.painterResource")
            }
            val usesIcons = project.screens.any { sc ->
                ElementTree.flatten(sc.elements).any { IconCatalog.isValid(it.prop(PropertyKeys.ICON)) }
            }
            if (usesIcons) {
                line("import androidx.compose.material.icons.Icons")
                line("import androidx.compose.material.icons.filled.*")
            }
            if (usesCustomFont || usesImages) line("import $pkg.R")
            for (p in listOf(
                "foundation", "foundation.layout", "foundation.shape", "foundation.text", "material3", "runtime", "ui",
                "ui.draw", "ui.graphics", "ui.layout", "ui.platform", "ui.text", "ui.text.font", "ui.text.style", "ui.unit"
            )) line("import androidx.compose.$p.*")
            line()
            designObjects()
            if (usesLogic || usesInterp) appLogic()
            for (s in screens) {
                screen(s)
                line()
            }
            if (withRoot) root()
            return sb.toString().trimEnd() + "\n"
        }

        private fun designObjects() {
            line("// ---- نظام التصميم (من تبويب «التصميم») ----")
            line("object AppColors {")
            depth++
            for (n in Tokens.colorNames) line("val $n = Color(0x${"%08X".format(ds.color(n))})")
            depth--
            line("}")
            line()
            line("object AppRadius {")
            depth++
            for (n in Tokens.radiusNames) line("val $n = ${ds.radius(n)}.dp")
            depth--
            line("}")
            line()
            line("object AppText {")
            depth++
            for (n in Tokens.textNames) line("val $n = ${ds.textSize(n)}.sp")
            depth--
            line("}")
            line()
        }

        private fun root() {
            val order = project.screens
            val start = project.effectiveStartScreenId?.let { screenIndex[it] } ?: 0
            line("// ---- التنقل بين الشاشات (مكدّس بسيط بلا مكتبات إضافية) ----")
            line("@Composable")
            line("fun AppRoot() {")
            depth++
            line("var stack by remember { mutableStateOf(listOf(\"${screenRoute(start)}\")) }")
            line("val navigate: (String) -> Unit = { stack = stack + it }")
            line("val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }")
            line("BackHandler(enabled = stack.size > 1) { back() }")
            line("when (stack.last()) {")
            depth++
            order.indices.forEach { i ->
                line("\"${screenRoute(i)}\" -> ${screenFunction(i)}(onNavigate = navigate, onBack = back)")
            }
            depth--
            line("}")
            depth--
            line("}")
        }

        private fun screen(s: UiScreen) {
            val idx = screenIndex[s.id] ?: 0
            inputCounter = 0
            frames = LayoutEngine.layout(s)
            line("// الشاشة: ${s.name.replace('\n', ' ')}")
            line("@Composable")
            line("fun ${screenFunction(idx)}(onNavigate: (String) -> Unit = {}, onBack: () -> Unit = {}) {")
            depth++
            if (usesToast) line("val ctx = LocalContext.current")
            line("// التصميم يسار-يمين كما في المحرر؛ احذف هذا الغلاف ليتبع اتجاه لغة الجهاز.")
            call("CompositionLocalProvider", listOf("LocalLayoutDirection provides LayoutDirection.Ltr"), null) {
                val read: (String) -> String = { key -> s.prop(key) }
                val mods = mutableListOf(".fillMaxSize()", ".background(AppColors.background)")
                padding(
                    read(PropertyKeys.PADDING_START).toIntOrNull() ?: 0, read(PropertyKeys.PADDING_TOP).toIntOrNull() ?: 0,
                    read(PropertyKeys.PADDING_END).toIntOrNull() ?: 0, read(PropertyKeys.PADDING_BOTTOM).toIntOrNull() ?: 0
                )?.let { mods += it }
                layoutNode(s.childLayout, read, mods, s.elements)
            }
            depth--
            line("}")
        }

        /** حاوية بتخطيط [kind] وأبنائها. [read] يقرأ خصائص التخطيط (ARRANGEMENT/SPACING/CHILD_ALIGN...). */
        private fun layoutNode(kind: ChildLayout, read: (String) -> String, mods: List<String>, children: List<UiElement>, owner: String? = null) {
            val spacing = read(PropertyKeys.SPACING).toIntOrNull() ?: 0
            val ah = read(PropertyKeys.CHILD_ALIGN_H)
            val av = read(PropertyKeys.CHILD_ALIGN_V)
            val arr = read(PropertyKeys.ARRANGEMENT)
            when (kind) {
                ChildLayout.COLUMN -> {
                    val args = ArrayList<String>()
                    arrangement(arr, spacing, vertical = true)?.let { args += "verticalArrangement = $it" }
                    args += "horizontalAlignment = ${alignH(ah)}"
                    call("Column", args, mods) { children.forEach { child(it, ChildLayout.COLUMN, ah, av, owner) } }
                }
                ChildLayout.ROW -> {
                    val args = ArrayList<String>()
                    arrangement(arr, spacing, vertical = false)?.let { args += "horizontalArrangement = $it" }
                    args += "verticalAlignment = ${alignV(av)}"
                    call("Row", args, mods) { children.forEach { child(it, ChildLayout.ROW, ah, av, owner) } }
                }
                ChildLayout.BOX ->
                    call("Box", listOf("contentAlignment = ${boxAlign(ah, av)}"), mods) {
                        children.forEach { child(it, ChildLayout.BOX, ah, av, owner) }
                    }
                else ->
                    call("Box", emptyList(), mods) {
                        val k = if (kind == ChildLayout.CONSTRAINT) ChildLayout.CONSTRAINT else ChildLayout.ABSOLUTE
                        children.forEach { child(it, k, ah, av, owner) }
                    }
            }
        }

        private fun arrangement(arr: String, spacing: Int, vertical: Boolean): String? {
            val sp = dp(spacing)
            return when (arr) {
                Arrangements.CENTER ->
                    if (spacing > 0) "Arrangement.spacedBy($sp, ${if (vertical) "Alignment.CenterVertically" else "Alignment.CenterHorizontally"})"
                    else "Arrangement.Center"
                Arrangements.END ->
                    if (spacing > 0) "Arrangement.spacedBy($sp, ${if (vertical) "Alignment.Bottom" else "Alignment.End"})"
                    else if (vertical) "Arrangement.Bottom" else "Arrangement.End"
                Arrangements.BETWEEN -> "Arrangement.SpaceBetween"
                Arrangements.AROUND -> "Arrangement.SpaceAround"
                Arrangements.EVENLY -> "Arrangement.SpaceEvenly"
                else -> if (spacing > 0) "Arrangement.spacedBy($sp)" else null
            }
        }

        // ---- عنصر ----

        private fun isContainerType(t: String) = t == ElementTypes.CONTAINER || t == ElementTypes.CARD

        /** معدّلات التموضع والحجم داخل الأب (الجزء المشترك لكل الأنواع). */
        private fun placement(e: UiElement, parent: ChildLayout, parentAh: String, parentAv: String, owner: String?): List<String> {
            val out = ArrayList<String>()
            if (parent == ChildLayout.CONSTRAINT) {
                // القيود تُحلّ بحجم التصميم: يُصدَّر الموضع والحجم الناتجان (لا قيود حيّة بعد).
                val r = frames
                val f = r?.frames?.get(e.id)
                val c = r?.contents?.get(owner ?: LayoutEngine.ROOT)
                if (f != null && c != null) {
                    return listOf(
                        ".offset(${dp(f.x - c.x)}, ${dp(f.y - c.y)})",
                        ".size(${dp(f.w)}, ${dp(f.h)})"
                    )
                }
            }
            val weight = intOf(e, PropertyKeys.WEIGHT)
            val selfH = e.prop(PropertyKeys.SELF_ALIGN_H)
            val selfV = e.prop(PropertyKeys.SELF_ALIGN_V)
            when (parent) {
                ChildLayout.COLUMN -> {
                    if (weight > 0) out += ".weight(${weight}f)"
                    if (selfH != Aligns.INHERIT) out += ".align(${alignH(selfH)})"
                }
                ChildLayout.ROW -> {
                    if (weight > 0) out += ".weight(${weight}f)"
                    if (selfV != Aligns.INHERIT) out += ".align(${alignV(selfV)})"
                }
                ChildLayout.BOX ->
                    if (selfH != Aligns.INHERIT || selfV != Aligns.INHERIT) {
                        val h = if (selfH == Aligns.INHERIT) parentAh else selfH
                        val v = if (selfV == Aligns.INHERIT) parentAv else selfV
                        out += ".align(${boxAlign(h, v)})"
                    }
                else -> {}
            }
            if (parent != ChildLayout.ABSOLUTE) {
                padding(
                    intOf(e, PropertyKeys.MARGIN_START), intOf(e, PropertyKeys.MARGIN_TOP),
                    intOf(e, PropertyKeys.MARGIN_END), intOf(e, PropertyKeys.MARGIN_BOTTOM)
                )?.let { out += it }
            } else {
                out += ".offset(${dp(e.layout.x)}, ${dp(e.layout.y)})"
            }
            out += sizeParts(e, parent, weight > 0)
            return out
        }

        private fun sizeParts(e: UiElement, parent: ChildLayout, weighted: Boolean): List<String> {
            val l = e.layout
            val leafFallback = !(isContainerType(e.type) && e.children.isNotEmpty()) &&
                e.type != ElementTypes.TEXT && e.type != ElementTypes.BUTTON && e.type != ElementTypes.LIST
            fun axis(spec: SizeSpec, horizontal: Boolean, skip: Boolean): String? {
                if (skip) return null
                return when (spec.mode) {
                    SizeMode.FIXED -> if (horizontal) ".width(${dp(spec.value)})" else ".height(${dp(spec.value)})"
                    SizeMode.MATCH_PARENT -> if (horizontal) ".fillMaxWidth()" else ".fillMaxHeight()"
                    SizeMode.FRACTION -> {
                        val f = (spec.value / 100f).toString() + "f"
                        if (horizontal) ".fillMaxWidth($f)" else ".fillMaxHeight($f)"
                    }
                    SizeMode.WRAP_CONTENT ->
                        if (leafFallback) (if (horizontal) ".width(${dp(spec.value)})" else ".height(${dp(spec.value)})") else null
                }
            }
            val skipW = weighted && parent == ChildLayout.ROW
            val skipH = weighted && parent == ChildLayout.COLUMN
            if (l.width.mode == SizeMode.FIXED && l.height.mode == SizeMode.FIXED && !skipW && !skipH) {
                return listOf(".size(${dp(l.width.value)}, ${dp(l.height.value)})")
            }
            return listOfNotNull(axis(l.width, true, skipW), axis(l.height, false, skipH))
        }

        /** المظهر: شفافية، دوران، ظل/قص، خلفية، حد، نقر. */
        private fun visual(e: UiElement, shadowed: Boolean = false): List<String> {
            val out = ArrayList<String>()
            val opacity = intOf(e, PropertyKeys.OPACITY)
            if (opacity in 0..99) out += ".alpha(${opacity / 100.0}f)"
            val rotation = intOf(e, PropertyKeys.ROTATION)
            if (rotation != 0) out += ".rotate(${rotation}f)"
            val fx = e.prop(PropertyKeys.FLIP_X) == "true"
            val fy = e.prop(PropertyKeys.FLIP_Y) == "true"
            if (fx || fy) out += ".graphicsLayer(scaleX = ${if (fx) "-1f" else "1f"}, scaleY = ${if (fy) "-1f" else "1f"})"
            val radiusRaw = e.prop(PropertyKeys.CORNER_RADIUS)
            val shape = if (radiusRaw.isNotEmpty() && (Tokens.isRadiusRef(radiusRaw) || radiusValue(radiusRaw) > 0))
                "RoundedCornerShape(${radiusExpr(radiusRaw)})" else null
            if (shadowed) out += ".shadow(3.dp${shape?.let { ", $it" } ?: ""})"
            else if (shape != null) out += ".clip($shape)"
            val bg = e.prop(PropertyKeys.BG_COLOR)
            if (isTarget(e)) {
                val def = if (bg.isNotEmpty()) colorExpr(bg) else "Color.Transparent"
                out += ".background(AppLogic.color(\"${esc(nameOf(e))}\", \"bgColor\", $def))"
            } else if (bg.isNotEmpty() && !isTransparent(bg)) out += ".background(${colorExpr(bg)})"
            val bw = intOf(e, PropertyKeys.BORDER_WIDTH)
            if (bw > 0) {
                val bc = colorExpr(e.prop(PropertyKeys.BORDER_COLOR))
                out += ".border(${dp(bw)}, $bc${shape?.let { ", $it" } ?: ""})"
            }
            clickMod(e)?.let { out += it }
            return out
        }

        private fun clickMod(e: UiElement): String? = when (val a = Interactions.clickAction(e)) {
            ClickAction.None -> null
            is ClickAction.Blocks -> blocksBody(a.steps).let { b ->
                if (b.isEmpty()) ".clickable { }" else ".clickable { $b }"
            }
            is ClickAction.Script -> ".clickable { /* TODO: سكربت JS في المحرر لا يُصدَّر؛ اكتب المنطق هنا */ }"
            ClickAction.Back -> ".clickable { onBack() }"
            is ClickAction.Navigate -> {
                val i = screenIndex[a.screenId]
                if (i == null) ".clickable { /* TODO: الشاشة المستهدفة محذوفة */ }"
                else ".clickable { onNavigate(\"${screenRoute(i)}\") }"
            }
        }

        private fun contentPadding(e: UiElement) = padding(
            intOf(e, PropertyKeys.PADDING_START), intOf(e, PropertyKeys.PADDING_TOP),
            intOf(e, PropertyKeys.PADDING_END), intOf(e, PropertyKeys.PADDING_BOTTOM)
        )

        private fun child(e: UiElement, parent: ChildLayout, parentAh: String, parentAv: String, owner: String? = null) {
            val label = e.prop(PropertyKeys.NAME).replace('\n', ' ').trim()
            val hiddenAtStart = e.prop(PropertyKeys.VISIBLE) == "false"
            val dynamic = isTarget(e)
            if (hiddenAtStart && !dynamic) {
                line("// ${label.ifEmpty { e.type }} (مخفي في التصميم، لم يُصدَّر)")
                return
            }
            if (label.isNotEmpty()) line("// $label")
            val place = placement(e, parent, parentAh, parentAv, owner)
            if (dynamic) {
                line("if (AppLogic.visible(\"${esc(nameOf(e))}\", ${!hiddenAtStart})) {")
                depth++
            }
            when (e.type) {
                ElementTypes.TEXT, ElementTypes.BUTTON -> textBox(e, place, e.prop(PropertyKeys.TEXT), singleLine = e.type == ElementTypes.BUTTON)
                ElementTypes.INPUT -> inputBox(e, place)
                ElementTypes.IMAGE -> {
                    val src = e.prop(PropertyKeys.IMAGE_SRC)
                    val icon = e.prop(PropertyKeys.ICON)
                    if (IconCatalog.isValid(icon)) {
                        call(
                            "Box",
                            emptyList(),
                            place + visual(e)
                        ) {
                            call(
                                "Icon",
                                listOf(
                                    "imageVector = Icons.Filled.$icon",
                                    "contentDescription = null",
                                    "tint = ${colorExpr(e.prop(PropertyKeys.ICON_COLOR))}"
                                ),
                                listOf(".fillMaxSize(0.75f)", ".align(Alignment.Center)")
                            )
                        }
                    } else if (src.isNotEmpty() && ImageNames.isValid(src)) {
                        line("// انسخ الملف $src من resources إلى app/src/main/res/drawable/ (الاسم: ${ImageNames.drawableName(src)})")
                        val zoom = intOf(e, PropertyKeys.IMAGE_ZOOM)
                        val px = intOf(e, PropertyKeys.IMAGE_PAN_X)
                        val py = intOf(e, PropertyKeys.IMAGE_PAN_Y)
                        val inner = if (zoom != 100 || px != 0 || py != 0) {
                            listOf(
                                ".fillMaxSize()",
                                ".graphicsLayer { scaleX = ${zoom / 100.0}f; scaleY = ${zoom / 100.0}f; " +
                                    "translationX = size.width * ${px / 100.0}f; translationY = size.height * ${py / 100.0}f }"
                            )
                        } else null
                        if (inner != null) {
                            call("Box", emptyList(), place + visual(e)) {
                                call(
                                    "Image",
                                    listOf(
                                        "painter = painterResource(R.drawable.${ImageNames.drawableName(src)})",
                                        "contentDescription = null",
                                        "contentScale = ContentScale.${when (e.prop(PropertyKeys.IMAGE_FIT)) { ImageFits.FIT -> "Fit"; ImageFits.FILL -> "FillBounds"; else -> "Crop" }}"
                                    ),
                                    inner
                                )
                            }
                        } else call(
                            "Image",
                            listOf(
                                "painter = painterResource(R.drawable.${ImageNames.drawableName(src)})",
                                "contentDescription = null",
                                "contentScale = ContentScale.${when (e.prop(PropertyKeys.IMAGE_FIT)) { ImageFits.FIT -> "Fit"; ImageFits.FILL -> "FillBounds"; else -> "Crop" }}"
                            ),
                            place + visual(e)
                        )
                    } else {
                        line("// TODO: ضع صورتك هنا، مثل Image(painterResource(R.drawable.name), contentDescription = null)")
                        call("Box", emptyList(), place + visual(e))
                    }
                }
                ElementTypes.CONTAINER, ElementTypes.CARD -> {
                    val shadowed = e.type == ElementTypes.CARD
                    val mods = place + visual(e, shadowed) + listOfNotNull(contentPadding(e))
                    layoutNode(e.childLayout, { k -> e.prop(k) }, mods, e.children, e.id)
                }
                ElementTypes.DIVIDER -> {
                    call("Box", listOf("contentAlignment = Alignment.Center"), place) {
                        call("Box", emptyList(), listOf(".fillMaxWidth()", ".height(1.dp)", ".background(${colorExpr(e.prop(PropertyKeys.BG_COLOR))})"))
                    }
                }
                ElementTypes.SPACER -> call("Spacer", emptyList(), place)
                ElementTypes.LIST -> listBox(e, place)
                else -> line("// عنصر غير مدعوم: ${e.type}")
            }
            if (dynamic) {
                depth--
                line("}")
            }
        }

        /** تعبير الخط، أو null للافتراضي. المخصص يفترض وجود الملف نفسه في res/font في مشروع الوجهة. */
        private fun fontExpr(e: UiElement): String? = when (val id = e.prop(PropertyKeys.FONT_FAMILY)) {
            FontCatalog.SANS -> "FontFamily.SansSerif"
            FontCatalog.SERIF -> "FontFamily.Serif"
            FontCatalog.MONO -> "FontFamily.Monospace"
            FontCatalog.CURSIVE -> "FontFamily.Cursive"
            FontCatalog.DEFAULT, "" -> null
            else -> if (customFonts && FontCatalog.isValid(id)) "FontFamily(Font(R.font.$id))" else null
        }

        private fun textStyleArgs(e: UiElement, singleLine: Boolean): List<String> {
            val args = ArrayList<String>()
            fontExpr(e)?.let { args += "fontFamily = $it" }
            val baseColor = colorExpr(e.prop(PropertyKeys.TEXT_COLOR))
            args += if (isTarget(e) && (e.type == ElementTypes.TEXT || e.type == ElementTypes.BUTTON))
                "color = AppLogic.color(\"${esc(nameOf(e))}\", \"textColor\", $baseColor)"
            else "color = $baseColor"
            args += "fontSize = ${textSizeExpr(e.prop(PropertyKeys.FONT_SIZE))}"
            when (e.prop(PropertyKeys.FONT_WEIGHT)) {
                FontWeights.BOLD -> args += "fontWeight = FontWeight.Bold"
                FontWeights.MEDIUM -> args += "fontWeight = FontWeight.Medium"
            }
            args += "textAlign = ${textAlign(e)}"
            if (singleLine) {
                args += "maxLines = 1"
                args += "overflow = TextOverflow.Ellipsis"
            }
            return args
        }

        private fun textAlign(e: UiElement) = when (e.prop(PropertyKeys.TEXT_ALIGN)) {
            TextAligns.CENTER -> "TextAlign.Center"
            TextAligns.END -> "TextAlign.End"
            else -> "TextAlign.Start"
        }

        private fun boxContentAlignment(e: UiElement) = when (e.prop(PropertyKeys.TEXT_ALIGN)) {
            TextAligns.CENTER -> "Alignment.Center"
            TextAligns.END -> "Alignment.CenterEnd"
            else -> "Alignment.CenterStart"
        }

        private fun innerPadMods(e: UiElement) = listOfNotNull(contentPadding(e))

        private fun textBox(e: UiElement, place: List<String>, text: String, singleLine: Boolean) {
            call("Box", listOf("contentAlignment = ${boxContentAlignment(e)}"), place + visual(e)) {
                val literal = "\"${esc(text)}\""
                val expr = when {
                    isTarget(e) -> "AppLogic.interp(AppLogic.prop(\"${esc(nameOf(e))}\", \"text\", $literal))"
                    text.contains("{{") -> "AppLogic.interp($literal)"
                    else -> literal
                }
                call("Text", listOf("text = $expr") + textStyleArgs(e, singleLine), innerPadMods(e).ifEmpty { null })
            }
        }

        private fun inputBox(e: UiElement, place: List<String>) {
            inputCounter++
            val v = "input$inputCounter"
            line("var $v by remember { mutableStateOf(\"\") }")
            call("Box", listOf("contentAlignment = ${boxContentAlignment(e)}"), place + visual(e)) {
                val pad = innerPadMods(e)
                val fam = fontExpr(e)?.let { "fontFamily = $it, " }.orEmpty()
                val style = "TextStyle(color = ${colorExpr(e.prop(PropertyKeys.TEXT_COLOR))}, " +
                    "fontSize = ${textSizeExpr(e.prop(PropertyKeys.FONT_SIZE))}, ${fam}textAlign = ${textAlign(e)})"
                call(
                    "BasicTextField",
                    listOf("value = $v", "onValueChange = { $v = it }", "singleLine = true", "textStyle = $style"),
                    listOf(".fillMaxWidth()") + pad
                )
                line("if ($v.isEmpty()) {")
                depth++
                call("Text", listOf("text = \"${esc(e.prop(PropertyKeys.HINT))}\"") + textStyleArgs(e, true), pad.ifEmpty { null })
                depth--
                line("}")
            }
        }

        private fun listBox(e: UiElement, place: List<String>) {
            val items = e.prop(PropertyKeys.ITEMS).split('\n').filter { it.isNotEmpty() }
            val h = intOf(e, PropertyKeys.ITEM_HEIGHT)
            val mods = place + visual(e) + listOfNotNull(contentPadding(e))
            call("Column", emptyList(), mods) {
                val listExpr = items.joinToString(", ") { "\"${esc(it)}\"" }
                line("val items = listOf($listExpr)")
                line("items.forEachIndexed { index, item ->")
                depth++
                call(
                    "Box",
                    listOf("contentAlignment = Alignment.CenterStart"),
                    listOf(".fillMaxWidth()", ".height(${dp(h)})")
                ) {
                    call("Text", listOf("text = item") + textStyleArgs(e, true))
                }
                line("if (index < items.lastIndex) HorizontalDivider(color = Color(0xFFB0B7BF))")
                depth--
                line("}")
            }
        }
    }
}
