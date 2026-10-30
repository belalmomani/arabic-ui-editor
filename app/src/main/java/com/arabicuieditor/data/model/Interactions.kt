package com.arabicuieditor.data.model

/** إجراء النقر على عنصر. يُخزَّن كحدث واحد (trigger = click) في قائمة أحداث العنصر. */
sealed interface ClickAction {
    data object None : ClickAction
    data class Navigate(val screenId: String) : ClickAction
    data object Back : ClickAction
}

/** قراءة وكتابة إجراء النقر (دوال نقية فوق [EventBinding] الموجود في النموذج). */
object Interactions {
    const val TRIGGER_CLICK = "click"
    const val ACTION_NAVIGATE = "navigate"
    const val ACTION_BACK = "back"
    const val PARAM_SCREEN = "screen"

    fun clickAction(e: UiElement): ClickAction {
        val ev = e.events.firstOrNull { it.trigger == TRIGGER_CLICK } ?: return ClickAction.None
        return when (ev.action) {
            ACTION_NAVIGATE -> ClickAction.Navigate(ev.params[PARAM_SCREEN].orEmpty())
            ACTION_BACK -> ClickAction.Back
            else -> ClickAction.None
        }
    }

    /** يستبدل أحداث النقر بالإجراء الجديد ويُبقي بقية الأحداث كما هي. */
    fun withClickAction(e: UiElement, action: ClickAction): UiElement {
        val others = e.events.filterNot { it.trigger == TRIGGER_CLICK }
        val added = when (action) {
            ClickAction.None -> emptyList()
            ClickAction.Back -> listOf(EventBinding(newId(), TRIGGER_CLICK, ACTION_BACK))
            is ClickAction.Navigate -> listOf(
                EventBinding(newId(), TRIGGER_CLICK, ACTION_NAVIGATE, mapOf(PARAM_SCREEN to action.screenId))
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
