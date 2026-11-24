package com.arabicuieditor.data.planner

import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiElement

/**
 * «مخطط التطبيق» كمستند Markdown: الشاشات والتنقل بينها، النصوص الرئيسية، المزايا المستنتجة،
 * وقائمة جاهزية النشر على Google Play مع وصف مقترح. يُبنى من المشروع نفسه دون أي خدمة خارجية.
 */
object Blueprint {
    private fun label(e: UiElement): String =
        (e.properties[PropertyKeys.TEXT] ?: e.properties[PropertyKeys.HINT]).orEmpty().replace('\n', ' ').trim().take(40)

    fun markdown(project: Project): String = buildString {
        val names = project.screens.associate { it.id to it.name }
        val all = project.screens.flatMap { ElementTree.flatten(it.elements) }
        appendLine("# مخطط تطبيق «${project.name}»")
        appendLine()
        appendLine("- عدد الشاشات: ${project.screens.size}")
        appendLine("- شاشة البداية: ${names[project.startScreenId ?: project.screens.firstOrNull()?.id] ?: "-"}")
        appendLine("- عدد العناصر: ${all.size}")
        appendLine()
        appendLine("## الشاشات والتنقل")
        for (s in project.screens) {
            val els = ElementTree.flatten(s.elements)
            appendLine()
            appendLine("### ${s.name}")
            appendLine("- عدد العناصر: ${els.size}")
            val titles = els.filter { it.type == ElementTypes.TEXT }.map { label(it) }.filter { it.isNotEmpty() }.take(5)
            if (titles.isNotEmpty()) appendLine("- أبرز النصوص: ${titles.joinToString("، ")}")
            val links = els.mapNotNull { e ->
                when (val a = Interactions.clickAction(e)) {
                    is ClickAction.Navigate -> names[a.screenId]?.let { "«${label(e).ifEmpty { e.type }}» ← $it" }
                    ClickAction.Back -> "«${label(e).ifEmpty { e.type }}» ← رجوع"
                    is ClickAction.External -> "«${label(e).ifEmpty { e.type }}» ← إجراء خارجي (${a.kind})"
                    else -> null
                }
            }.distinct()
            if (links.isEmpty()) appendLine("- لا روابط تنقل") else {
                appendLine("- التنقل:")
                links.forEach { appendLine("  - $it") }
            }
        }
        appendLine()
        appendLine("## المزايا المستنتجة")
        val types = all.groupingBy { it.type }.eachCount()
        val features = buildList {
            if ((types[ElementTypes.INPUT] ?: 0) > 0) add("نماذج إدخال (${types[ElementTypes.INPUT]} حقلًا)")
            if ((types[ElementTypes.LIST] ?: 0) > 0) add("قوائم")
            if ((types[ElementTypes.IMAGE] ?: 0) > 0) add("صور وأيقونات")
            if ((types[ElementTypes.BUTTON] ?: 0) > 0) add("أزرار (${types[ElementTypes.BUTTON]})")
            if (project.screens.size > 1) add("تنقل بين ${project.screens.size} شاشات")
        }
        if (features.isEmpty()) appendLine("- لا مزايا ظاهرة بعد") else features.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## قائمة جاهزية النشر على Google Play")
        listOf(
            "اسم التطبيق (حتى 30 حرفًا): «${project.name.take(30)}»",
            "أيقونة التطبيق 512×512",
            "لقطات شاشة (2 على الأقل) من الهاتف",
            "وصف قصير (حتى 80 حرفًا) ووصف كامل",
            "سياسة خصوصية برابط عام، وتوضيح للبيانات التي يجمعها التطبيق",
            "تصنيف المحتوى والفئة العمرية",
            "رقم الإصدار (versionCode / versionName) وزيادته مع كل تحديث",
            "توقيع نسخة release بمفتاح تحتفظ به أنت (لا تفقده)",
            "اختبار التطبيق على هاتف حقيقي قبل الرفع"
        ).forEach { appendLine("- [ ] $it") }
        appendLine()
        appendLine("## وصف مقترح للمتجر (عدّله)")
        val short = "${project.name} — " + project.screens.take(3).joinToString("، ") { it.name }
        appendLine("- القصير: ${short.take(80)}")
        appendLine("- الكامل: تطبيق «${project.name}» يوفّر ${project.screens.joinToString("، ") { it.name }}. واجهة عربية بسيطة وسريعة.")
    }
}
