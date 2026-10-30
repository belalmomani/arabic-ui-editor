package com.arabicuieditor.ui.editor.canvas

import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.boolProp

/** حالة تشغيل النموذج التفاعلي: الشاشة الحالية وسجل الشاشات السابقة للرجوع. */
data class PrototypeState(val current: String, val history: List<String> = emptyList()) {

    /** يطبّق إجراء نقر. الانتقال لشاشة غير موجودة أو للشاشة نفسها لا يغيّر شيئًا. */
    fun apply(action: ClickAction, screens: List<UiScreen>): PrototypeState = when (action) {
        ClickAction.None -> this
        ClickAction.Back -> if (history.isEmpty()) this else PrototypeState(history.last(), history.dropLast(1))
        is ClickAction.Navigate ->
            if (action.screenId == current || screens.none { it.id == action.screenId }) this
            else PrototypeState(action.screenId, history + current)
    }
}

object Prototype {
    /**
     * الإجراء الذي يُنفَّذ عند النقر على النقطة (x, y) بإحداثيات الشاشة:
     * من الأعلى رسمًا إلى الأسفل، أول عنصر ظاهر تحت النقطة له إجراء (أو لأحد آبائه).
     * العنصر بلا إجراء لا يحجب ما تحته (كالعرض غير القابل للنقر في أندرويد). الدوران يُتجاهل هنا.
     */
    fun actionAt(screen: UiScreen, layout: LayoutResult, x: Float, y: Float): ClickAction {
        val byId = ElementTree.flatten(screen.elements).associateBy { it.id }
        for (id in layout.paintOrder.asReversed()) {
            val frame = layout.frames[id] ?: continue
            if (x < frame.x || x > frame.x + frame.w || y < frame.y || y > frame.y + frame.h) continue
            var cursor: String? = id
            var hidden = false
            var found: ClickAction = ClickAction.None
            while (cursor != null) {
                val e = byId[cursor] ?: break
                if (!e.boolProp(PropertyKeys.VISIBLE)) { hidden = true; break }
                val a = Interactions.clickAction(e)
                if (a != ClickAction.None) { found = a; break }
                cursor = layout.parentOf[cursor]
            }
            if (!hidden && found != ClickAction.None) return found
        }
        return ClickAction.None
    }
}
