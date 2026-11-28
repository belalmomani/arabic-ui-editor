package com.arabicuieditor.data.export

import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.ImageNames
import com.arabicuieditor.data.model.Platforms
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.prop
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * يجمع ملفات تصميم واجهة Windows حسب تقنية المشروع (WinUI 3 / WPF / HTML+CSS / Flutter Desktop) ويكتبها ZIP.
 * تصميم واجهة فقط: لا منطق تطبيق ولا ادعاء بأن الناتج تطبيق Windows مكتمل.
 */
object WindowsProjectExporter {
    data class Options(val script: DesktopHtmlExporter.Script = DesktopHtmlExporter.Script.JS, val rtl: Boolean = true)

    fun usedImages(project: Project): List<String> =
        project.screens.flatMap { ElementTree.flatten(it.elements) }
            .filter { it.type == ElementTypes.IMAGE }
            .map { it.prop(PropertyKeys.IMAGE_SRC) }
            .filter { ImageNames.isValid(it) }
            .distinct()

    fun zipName(project: Project): String = "windows-ui-" + project.uiTech.ifEmpty { Platforms.TECH_WINUI } + ".zip"

    fun files(project: Project, options: Options = Options(), images: Map<String, ByteArray> = emptyMap()): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        val tech = Platforms.normalizeTech(project.uiTech).ifEmpty { Platforms.TECH_WINUI }
        when (tech) {
            Platforms.TECH_HTML -> out.putAll(DesktopHtmlExporter.files(project, DesktopHtmlExporter.Options(options.script, options.rtl), images))
            Platforms.TECH_FLUTTER -> out.putAll(flutterFiles(project, images))
            Platforms.TECH_WPF -> out.putAll(XamlExporter.files(project, XamlExporter.Options(XamlExporter.Flavor.WPF, options.rtl), images))
            else -> out.putAll(XamlExporter.files(project, XamlExporter.Options(XamlExporter.Flavor.WINUI3, options.rtl), images))
        }
        return out
    }

    /** ملف العرض السريع في نافذة التصدير: الصفحة الأولى أو main.dart أو index.html. */
    fun preview(project: Project, options: Options = Options()): String {
        val tech = Platforms.normalizeTech(project.uiTech).ifEmpty { Platforms.TECH_WINUI }
        return when (tech) {
            Platforms.TECH_HTML -> DesktopHtmlExporter.indexHtml(project, DesktopHtmlExporter.Options(options.script, options.rtl))
            Platforms.TECH_FLUTTER -> FlutterExporter.export(project)
            Platforms.TECH_WPF -> XamlExporter.pageXaml(project, 0, XamlExporter.Options(XamlExporter.Flavor.WPF, options.rtl))
            else -> XamlExporter.pageXaml(project, 0, XamlExporter.Options(XamlExporter.Flavor.WINUI3, options.rtl))
        }
    }

    private fun flutterFiles(project: Project, images: Map<String, ByteArray>): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        fun put(path: String, text: String) { out[path] = text.toByteArray(Charsets.UTF_8) }
        put("lib/main.dart", FlutterExporter.export(project))
        val used = usedImages(project)
        val assets = used.filter { it in images }
        put(
            "pubspec.yaml",
            buildString {
                append("name: windows_ui_design\ndescription: واجهة Flutter Desktop مصدَّرة من استوديو إنشاء التطبيقات (تصميم فقط).\n")
                append("publish_to: none\nversion: 0.1.0\n\nenvironment:\n  sdk: '>=3.3.0 <4.0.0'\n\n")
                append("dependencies:\n  flutter:\n    sdk: flutter\n\nflutter:\n  uses-material-design: true\n")
                if (assets.isNotEmpty()) {
                    append("  assets:\n")
                    assets.forEach { append("    - assets/$it\n") }
                }
            }
        )
        for (n in assets) out["assets/$n"] = images.getValue(n)
        put(
            "README.md",
            buildString {
                append("# ${project.name} — واجهة Flutter Desktop\n\n")
                append("مصدَّر من «استوديو إنشاء التطبيقات». **تصميم واجهة فقط** وليس تطبيق Windows مكتملًا.\n\n")
                append("## الاستعمال\n1. نفّذ `flutter create --platforms=windows .` في مجلد جديد.\n")
                append("2. استبدل `lib/main.dart` و`pubspec.yaml` بالملفين هنا وانسخ مجلد `assets`.\n")
                append("3. شغّل `flutter run -d windows`.\n\n")
                append("## قيود\n- التخطيط بمواضع وأحجام المحرر (Stack) ولا يتمدد تلقائيًا مع النافذة.\n- الإجراءات الخارجية والمنطق تعليقات TODO تستكملها أنت.\n")
                val missing = used.filter { it !in images }
                if (missing.isNotEmpty()) append("- صور ناقصة، ضعها في assets/: ${missing.joinToString("، ")}\n")
            }
        )
        return out
    }

    fun collectFiles(project: Project, imagesDir: File?, options: Options = Options()): Map<String, ByteArray> {
        val images = LinkedHashMap<String, ByteArray>()
        for (name in usedImages(project)) {
            val f = imagesDir?.let { File(it, name) }
            if (f != null && f.isFile) images[name] = f.readBytes()
        }
        return files(project, options, images)
    }

    fun writeZip(project: Project, imagesDir: File?, out: OutputStream, options: Options = Options()) {
        val files = collectFiles(project, imagesDir, options)
        ZipOutputStream(out).use { zip ->
            for ((path, bytes) in files) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
