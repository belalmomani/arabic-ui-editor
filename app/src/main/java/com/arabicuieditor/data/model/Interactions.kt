package com.arabicuieditor.data.model

/** إجراء النقر على عنصر. يُخزَّن كحدث واحد (trigger = click) في قائمة أحداث العنصر. */
sealed interface ClickAction {
    data object None : ClickAction
    data class Navigate(val screenId: String) : ClickAction
    data object Back : ClickAction
    /** يشغّل سكربت جافا سكريبت داخل المعاينة (انظر ScriptEngine). */
    data class Script(val code: String) : ClickAction
    /** منطق بالأزرار (بلا كتابة): خطوات تُترجم إلى سكربت وتعمل في المعاينة. */
    data class Blocks(val steps: List<com.arabicuieditor.data.script.Step>) : ClickAction
    /** إجراء خارجي على الجهاز: اتصال، واتساب، بريد، رابط، خريطة، مشاركة (يعمل في المعاينة والتصدير Compose). */
    data class External(val kind: String, val value: String) : ClickAction
}

/** أنواع الإجراءات الخارجية وتحويل القيمة إلى عنوان (URI). دوال نقية قابلة للاختبار. */
object ExternalKinds {
    const val CALL = "call"
    const val WHATSAPP = "whatsapp"
    const val EMAIL = "email"
    const val URL = "url"
    const val MAP = "map"
    const val SHARE = "share"
    val all = listOf(CALL, WHATSAPP, EMAIL, URL, MAP, SHARE)

    private fun digits(v: String): String {
        val t = v.trim()
        return (if (t.startsWith("+")) "+" else "") + t.filter { it in '0'..'9' || it in '٠'..'٩' }
            .map { if (it in '٠'..'٩') ('0' + (it - '٠')) else it }.joinToString("")
    }

    /** العنوان الذي يُفتح، أو null إن كانت القيمة فارغة/غير صالحة (المشاركة تعيد النص نفسه). */
    fun uri(kind: String, value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        return when (kind) {
            CALL -> digits(v).takeIf { it.length >= 3 }?.let { "tel:$it" }
            WHATSAPP -> digits(v).removePrefix("+").takeIf { it.length >= 6 }?.let { "https://wa.me/$it" }
            EMAIL -> v.takeIf { it.contains('@') && !it.contains(' ') }?.let { "mailto:$it" }
            URL -> if (v.startsWith("http://") || v.startsWith("https://")) v else if (v.contains(' ')) null else "https://$v"
            MAP -> "geo:0,0?q=" + java.net.URLEncoder.encode(v, "UTF-8")
            SHARE -> v
            else -> null
        }
    }
}

/** قراءة وكتابة إجراء النقر (دوال نقية فوق [EventBinding] الموجود في النموذج). */
object Interactions {
    const val TRIGGER_CLICK = "click"
    const val TRIGGER_LONG_PRESS = "longpress"
    const val ACTION_NAVIGATE = "navigate"
    const val ACTION_BACK = "back"
    const val ACTION_SCRIPT = "script"
    const val PARAM_SCREEN = "screen"
    const val PARAM_CODE = "code"
    const val ACTION_BLOCKS = "blocks"
    const val ACTION_EXTERNAL = "external"
    const val PARAM_KIND = "kind"
    const val PARAM_VALUE = "value"
    const val PARAM_STEPS = "steps"

    fun clickAction(e: UiElement): ClickAction = actionFor(e, TRIGGER_CLICK)

    fun withClickAction(e: UiElement, action: ClickAction): UiElement = withAction(e, TRIGGER_CLICK, action)

    /** إجراء محفِّز معيّن (نقر أو ضغطة مطوّلة). */
    fun actionFor(e: UiElement, trigger: String): ClickAction {
        val ev = e.events.firstOrNull { it.trigger == trigger } ?: return ClickAction.None
        return when (ev.action) {
            ACTION_NAVIGATE -> ClickAction.Navigate(ev.params[PARAM_SCREEN].orEmpty())
            ACTION_BACK -> ClickAction.Back
            ACTION_BLOCKS -> ClickAction.Blocks(com.arabicuieditor.data.script.Steps.decode(ev.params[PARAM_STEPS].orEmpty()))
            ACTION_SCRIPT -> ClickAction.Script(ev.params[PARAM_CODE].orEmpty())
            ACTION_EXTERNAL -> ClickAction.External(ev.params[PARAM_KIND].orEmpty(), ev.params[PARAM_VALUE].orEmpty())
            else -> ClickAction.None
        }
    }

    /** يستبدل أحداث النقر بالإجراء الجديد ويُبقي بقية الأحداث كما هي. */
    fun withAction(e: UiElement, trigger: String, action: ClickAction): UiElement {
        val others = e.events.filterNot { it.trigger == trigger }
        val added = when (action) {
            ClickAction.None -> emptyList()
            ClickAction.Back -> listOf(EventBinding(newId(), trigger, ACTION_BACK))
            is ClickAction.Navigate -> listOf(
                EventBinding(newId(), trigger, ACTION_NAVIGATE, mapOf(PARAM_SCREEN to action.screenId))
            )
            is ClickAction.Blocks -> listOf(
                EventBinding(newId(), trigger, ACTION_BLOCKS, mapOf(PARAM_STEPS to com.arabicuieditor.data.script.Steps.encode(action.steps)))
            )
            is ClickAction.Script -> listOf(
                EventBinding(newId(), trigger, ACTION_SCRIPT, mapOf(PARAM_CODE to action.code))
            )
            is ClickAction.External -> listOf(
                EventBinding(newId(), trigger, ACTION_EXTERNAL, mapOf(PARAM_KIND to action.kind, PARAM_VALUE to action.value))
            )
        }
        return e.copy(events = others + added)
    }

    /** الانتقال إلى شاشة محذوفة أو غير محددة غير صالح (يبقى الإجراء مخزَّنًا ويُحذَّر منه). */
    fun isTargetValid(screens: List<UiScreen>, action: ClickAction): Boolean = when (action) {
        is ClickAction.Navigate -> screens.any { it.id == action.screenId }
        else -> true
    }
}
