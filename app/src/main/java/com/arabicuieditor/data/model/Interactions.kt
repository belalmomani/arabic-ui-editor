package com.arabicuieditor.data.model

/** إجراء النقر على عنصر. يُخزَّن كحدث واحد (trigger = click) في قائمة أحداث العنصر. */
sealed interface ClickAction {
    data object None : ClickAction
    data class Navigate(val screenId: String) : ClickAction
    data object Back : ClickAction
    /** يشغّل سكربت جافا سكريبت داخل المعاينة (انظر ScriptEngine). */
    data class Script(val code: String) : ClickAction
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

    fun clickAction(e: UiElement): ClickAction = actionFor(e, TRIGGER_CLICK)

    fun withClickAction(e: UiElement, action: ClickAction): UiElement = withAction(e, TRIGGER_CLICK, action)

    /** إجراء محفِّز معيّن (نقر أو ضغطة مطوّلة). */
    fun actionFor(e: UiElement, trigger: String): ClickAction {
        val ev = e.events.firstOrNull { it.trigger == trigger } ?: return ClickAction.None
        return when (ev.action) {
            ACTION_NAVIGATE -> ClickAction.Navigate(ev.params[PARAM_SCREEN].orEmpty())
            ACTION_BACK -> ClickAction.Back
            ACTION_SCRIPT -> ClickAction.Script(ev.params[PARAM_CODE].orEmpty())
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
            is ClickAction.Script -> listOf(
                EventBinding(newId(), trigger, ACTION_SCRIPT, mapOf(PARAM_CODE to action.code))
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
