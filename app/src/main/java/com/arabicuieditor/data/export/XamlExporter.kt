package com.arabicuieditor.data.export

import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ColorUtil
import com.arabicuieditor.data.model.DarkMode
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.ElementState
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.ExternalKinds
import com.arabicuieditor.data.model.FontWeights
import com.arabicuieditor.data.model.IconCatalog
import com.arabicuieditor.data.model.ImageFits
import com.arabicuieditor.data.model.ImageNames
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.TextAligns
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.LayoutResult

/**
 * يصدّر تصميم واجهة Windows بصيغة XAML لـ WinUI 3 أو WPF. دالة نقية بلا اعتماد على أندرويد.
 *
 * كل شاشة صفحة XAML بـ Canvas مواضعه وأحجامه من محرك التخطيط. الألوان المرتبطة بنظام التصميم
 * تُصدَّر موارد فرش (AppPrimaryBrush...) بثيمين فاتح وداكن. لا يُولَّد أي كود C# ولا x:Class:
 * الملفات تصميم واجهة تُنسخ إلى مشروعك ثم تُستكمل برمجيًا. التنقل يظهر في Tag بصيغة nav:اسم-الصفحة.
 */
object XamlExporter {
    enum class Flavor { WINUI3, WPF }

    data class Options(val flavor: Flavor = Flavor.WINUI3, val rtl: Boolean = true)

    fun pageName(index: Int) = "Screen${index + 1}Page"

    private fun esc(s: String): String = (if (s.startsWith("{")) "{}" else "") + buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\n', '\r' -> append("&#10;")
            else -> append(c)
        }
    }

    private fun f(v: Float): String {
        val r = Math.round(v * 100f) / 100f
        return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    }

    private fun cap(n: String) = n.replaceFirstChar { it.uppercase() }
    private fun brushKey(token: String) = "App${cap(token)}Brush"

    fun files(project: Project, options: Options = Options(), images: Map<String, ByteArray> = emptyMap()): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        fun put(path: String, text: String) { out[path] = text.toByteArray(Charsets.UTF_8) }
        val gen = Gen(project, options)
        project.screens.forEachIndexed { i, s -> put("Views/${pageName(i)}.xaml", gen.page(s, i)) }
        val ds = project.designSystem.completed()
        if (options.flavor == Flavor.WINUI3) {
            put("Themes/Colors.xaml", gen.winUiColors(ds))
        } else {
            put("Themes/Colors.xaml", gen.wpfColors(ds))
            put("Themes/Colors.Dark.xaml", gen.wpfColors(DarkMode.apply(ds)))
        }
        for (name in gen.usedImages) images[name]?.let { out["Assets/$name"] = it }
        put("README.md", gen.readme(gen.usedImages.filter { it !in images }))
        return out
    }

    /** نص صفحة واحدة (للاختبارات). */
    fun pageXaml(project: Project, index: Int = 0, options: Options = Options()): String =
        Gen(project, options).page(project.screens[index], index)

    private class Gen(val project: Project, val o: Options) {
        val ds: DesignSystem = project.designSystem.completed()
        val usedImages = LinkedHashSet<String>()
        private val screenIdx = project.screens.mapIndexed { i, s -> s.id to i }.toMap()
        private val wpf = o.flavor == Flavor.WPF

        private fun i(e: UiElement, key: String): Int {
            val raw = e.prop(key)
            return raw.toIntOrNull() ?: Tokens.resolveInt(raw, ds) ?: 0
        }

        private fun argb(v: Int) = String.format("#%08X", v)

        /** فرشاة: مرجع نظام التصميم أو لون ثابت؛ null إن كان شفافًا/غير صالح. */
        private fun brush(raw: String): String? {
            if (Tokens.isColorRef(raw)) {
                val key = brushKey(raw.removePrefix(Tokens.COLOR))
                return if (wpf) "{DynamicResource $key}" else "{ThemeResource $key}"
            }
            val v = ColorUtil.parse(raw) ?: return null
            return if (v ushr 24 == 0) null else argb(v)
        }

        private fun nav(e: UiElement): String? = when (val a = Interactions.clickAction(e)) {
            is ClickAction.Navigate -> screenIdx[a.screenId]?.let { "nav:${pageName(it)}" }
            ClickAction.Back -> "nav:back"
            is ClickAction.External -> ExternalKinds.uri(a.kind, a.value)?.let { "open:$it" }
            else -> null
        }

        fun page(s: UiScreen, idx: Int): String {
            val frames = LayoutEngine.layout(s)
            val sb = StringBuilder()
            val tag = "Page"
            sb.append("<!-- ${project.name.replace("--", "-")} — ${s.name.replace("--", "-")}: تصميم واجهة فقط (بلا ربط بصنف ولا كود). -->\n")
            sb.append("<$tag\n")
            sb.append("    xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\"\n")
            sb.append("    xmlns:x=\"http://schemas.microsoft.com/winfx/2006/xaml\"\n")
            sb.append("    Title=\"${esc(s.name)}\"\n")
            sb.append("    FlowDirection=\"${if (o.rtl) "RightToLeft" else "LeftToRight"}\"\n")
            sb.append("    FontFamily=\"Segoe UI Variable, Segoe UI\">\n")
            sb.append("  <Canvas Width=\"${f(s.width)}\" Height=\"${f(s.height)}\" Background=\"${brush(Tokens.color(Tokens.BACKGROUND))}\">\n")
            for (e in s.elements) element(e, frames, 0f, 0f, 2, sb)
            sb.append("  </Canvas>\n</$tag>\n")
            return sb.toString()
        }

        private fun element(src: UiElement, frames: LayoutResult, px: Float, py: Float, depth: Int, sb: StringBuilder) {
            val e = ElementState.resolved(src)
            val fr = frames.frameOf(e.id) ?: return
            val ind = "  ".repeat(depth)
            val a = ArrayList<String>()
            a += "Canvas.Left=\"${f(fr.x - px)}\""
            a += "Canvas.Top=\"${f(fr.y - py)}\""
            a += "Width=\"${f(fr.w)}\""
            a += "Height=\"${f(fr.h)}\""
            nav(e)?.let { a += "Tag=\"${esc(it)}\"" }
            val op = e.prop(PropertyKeys.OPACITY).toIntOrNull() ?: 100
            if (op in 0..99) a += "Opacity=\"${f(op / 100f)}\""
            if (e.prop(PropertyKeys.VISIBLE) == "false") a += "Visibility=\"Collapsed\""
            val rot = e.prop(PropertyKeys.ROTATION).toFloatOrNull() ?: 0f
            val flipX = e.prop(PropertyKeys.FLIP_X) == "true"
            val flipY = e.prop(PropertyKeys.FLIP_Y) == "true"
            val transform = rot != 0f || flipX || flipY
            if (transform) a += "RenderTransformOrigin=\"0.5,0.5\""

            val bg = brush(e.prop(PropertyKeys.BG_COLOR))
            val r = i(e, PropertyKeys.CORNER_RADIUS)
            val bw = i(e, PropertyKeys.BORDER_WIDTH)
            val bc = brush(e.prop(PropertyKeys.BORDER_COLOR))

            fun boxAttrs(list: MutableList<String>) {
                bg?.let { list += "Background=\"$it\"" }
                if (r > 0) list += "CornerRadius=\"$r\""
                if (bw > 0 && bc != null) { list += "BorderThickness=\"$bw\""; list += "BorderBrush=\"$bc\"" }
            }

            fun fontAttrs(list: MutableList<String>) {
                brush(e.prop(PropertyKeys.TEXT_COLOR))?.let { list += "Foreground=\"$it\"" }
                list += "FontSize=\"${i(e, PropertyKeys.FONT_SIZE)}\""
                when (e.prop(PropertyKeys.FONT_WEIGHT)) {
                    FontWeights.BOLD -> list += "FontWeight=\"Bold\""
                    FontWeights.MEDIUM -> list += "FontWeight=\"Medium\""
                }
            }

            val align = when (e.prop(PropertyKeys.TEXT_ALIGN)) { TextAligns.CENTER -> "Center"; TextAligns.END -> "Right"; else -> "Left" }
            // محاذاة منطقية: في RTL تعني البداية يمينًا؛ TextAlignment=Left/Right في XAML فيزيائية، لذا نستعمل Start/End عبر HorizontalAlignment.
            val hAlign = when (e.prop(PropertyKeys.TEXT_ALIGN)) { TextAligns.CENTER -> "Center"; TextAligns.END -> "Right"; else -> "Left" }

            fun transformXml(): String = if (!transform) "" else buildString {
                val sx = if (flipX) -1 else 1
                val sy = if (flipY) -1 else 1
                append("\n$ind  <Border.RenderTransform>\n$ind    <TransformGroup>\n")
                if (flipX || flipY) append("$ind      <ScaleTransform ScaleX=\"$sx\" ScaleY=\"$sy\"/>\n")
                if (rot != 0f) append("$ind      <RotateTransform Angle=\"${f(rot)}\"/>\n")
                append("$ind    </TransformGroup>\n$ind  </Border.RenderTransform>\n")
            }

            fun wrapBorder(inner: (String) -> String) {
                val list = ArrayList(a)
                boxAttrs(list)
                val tfm = transformXml()
                val shadow = if (wpf && e.type == ElementTypes.CARD)
                    "\n$ind  <Border.Effect><DropShadowEffect BlurRadius=\"6\" ShadowDepth=\"2\" Opacity=\"0.25\"/></Border.Effect>" else ""
                sb.append("$ind<Border ${list.joinToString(" ")}>$shadow$tfm\n")
                sb.append(inner("$ind  "))
                sb.append("$ind</Border>\n")
            }

            when (e.type) {
                ElementTypes.TEXT -> wrapBorder { p ->
                    val t = arrayListOf("VerticalAlignment=\"Center\"", "HorizontalAlignment=\"$hAlign\"", "TextAlignment=\"$align\"", "TextWrapping=\"Wrap\"", "Text=\"${esc(e.prop(PropertyKeys.TEXT))}\"")
                    fontAttrs(t)
                    "$p<TextBlock ${t.joinToString(" ")}/>\n"
                }
                ElementTypes.BUTTON -> {
                    val list = ArrayList(a)
                    boxAttrs(list)
                    fontAttrs(list)
                    list += "Content=\"${esc(e.prop(PropertyKeys.TEXT))}\""
                    list += "Padding=\"0\""
                    list += "HorizontalContentAlignment=\"Center\""
                    sb.append("$ind<Button ${list.joinToString(" ")}/>\n")
                }
                ElementTypes.INPUT -> {
                    val list = ArrayList(a)
                    boxAttrs(list)
                    fontAttrs(list)
                    list += "VerticalContentAlignment=\"Center\""
                    if (wpf) {
                        list += "ToolTip=\"${esc(e.prop(PropertyKeys.HINT))}\""
                        list += "Text=\"${esc(e.prop(PropertyKeys.HINT))}\""
                    } else list += "PlaceholderText=\"${esc(e.prop(PropertyKeys.HINT))}\""
                    sb.append("$ind<TextBox ${list.joinToString(" ")}/>\n")
                }
                ElementTypes.DIVIDER -> {
                    val list = ArrayList(a)
                    list += "Fill=\"${brush(e.prop(PropertyKeys.BG_COLOR)) ?: brush(Tokens.color(Tokens.BORDER))}\""
                    sb.append("$ind<Rectangle ${list.joinToString(" ")}/>\n")
                }
                ElementTypes.SPACER -> sb.append("$ind<Border ${a.joinToString(" ")}/>\n")
                ElementTypes.IMAGE -> {
                    val icon = e.prop(PropertyKeys.ICON)
                    val src = e.prop(PropertyKeys.IMAGE_SRC)
                    when {
                        IconCatalog.isValid(icon) -> wrapBorder { p ->
                            "$p<Ellipse Width=\"${f(minOf(fr.w, fr.h) * 0.6f)}\" Height=\"${f(minOf(fr.w, fr.h) * 0.6f)}\" " +
                                "Fill=\"${brush(e.prop(PropertyKeys.ICON_COLOR)) ?: brush(Tokens.color(Tokens.TEXT_COLOR))}\" " +
                                "HorizontalAlignment=\"Center\" VerticalAlignment=\"Center\" ToolTipService.ToolTip=\"${esc(icon)}\"/>\n"
                        }
                        src.isNotEmpty() && ImageNames.isValid(src) -> {
                            usedImages += src
                            val stretch = when (e.prop(PropertyKeys.IMAGE_FIT)) { ImageFits.FIT -> "Uniform"; ImageFits.FILL -> "Fill"; else -> "UniformToFill" }
                            wrapBorder { p -> "$p<Image Source=\"../Assets/${esc(src)}\" Stretch=\"$stretch\"/>\n" }
                        }
                        else -> sb.append("$ind<Border ${(a + listOfNotNull(bg?.let { "Background=\"$it\"" })).joinToString(" ")}/>\n")
                    }
                }
                ElementTypes.LIST -> wrapBorder { p ->
                    val h = i(e, PropertyKeys.ITEM_HEIGHT).coerceAtLeast(1)
                    val items = e.prop(PropertyKeys.ITEMS).split('\n').filter { it.isNotEmpty() }
                    val rows = items.joinToString("") {
                        val t = arrayListOf("Height=\"$h\"", "VerticalAlignment=\"Center\"", "Text=\"${esc(it)}\"")
                        fontAttrs(t)
                        "$p  <TextBlock ${t.joinToString(" ")}/>\n"
                    }
                    "$p<StackPanel>\n$rows$p</StackPanel>\n"
                }
                else -> wrapBorder { p ->
                    if (e.children.isEmpty()) "" else {
                        val inner = StringBuilder()
                        for (c in e.children) element(c, frames, fr.x, fr.y, depth + 2, inner)
                        "$p<Canvas>\n$inner$p</Canvas>\n"
                    }
                }
            }
        }

        private fun brushes(d: DesignSystem, p: String) =
            Tokens.colorNames.joinToString("") { "$p<SolidColorBrush x:Key=\"${brushKey(it)}\" Color=\"${argb(d.color(it))}\"/>\n" }

        fun winUiColors(d: DesignSystem): String = buildString {
            append("<ResourceDictionary\n    xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\"\n    xmlns:x=\"http://schemas.microsoft.com/winfx/2006/xaml\">\n")
            append("  <ResourceDictionary.ThemeDictionaries>\n")
            append("    <ResourceDictionary x:Key=\"Light\">\n").append(brushes(d, "      ")).append("    </ResourceDictionary>\n")
            append("    <ResourceDictionary x:Key=\"Dark\">\n").append(brushes(DarkMode.apply(d), "      ")).append("    </ResourceDictionary>\n")
            append("  </ResourceDictionary.ThemeDictionaries>\n</ResourceDictionary>\n")
        }

        fun wpfColors(d: DesignSystem): String = buildString {
            append("<ResourceDictionary\n    xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\"\n    xmlns:x=\"http://schemas.microsoft.com/winfx/2006/xaml\">\n")
            append(brushes(d, "  "))
            append("</ResourceDictionary>\n")
        }

        fun readme(missing: List<String>): String = buildString {
            val name = if (wpf) "WPF" else "WinUI 3"
            append("# ${project.name} — واجهة $name (XAML)\n\n")
            append("مصدَّر من «استوديو إنشاء التطبيقات». **تصميم واجهة فقط** وليس تطبيق Windows مكتملًا.\n\n")
            append("## الاستعمال\n")
            append("1. أنشئ مشروع $name (${if (wpf) "WPF App" else "WinUI 3 Blank App"}) في Visual Studio.\n")
            append("2. انسخ مجلدات `Views` و`Themes` و`Assets` إلى المشروع.\n")
            if (wpf) append("3. ادمج `Themes/Colors.xaml` في موارد التطبيق، وبدّل إلى `Colors.Dark.xaml` للوضع الداكن.\n")
            else append("3. ادمج `Themes/Colors.xaml` في `Application.Resources`؛ يتبدل الثيم الفاتح والداكن تلقائيًا (ThemeResource).\n")
            append("4. الصفحات بلا `x:Class` عمدًا؛ أضف الصنف والكود حين تدمجها في مشروعك.\n\n")
            append("## التنقل\nالعناصر التي لها إجراء تحمل `Tag`: `nav:Screen2Page` (انتقال)، `nav:back` (رجوع)، `open:رابط` (فتح خارجي). اربطها بكودك.\n")
            if (missing.isNotEmpty()) append("\n## صور ناقصة\nضع في مجلد `Assets/`: ${missing.joinToString("، ")}\n")
            append("\n## قيود\n- التخطيط Canvas بمواضع وأحجام المحرر (لا يتمدد تلقائيًا مع النافذة).\n")
            if (wpf) append("- حقل الإدخال في WPF لا يملك نصًا إرشاديًا أصليًا، فُضِع التلميح كنص أولي.\n")
            else append("- الظل على البطاقات غير مُصدَّر في WinUI 3.\n")
            append("- الأيقونات دوائر بديلة؛ استبدلها بأيقونات Fluent التي تختارها.\n")
        }
    }
}
