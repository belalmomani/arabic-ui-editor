package com.arabicuieditor.data.model

import java.util.UUID

/**
 * إصدار مخطط بيانات المشروع الحالي. يُرفع عند أي تغيير في صيغة JSON.
 * 1: المرحلة 2 (مشروع وشاشات بلا تخطيط).
 * 2: المرحلة 3 (تخطيط العناصر وأبعاد الشاشة).
 * 3: المرحلة 5 (شاشة البداية، حجم المعاينة، تخطيط الشاشة الجذري، تخطيطات Row/Column/Box، أبعاد نسبية).
 */
const val CURRENT_SCHEMA_VERSION = 3

/**
 * طريقة تحديد بُعد العنصر:
 * - FIXED: قيمة ثابتة بوحدة dp.
 * - MATCH_PARENT: يملأ المتاح داخل الأب.
 * - WRAP_CONTENT: بحجم محتواه (للعنصر الورقي يُستعمل value كحجم احتياطي).
 * - FRACTION: نسبة مئوية (value من 1 إلى 100) من المتاح داخل الأب.
 */
enum class SizeMode { FIXED, MATCH_PARENT, WRAP_CONTENT, FRACTION }

data class SizeSpec(val mode: SizeMode = SizeMode.FIXED, val value: Float = 0f) {
    companion object {
        fun fixed(value: Float) = SizeSpec(SizeMode.FIXED, value)
    }
}

/**
 * تموضع العنصر داخل أبيه. الوحدات dp.
 * [x] و[y] يُستخدمان عندما يكون ترتيب الأب ABSOLUTE؛ في الترتيبات المرنة لاحقًا
 * تُتجاهل الإحداثيات ويحدد الأب الموضع، وتبقى [width] و[height] تصف الحجم المطلوب.
 */
data class ElementLayout(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: SizeSpec = SizeSpec.fixed(100f),
    val height: SizeSpec = SizeSpec.fixed(40f)
)

/**
 * كيف يرتّب العنصر (أو الشاشة) أبناءه:
 * - ABSOLUTE: تخطيط حر؛ موضع كل ابن (x, y) نسبةً إلى الأب.
 * - COLUMN: أبناء مرتبون عموديًا. ROW: أفقيًا. BOX: أبناء متراكبون بمحاذاة.
 * - CONSTRAINT: محجوز لمراحل لاحقة (غير منفّذ).
 */
enum class ChildLayout { ABSOLUTE, COLUMN, ROW, BOX, CONSTRAINT }

/** ربط حدث (مثل النقر) بإجراء. */
data class EventBinding(
    val id: String,
    val trigger: String,
    val action: String,
    val params: Map<String, String> = emptyMap()
)

/** عنصر واجهة (نص، زر، ...). قد يحتوي عناصر أبناء. */
data class UiElement(
    val id: String,
    val type: String,
    val layout: ElementLayout = ElementLayout(),
    val childLayout: ChildLayout = ChildLayout.ABSOLUTE,
    val properties: Map<String, String> = emptyMap(),
    val events: List<EventBinding> = emptyList(),
    val children: List<UiElement> = emptyList()
)

/** شاشة بأبعاد تصميم بوحدة dp (الافتراضي هاتف صغير 360×640). */
data class UiScreen(
    val id: String,
    val name: String,
    val width: Float = 360f,
    val height: Float = 640f,
    val elements: List<UiElement> = emptyList(),
    /** تخطيط جذر الشاشة؛ خصائصه (محاذاة، مسافات...) في [properties] بمفاتيح [PropertyKeys] نفسها. */
    val childLayout: ChildLayout = ChildLayout.ABSOLUTE,
    val properties: Map<String, String> = emptyMap()
)

/**
 * مرجع إلى مورد (صورة، خط، ...) مخزّن كملف داخل مجلد resources للمشروع.
 * [path] نسبي لمجلد resources، فلا تُضمَّن الصور داخل JSON التصميم.
 */
data class ResourceRef(
    val id: String,
    val name: String,
    val type: String,
    val path: String
)

data class Project(
    val id: String,
    val name: String,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAt: Long,
    val updatedAt: Long,
    val screens: List<UiScreen> = emptyList(),
    val resources: List<ResourceRef> = emptyList(),
    /** شاشة البداية؛ null أو معرّف غير موجود يعني أول شاشة. */
    val startScreenId: String? = null,
    /** حجم المعاينة الحالي بوحدة dp؛ 0 يعني «حجم تصميم الشاشة نفسه». */
    val previewWidth: Float = 0f,
    val previewHeight: Float = 0f
) {
    /** معرّف شاشة البداية الفعلي (يتراجع إلى أول شاشة إن كان المخزَّن غير صالح). */
    val effectiveStartScreenId: String?
        get() = screens.firstOrNull { it.id == startScreenId }?.id ?: screens.firstOrNull()?.id
}

/** أنواع العناصر المدعومة وأحجامها الافتراضية (dp). */
object ElementTypes {
    const val TEXT = "text"
    const val BUTTON = "button"
    const val IMAGE = "image"
    const val INPUT = "input"
    const val CONTAINER = "container"
    const val CARD = "card"
    const val DIVIDER = "divider"
    const val SPACER = "spacer"
    const val LIST = "list"

    val all = listOf(TEXT, BUTTON, IMAGE, INPUT, CONTAINER, CARD, DIVIDER, SPACER, LIST)

    /** (العرض، الارتفاع) الافتراضيان. */
    fun defaultSize(type: String): Pair<Float, Float> = when (type) {
        TEXT -> 120f to 28f
        BUTTON -> 120f to 44f
        IMAGE -> 140f to 100f
        INPUT -> 200f to 48f
        CONTAINER -> 200f to 120f
        CARD -> 200f to 120f
        DIVIDER -> 200f to 12f
        SPACER -> 100f to 40f
        LIST -> 220f to 136f
        else -> 100f to 40f
    }
}

fun newId(): String = UUID.randomUUID().toString()

/** ينشئ عنصرًا جديدًا بحجمه الافتراضي عند (x, y). [label] هو النص الافتراضي للنص/الزر/حقل الإدخال. */
fun newElement(type: String, x: Float, y: Float, label: String): UiElement {
    val (w, h) = ElementTypes.defaultSize(type)
    val properties = when (type) {
        ElementTypes.TEXT, ElementTypes.BUTTON -> mapOf("text" to label)
        ElementTypes.INPUT -> mapOf("hint" to label)
        else -> emptyMap()
    }
    return UiElement(
        id = newId(),
        type = type,
        layout = ElementLayout(x, y, SizeSpec.fixed(w), SizeSpec.fixed(h)),
        properties = properties
    )
}

/** ينشئ مشروعًا جديدًا بشاشة أولى فارغة. */
fun newProject(name: String, firstScreenName: String, now: Long = System.currentTimeMillis()): Project =
    Project(
        id = newId(),
        name = name,
        createdAt = now,
        updatedAt = now,
        screens = listOf(UiScreen(id = newId(), name = firstScreenName))
    )
