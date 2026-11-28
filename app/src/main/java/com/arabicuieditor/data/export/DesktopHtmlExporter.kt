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
 * يصدّر تصميم واجهة سطح مكتب كملفات HTML + CSS (+ JavaScript أو TypeScript اختياريًا).
 * دالة نقية بلا اعتماد على أندرويد.
 *
 * ما يُصدَّر: هيكل الشاشات، تنسيق كل عنصر بمواضعه وأحجامه المحسوبة من محرك التخطيط، متغيرات ألوان
 * بوضعين فاتح وداكن، اتجاه RTL/LTR، وتفاعلات معاينة (تنقل بين الشاشات، رجوع، تبديل الثيم والاتجاه).
 * ما لا يُصدَّر: منطق التطبيق (الكتل والسكربتات). الناتج قالب واجهة وليس تطبيق Windows مكتملًا.
 */
object DesktopHtmlExporter {
    enum class Script { NONE, JS, TS }

    data class Options(val script: Script = Script.JS, val rtl: Boolean = true, val dark: Boolean = false)

    fun screenId(index: Int) = "screen-${index + 1}"

    fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    private fun f(v: Float): String {
        val r = Math.round(v * 100f) / 100f
        return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    }

    /** كل ملفات المشروع: مسار نسبي ← بايتات. [images] هي صور المشروع المستعملة (اسم ← بايتات). */
    fun files(project: Project, options: Options = Options(), images: Map<String, ByteArray> = emptyMap()): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        fun put(path: String, text: String) { out[path] = text.toByteArray(Charsets.UTF_8) }
        val gen = Gen(project)
        put("index.html", gen.html(options))
        put("css/tokens.css", gen.tokensCss())
        put("css/styles.css", gen.stylesCss())
        when (options.script) {
            Script.JS -> put("js/app.js", script(ts = false))
            Script.TS -> {
                put("src/app.ts", script(ts = true))
                put("js/app.js", script(ts = false))
                put("tsconfig.json", TSCONFIG)
            }
            Script.NONE -> {}
        }
        val used = gen.usedImages
        for (name in used) images[name]?.let { out["assets/$name"] = it }
        put("README.md", gen.readme(options, used.filter { it !in images }))
        return out
    }

    /** نص HTML وحده (للاختبارات والمعاينة السريعة). */
    fun indexHtml(project: Project, options: Options = Options()): String = Gen(project).html(options)

    private class Gen(val project: Project) {
        val ds: DesignSystem = project.designSystem.completed()
        private var counter = 0
        private val css = StringBuilder()
        private val bodies = ArrayList<String>()
        val usedImages = LinkedHashSet<String>()
        private val screenIdx = project.screens.mapIndexed { i, s -> s.id to i }.toMap()

        init {
            project.screens.forEach { bodies += screenHtml(it) }
        }

        private fun i(e: UiElement, key: String): Int {
            val raw = e.prop(key)
            return raw.toIntOrNull() ?: Tokens.resolveInt(raw, ds) ?: 0
        }

        private fun rgba(argb: Int): String {
            val a = argb ushr 24 and 0xFF
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            return if (a == 255) String.format("#%02X%02X%02X", r, g, b) else "rgba($r,$g,$b,${f(a / 255f)})"
        }

        /** لون CSS: المراجع تصبح متغيرات var(--c-..) فيتبدل الوضع الداكن تلقائيًا. */
        private fun color(raw: String): String? {
            if (Tokens.isColorRef(raw)) return "var(--c-${raw.removePrefix(Tokens.COLOR)})"
            val v = ColorUtil.parse(raw) ?: return null
            return rgba(v)
        }

        private fun visible(raw: String): Boolean = (ColorUtil.parse(raw) ?: Tokens.resolveColor(raw, ds) ?: 0) ushr 24 != 0

        private fun screenHtml(s: UiScreen): String {
            val idx = screenIdx[s.id] ?: 0
            val frames = LayoutEngine.layout(s)
            val id = screenId(idx)
            css.append(".$id{width:${f(s.width)}px;height:${f(s.height)}px}\n")
            val sb = StringBuilder()
            sb.append("    <section class=\"screen\" id=\"$id\" data-title=\"${esc(s.name)}\"${if (idx == 0) "" else " hidden"}>\n")
            for (e in s.elements) element(e, frames, 0f, 0f, 3, sb)
            sb.append("    </section>\n")
            return sb.toString()
        }

        private fun action(e: UiElement): String {
            val a = Interactions.clickAction(e)
            return when (a) {
                is ClickAction.Navigate -> screenIdx[a.screenId]?.let { " data-nav=\"${screenId(it)}\"" }.orEmpty()
                ClickAction.Back -> " data-back"
                is ClickAction.External -> ExternalKinds.uri(a.kind, a.value)?.let { " data-href=\"${esc(it)}\"" }.orEmpty()
                else -> ""
            }
        }

        private fun element(src: UiElement, frames: LayoutResult, px: Float, py: Float, depth: Int, sb: StringBuilder) {
            val e = ElementState.resolved(src)
            val fr = frames.frameOf(e.id) ?: return
            val n = ++counter
            val cls = "e$n"
            val rules = ArrayList<String>()
            rules += "inset-inline-start:${f(fr.x - px)}px"
            rules += "top:${f(fr.y - py)}px"
            rules += "width:${f(fr.w)}px"
            rules += "height:${f(fr.h)}px"
            val bgRaw = e.prop(PropertyKeys.BG_COLOR)
            val bg = if (Tokens.isColorRef(bgRaw) || visible(bgRaw)) color(bgRaw) else null
            if (bg != null && e.type != ElementTypes.DIVIDER) rules += "background:$bg"
            val r = i(e, PropertyKeys.CORNER_RADIUS)
            if (r > 0) rules += "border-radius:${r}px"
            val bw = i(e, PropertyKeys.BORDER_WIDTH)
            val shadows = ArrayList<String>()
            if (bw > 0) color(e.prop(PropertyKeys.BORDER_COLOR))?.let { shadows += "inset 0 0 0 ${bw}px $it" }
            if (e.type == ElementTypes.CARD) shadows += "0 2px 6px rgba(0,0,0,.2)"
            if (shadows.isNotEmpty()) rules += "box-shadow:${shadows.joinToString(",")}"
            val op = e.prop(PropertyKeys.OPACITY).toIntOrNull() ?: 100
            if (op in 0..99) rules += "opacity:${f(op / 100f)}"
            val rot = e.prop(PropertyKeys.ROTATION).toFloatOrNull() ?: 0f
            val flipX = e.prop(PropertyKeys.FLIP_X) == "true"
            val flipY = e.prop(PropertyKeys.FLIP_Y) == "true"
            if (rot != 0f || flipX || flipY) {
                rules += "transform:rotate(${f(rot)}deg)${if (flipX) " scaleX(-1)" else ""}${if (flipY) " scaleY(-1)" else ""}"
            }
            val hidden = e.prop(PropertyKeys.VISIBLE) == "false"
            val act = action(e)
            val ind = "    ".repeat(depth)
            val attrs = "class=\"el $cls\"$act${if (hidden) " hidden" else ""}"

            fun textRules() {
                color(e.prop(PropertyKeys.TEXT_COLOR))?.let { rules += "color:$it" }
                rules += "font-size:${i(e, PropertyKeys.FONT_SIZE)}px"
                when (e.prop(PropertyKeys.FONT_WEIGHT)) {
                    FontWeights.BOLD -> rules += "font-weight:700"
                    FontWeights.MEDIUM -> rules += "font-weight:500"
                }
                val justify = when (e.prop(PropertyKeys.TEXT_ALIGN)) {
                    TextAligns.CENTER -> "center"
                    TextAligns.END -> "flex-end"
                    else -> "flex-start"
                }
                rules += "justify-content:$justify"
                rules += "text-align:${when (e.prop(PropertyKeys.TEXT_ALIGN)) { TextAligns.CENTER -> "center"; TextAligns.END -> "end"; else -> "start" }}"
            }

            when (e.type) {
                ElementTypes.TEXT -> {
                    textRules()
                    sb.append("$ind<div $attrs>${esc(e.prop(PropertyKeys.TEXT))}</div>\n")
                }
                ElementTypes.BUTTON -> {
                    textRules()
                    rules += "justify-content:center"
                    sb.append("$ind<button type=\"button\" $attrs>${esc(e.prop(PropertyKeys.TEXT))}</button>\n")
                }
                ElementTypes.INPUT -> {
                    textRules()
                    sb.append("$ind<input type=\"text\" $attrs placeholder=\"${esc(e.prop(PropertyKeys.HINT))}\">\n")
                }
                ElementTypes.DIVIDER -> {
                    val c = color(bgRaw) ?: "var(--c-border)"
                    rules += "background:$c"
                    sb.append("$ind<div $attrs role=\"separator\"></div>\n")
                }
                ElementTypes.SPACER -> sb.append("$ind<div $attrs aria-hidden=\"true\"></div>\n")
                ElementTypes.IMAGE -> {
                    val icon = e.prop(PropertyKeys.ICON)
                    val srcName = e.prop(PropertyKeys.IMAGE_SRC)
                    if (IconCatalog.isValid(icon)) {
                        val ic = color(e.prop(PropertyKeys.ICON_COLOR)) ?: "currentColor"
                        sb.append("$ind<div $attrs role=\"img\" aria-label=\"${esc(icon)}\"><svg viewBox=\"0 0 24 24\" width=\"60%\" height=\"60%\" fill=\"$ic\"><circle cx=\"12\" cy=\"12\" r=\"10\"/></svg></div>\n")
                        rules += "display:flex;align-items:center;justify-content:center"
                    } else if (srcName.isNotEmpty() && ImageNames.isValid(srcName)) {
                        usedImages += srcName
                        rules += "object-fit:${when (e.prop(PropertyKeys.IMAGE_FIT)) { ImageFits.FIT -> "contain"; ImageFits.FILL -> "fill"; else -> "cover" }}"
                        sb.append("$ind<img $attrs src=\"assets/${esc(srcName)}\" alt=\"\">\n")
                    } else {
                        sb.append("$ind<div $attrs role=\"img\" aria-label=\"placeholder\"></div>\n")
                    }
                }
                ElementTypes.LIST -> {
                    textRules()
                    rules += "justify-content:flex-start"
                    val h = i(e, PropertyKeys.ITEM_HEIGHT).coerceAtLeast(1)
                    val items = e.prop(PropertyKeys.ITEMS).split('\n').filter { it.isNotEmpty() }
                    sb.append("$ind<ul $attrs>\n")
                    items.forEach { sb.append("$ind  <li style=\"height:${h}px\">${esc(it)}</li>\n") }
                    sb.append("$ind</ul>\n")
                }
                else -> {
                    // حاوية أو بطاقة: الأبناء مواضعهم نسبة إلى هذه الحاوية.
                    if (e.children.isEmpty()) sb.append("$ind<div $attrs></div>\n")
                    else {
                        sb.append("$ind<div $attrs>\n")
                        for (c in e.children) element(c, frames, fr.x, fr.y, depth + 1, sb)
                        sb.append("$ind</div>\n")
                    }
                }
            }
            css.append(".$cls{${rules.joinToString(";")}}\n")
        }

        fun html(o: Options): String = buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"${if (o.rtl) "ar" else "en"}\" dir=\"${if (o.rtl) "rtl" else "ltr"}\"${if (o.dark) " data-theme=\"dark\"" else ""}>\n<head>\n")
            append("  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("  <title>${esc(project.name)}</title>\n")
            append("  <link rel=\"stylesheet\" href=\"css/tokens.css\">\n  <link rel=\"stylesheet\" href=\"css/styles.css\">\n")
            append("</head>\n<body>\n")
            if (o.script != Script.NONE) {
                append("  <header class=\"toolbar\">\n")
                append("    <button type=\"button\" id=\"toggle-theme\">Dark / Light</button>\n")
                append("    <button type=\"button\" id=\"toggle-dir\">RTL / LTR</button>\n  </header>\n")
            }
            append("  <main class=\"stage\" id=\"stage\">\n")
            bodies.forEach { append(it) }
            append("  </main>\n")
            if (o.script != Script.NONE) append("  <script src=\"js/app.js\"></script>\n")
            append("</body>\n</html>\n")
        }

        fun tokensCss(): String = buildString {
            fun vars(d: DesignSystem) = Tokens.colorNames.joinToString("") { "  --c-$it:${rgba(d.color(it))};\n" }
            val dark = DarkMode.apply(ds)
            append("/* ألوان نظام التصميم. الوضع الداكن يُفعَّل بـ data-theme=\"dark\" أو تفضيل النظام. */\n")
            append(":root {\n").append(vars(ds)).append("}\n")
            append("[data-theme=\"dark\"] {\n").append(vars(dark)).append("}\n")
            append("@media (prefers-color-scheme: dark) {\n  :root:not([data-theme=\"light\"]) {\n")
            append(vars(dark).replace("  --", "    --")).append("  }\n}\n")
        }

        fun stylesCss(): String = buildString {
            append("/* تنسيق الواجهة: مواضع وأحجام العناصر محسوبة من محرك التخطيط في المحرر. */\n")
            append("* { box-sizing: border-box; }\n")
            append("body { margin: 0; background: #1f2630; font-family: \"Segoe UI Variable\", \"Segoe UI\", system-ui, sans-serif; }\n")
            append(".toolbar { position: fixed; top: 8px; inset-inline-end: 8px; z-index: 10; display: flex; gap: 8px; }\n")
            append(".toolbar button { font: inherit; padding: 6px 12px; border-radius: 6px; border: 1px solid #888; background: #fff; cursor: pointer; }\n")
            append(".stage { display: flex; justify-content: center; padding: 48px 16px 16px; }\n")
            append(".screen { position: relative; overflow: hidden; background: var(--c-background); color: var(--c-text); transform-origin: top center; }\n")
            append(".screen[hidden] { display: none; }\n")
            append(".el { position: absolute; margin: 0; padding: 0; }\n")
            append("div.el, button.el, ul.el { display: flex; align-items: center; }\n")
            append("ul.el { flex-direction: column; align-items: stretch; list-style: none; }\n")
            append("ul.el li { display: flex; align-items: center; }\n")
            append("button.el, input.el { border: 0; font-family: inherit; }\n")
            append("input.el { padding-inline: 8px; outline: none; }\n")
            append("[data-nav], [data-back], [data-href] { cursor: pointer; }\n")
            append("button.el:hover { filter: brightness(1.08); }\nbutton.el:active { filter: brightness(.9); }\n")
            append("input.el:focus { box-shadow: 0 0 0 2px var(--c-primary); }\n")
            append(css)
        }

        fun readme(o: Options, missing: List<String>): String = buildString {
            append("# ${project.name}\n\n")
            append("قالب واجهة سطح مكتب (HTML + CSS${when (o.script) { Script.JS -> " + JavaScript"; Script.TS -> " + TypeScript"; Script.NONE -> "" }}) مصدَّر من «استوديو إنشاء التطبيقات».\n\n")
            append("> هذا تصميم واجهة فقط، وليس تطبيق Windows مكتملًا. يلزم استكمال المنطق البرمجي لاحقًا.\n\n")
            append("## التشغيل\nافتح `index.html` في المتصفح. التنقل بين الشاشات وتبديل الثيم والاتجاه من الأزرار أعلى الصفحة.\n\n")
            append("## الملفات\n- `index.html`: كل الشاشات.\n- `css/tokens.css`: ألوان نظام التصميم (فاتح وداكن).\n- `css/styles.css`: مواضع وأحجام العناصر.\n")
            if (o.script != Script.NONE) append("- `js/app.js`: تفاعلات المعاينة (تنقل، رجوع، ثيم، اتجاه، ملاءمة النافذة).\n")
            if (o.script == Script.TS) append("- `src/app.ts` و`tsconfig.json`: النسخة المكتوبة بـ TypeScript (`npx tsc` تُنتج js/app.js).\n")
            if (missing.isNotEmpty()) append("\n## صور ناقصة\nضع هذه الملفات في مجلد `assets/`: ${missing.joinToString("، ")}\n")
            append("\n## قيود\n- التخطيط مطلق المواضع بقياسات المحرر، وتُلاءَم الشاشة مع النافذة بالتحجيم.\n- الأيقونات تُصدَّر رمزًا بديلًا بسيطًا؛ استبدلها بخط أيقونات تختاره.\n- الكتل والسكربتات في المحرر لا تُصدَّر.\n")
        }
    }

    private const val TSCONFIG = """{
  "compilerOptions": {
    "target": "ES2019",
    "lib": ["ES2019", "DOM"],
    "outDir": "js",
    "rootDir": "src",
    "strict": true
  },
  "include": ["src"]
}
"""

    /** سكربت المعاينة. العلامات ⟦...⟧ تُحوَّل إلى تعليقات الأنواع في TypeScript وتُحذف في JavaScript. */
    private fun script(ts: Boolean): String {
        val t = Regex("⟦([^⟧]*)⟧")
        val body = SCRIPT.let { src -> if (ts) t.replace(src) { it.groupValues[1] } else t.replace(src, "") }
        return "// تفاعلات المعاينة فقط (لا منطق تطبيق).\n$body"
    }

    private val SCRIPT = """
(function () {
  var stage = document.getElementById('stage')⟦ as HTMLElement⟧;
  var screens = Array.prototype.slice.call(document.querySelectorAll('.screen'))⟦ as HTMLElement[]⟧;
  var history⟦: string[]⟧ = [];

  function fit()⟦: void⟧ {
    var current = screens.filter(function (s) { return !s.hidden; })[0];
    if (!current) { return; }
    current.style.transform = 'none';
    var scale = Math.min(1, (window.innerWidth - 32) / current.offsetWidth);
    current.style.transform = scale < 1 ? 'scale(' + scale + ')' : 'none';
    stage.style.height = scale < 1 ? Math.ceil(current.offsetHeight * scale + 64) + 'px' : '';
  }

  function show(id⟦: string⟧, push⟦: boolean⟧)⟦: void⟧ {
    var current = screens.filter(function (s) { return !s.hidden; })[0];
    if (push && current) { history.push(current.id); }
    screens.forEach(function (s) { s.hidden = s.id !== id; });
    fit();
  }

  document.addEventListener('click', function (ev) {
    var target = (ev.target⟦ as Element⟧).closest('[data-nav],[data-back],[data-href]')⟦ as HTMLElement | null⟧;
    if (!target) { return; }
    if (target.hasAttribute('data-nav')) { show(target.getAttribute('data-nav')⟦ as string⟧, true); }
    else if (target.hasAttribute('data-back')) { var prev = history.pop(); if (prev) { show(prev, false); } }
    else if (target.hasAttribute('data-href')) { window.open(target.getAttribute('data-href')⟦ as string⟧, '_blank'); }
  });

  var root = document.documentElement;
  var themeBtn = document.getElementById('toggle-theme');
  var dirBtn = document.getElementById('toggle-dir');
  if (themeBtn) { themeBtn.addEventListener('click', function () {
    var dark = root.getAttribute('data-theme') === 'dark' ||
      (!root.getAttribute('data-theme') && window.matchMedia('(prefers-color-scheme: dark)').matches);
    root.setAttribute('data-theme', dark ? 'light' : 'dark');
  }); }
  if (dirBtn) { dirBtn.addEventListener('click', function () {
    var rtl = root.getAttribute('dir') === 'rtl';
    root.setAttribute('dir', rtl ? 'ltr' : 'rtl');
    root.setAttribute('lang', rtl ? 'en' : 'ar');
  }); }

  window.addEventListener('resize', fit);
  fit();
})();
""".trimStart()
}
