package com.arabicuieditor.data.storage

import com.arabicuieditor.data.model.CURRENT_SCHEMA_VERSION
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ColorUtil
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.EventBinding
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.ResourceRef
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import org.json.JSONArray
import org.json.JSONObject

/** يُرمى عندما يكون ملف المشروع من إصدار مخطط أحدث مما يفهمه التطبيق. */
class UnsupportedSchemaException(val found: Int) :
    Exception("Unsupported schema version $found (max $CURRENT_SCHEMA_VERSION)")

/**
 * تحويل المشروع من/إلى JSON منظم.
 * يحتوي JSON على بيانات التصميم ومراجع الموارد فقط؛ الملفات نفسها تبقى في مجلد resources.
 */
/** أحجام الشبكة المسموحة (0 = بلا شبكة). */
val ALLOWED_GRIDS = listOf(0, 4, 8, 16)

object ProjectJsonCodec {

    fun encode(project: Project): String = JSONObject().apply {
        put("schemaVersion", project.schemaVersion)
        put("id", project.id)
        put("name", project.name)
        put("createdAt", project.createdAt)
        put("updatedAt", project.updatedAt)
        project.startScreenId?.let { put("startScreenId", it) }
        put("previewWidth", project.previewWidth.toDouble())
        put("previewHeight", project.previewHeight.toDouble())
        put("designSystem", designToJson(project.designSystem))
        put("snapObjects", project.snapObjects)
        put("gridSize", project.gridSize)
        put("screens", JSONArray().also { arr -> project.screens.forEach { arr.put(screenToJson(it)) } })
        put("resources", JSONArray().also { arr -> project.resources.forEach { arr.put(resourceToJson(it)) } })
    }.toString(2)

    fun decode(text: String): Project {
        val root = JSONObject(text)
        val version = root.optInt("schemaVersion", 1)
        if (version > CURRENT_SCHEMA_VERSION) throw UnsupportedSchemaException(version)
        val migrated = migrate(root, version)
        return Project(
            id = migrated.getString("id"),
            name = migrated.optString("name"),
            schemaVersion = CURRENT_SCHEMA_VERSION,
            createdAt = migrated.optLong("createdAt"),
            updatedAt = migrated.optLong("updatedAt"),
            screens = migrated.optJSONArray("screens").mapObjects { screenFromJson(it) },
            resources = migrated.optJSONArray("resources").mapObjects { resourceFromJson(it) },
            startScreenId = migrated.optString("startScreenId").takeIf { it.isNotEmpty() },
            previewWidth = migrated.optDouble("previewWidth", 0.0).toFloat(),
            previewHeight = migrated.optDouble("previewHeight", 0.0).toFloat(),
            designSystem = designFromJson(migrated.optJSONObject("designSystem")),
            snapObjects = migrated.optBoolean("snapObjects", true),
            gridSize = migrated.optInt("gridSize", 0).takeIf { it in ALLOWED_GRIDS } ?: 0
        )
    }

    /**
     * نقطة ترقية المخططات القديمة.
     * 1 → 2: لا تغيير مطلوب؛ الحقول الجديدة (التخطيط وأبعاد الشاشة) اختيارية ولها قيم افتراضية
     * عند فك الترميز، فتُفتح مشاريع المرحلة 2 كما هي.
     * 2 → 3: لا تغيير مطلوب؛ الحقول الجديدة (شاشة البداية، حجم المعاينة، تخطيط الشاشة وخصائصها،
     * وضع BOX ونمط FRACTION) كلها اختيارية. التصاميم القديمة كلها بتخطيط حر فتظهر كما كانت.
     * 3 → 4: لا تغيير مطلوب؛ حقل designSystem اختياري، وغيابه يعني النظام الافتراضي، وهو يطابق
     * ألوان وأحجام ما كان مرسومًا قبله، فتظهر المشاريع القديمة كما كانت. العناصر تحفظ مراجع
     * مثل "@color/primary" في خصائصها كنصوص عادية.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun migrate(root: JSONObject, fromVersion: Int): JSONObject = root

    // ---- نظام التصميم ----

    private fun designToJson(ds: DesignSystem) = JSONObject().apply {
        put("colors", JSONObject().also { o -> ds.colors.forEach { (k, v) -> o.put(k, ColorUtil.format(v)) } })
        put("radii", JSONObject().also { o -> ds.radii.forEach { (k, v) -> o.put(k, v) } })
        put("textSizes", JSONObject().also { o -> ds.textSizes.forEach { (k, v) -> o.put(k, v) } })
    }

    /** يقرأ بتسامح: مفتاح مجهول أو قيمة غير صالحة تُهمل، والناقص يُكمَّل بالافتراضي. */
    private fun designFromJson(o: JSONObject?): DesignSystem {
        if (o == null) return DesignSystem.DEFAULT
        val colors = mutableMapOf<String, Int>()
        o.optJSONObject("colors")?.let { c ->
            for (k in Tokens.colorNames) ColorUtil.parse(c.optString(k))?.let { colors[k] = it }
        }
        val radii = mutableMapOf<String, Int>()
        o.optJSONObject("radii")?.let { r ->
            for (k in Tokens.radiusNames) if (r.has(k)) radii[k] = r.optInt(k).coerceIn(0, 100)
        }
        val sizes = mutableMapOf<String, Int>()
        o.optJSONObject("textSizes")?.let { t ->
            for (k in Tokens.textNames) if (t.has(k)) sizes[k] = t.optInt(k).coerceIn(8, 72)
        }
        return DesignSystem(colors, radii, sizes).completed()
    }

    // ---- ترميز ----

    private fun screenToJson(s: UiScreen) = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("width", s.width.toDouble())
        put("height", s.height.toDouble())
        put("childLayout", s.childLayout.name.lowercase())
        put("properties", mapToJson(s.properties))
        put("elements", JSONArray().also { arr -> s.elements.forEach { arr.put(elementToJson(it)) } })
    }

    private fun elementToJson(e: UiElement): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("type", e.type)
        put("layout", layoutToJson(e.layout))
        put("childLayout", e.childLayout.name.lowercase())
        put("properties", mapToJson(e.properties))
        put("events", JSONArray().also { arr -> e.events.forEach { arr.put(eventToJson(it)) } })
        put("children", JSONArray().also { arr -> e.children.forEach { arr.put(elementToJson(it)) } })
    }

    private fun layoutToJson(l: ElementLayout) = JSONObject().apply {
        put("x", l.x.toDouble())
        put("y", l.y.toDouble())
        put("width", sizeToJson(l.width))
        put("height", sizeToJson(l.height))
    }

    private fun sizeToJson(s: SizeSpec) = JSONObject().apply {
        put("mode", s.mode.name.lowercase())
        put("value", s.value.toDouble())
    }

    private fun eventToJson(ev: EventBinding) = JSONObject().apply {
        put("id", ev.id)
        put("trigger", ev.trigger)
        put("action", ev.action)
        put("params", mapToJson(ev.params))
    }

    private fun resourceToJson(r: ResourceRef) = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("type", r.type)
        put("path", r.path)
    }

    private fun mapToJson(map: Map<String, String>) = JSONObject().also { o -> map.forEach { (k, v) -> o.put(k, v) } }

    // ---- فك الترميز ----

    private fun screenFromJson(o: JSONObject) = UiScreen(
        id = o.getString("id"),
        name = o.optString("name"),
        width = o.optDouble("width", 360.0).toFloat(),
        height = o.optDouble("height", 640.0).toFloat(),
        elements = o.optJSONArray("elements").mapObjects { elementFromJson(it) },
        childLayout = enumOrDefault(o.optString("childLayout"), ChildLayout.ABSOLUTE),
        properties = o.optJSONObject("properties").toStringMap()
    )

    private fun elementFromJson(o: JSONObject): UiElement = UiElement(
        id = o.getString("id"),
        type = o.optString("type"),
        layout = layoutFromJson(o.optJSONObject("layout")),
        childLayout = enumOrDefault(o.optString("childLayout"), ChildLayout.ABSOLUTE),
        properties = o.optJSONObject("properties").toStringMap(),
        events = o.optJSONArray("events").mapObjects { eventFromJson(it) },
        children = o.optJSONArray("children").mapObjects { elementFromJson(it) }
    )

    private fun layoutFromJson(o: JSONObject?): ElementLayout {
        if (o == null) return ElementLayout()
        return ElementLayout(
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            width = sizeFromJson(o.optJSONObject("width"), 100f),
            height = sizeFromJson(o.optJSONObject("height"), 40f)
        )
    }

    private fun sizeFromJson(o: JSONObject?, fallback: Float): SizeSpec {
        if (o == null) return SizeSpec.fixed(fallback)
        return SizeSpec(
            mode = enumOrDefault(o.optString("mode"), SizeMode.FIXED),
            value = o.optDouble("value", fallback.toDouble()).toFloat()
        )
    }

    private fun eventFromJson(o: JSONObject) = EventBinding(
        id = o.getString("id"),
        trigger = o.optString("trigger"),
        action = o.optString("action"),
        params = o.optJSONObject("params").toStringMap()
    )

    private fun resourceFromJson(o: JSONObject) = ResourceRef(
        id = o.getString("id"),
        name = o.optString("name"),
        type = o.optString("type"),
        path = o.optString("path")
    )

    private inline fun <reified E : Enum<E>> enumOrDefault(raw: String, default: E): E =
        enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        val names = keys()
        while (names.hasNext()) {
            val key = names.next()
            out[key] = optString(key)
        }
        return out
    }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { index -> transform(getJSONObject(index)) }
    }
}
