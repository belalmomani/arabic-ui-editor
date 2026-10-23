package com.arabicuieditor.data.storage

import com.arabicuieditor.data.model.CURRENT_SCHEMA_VERSION
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.EventBinding
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.ResourceRef
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
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
object ProjectJsonCodec {

    fun encode(project: Project): String = JSONObject().apply {
        put("schemaVersion", project.schemaVersion)
        put("id", project.id)
        put("name", project.name)
        put("createdAt", project.createdAt)
        put("updatedAt", project.updatedAt)
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
            resources = migrated.optJSONArray("resources").mapObjects { resourceFromJson(it) }
        )
    }

    /**
     * نقطة ترقية المخططات القديمة.
     * 1 → 2: لا تغيير مطلوب؛ الحقول الجديدة (التخطيط وأبعاد الشاشة) اختيارية ولها قيم افتراضية
     * عند فك الترميز، فتُفتح مشاريع المرحلة 2 كما هي.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun migrate(root: JSONObject, fromVersion: Int): JSONObject = root

    // ---- ترميز ----

    private fun screenToJson(s: UiScreen) = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("width", s.width.toDouble())
        put("height", s.height.toDouble())
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
        elements = o.optJSONArray("elements").mapObjects { elementFromJson(it) }
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
