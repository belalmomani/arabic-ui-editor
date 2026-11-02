package com.arabicuieditor.ui.editor.canvas

import com.arabicuieditor.data.model.AnchorEdge
import com.arabicuieditor.data.model.Constraints
import com.arabicuieditor.data.model.UiElement

/** خط توضيحي بين حافة عنصر والهدف المربوط به (إحداثيات التصميم). */
data class ConstraintLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/** يحسب خطوط قيود العنصر المحدد ليراها المستخدم على اللوحة. منطق نقي. */
object ConstraintLines {

    fun compute(
        element: UiElement,
        frames: Map<String, DesignRect>,
        parentContent: DesignRect
    ): List<ConstraintLine> {
        val me = frames[element.id] ?: return emptyList()
        val cx = me.x + me.w / 2f
        val cy = me.y + me.h / 2f
        val out = ArrayList<ConstraintLine>()
        for (edge in AnchorEdge.entries) {
            val a = Constraints.read(element, edge) ?: continue
            val toParent = a.target == Constraints.PARENT
            val t = if (toParent) null else (frames[a.target] ?: continue)
            if (!toParent && a.target == element.id) continue
            when (edge) {
                AnchorEdge.LEFT -> out += ConstraintLine(me.x, cy, if (toParent) parentContent.x else t!!.right, cy)
                AnchorEdge.RIGHT -> out += ConstraintLine(me.right, cy, if (toParent) parentContent.right else t!!.x, cy)
                AnchorEdge.TOP -> out += ConstraintLine(cx, me.y, cx, if (toParent) parentContent.y else t!!.bottom)
                AnchorEdge.BOTTOM -> out += ConstraintLine(cx, me.bottom, cx, if (toParent) parentContent.bottom else t!!.y)
                AnchorEdge.CENTER_H -> out += ConstraintLine(cx, cy, parentContent.x + parentContent.w / 2f, cy)
                AnchorEdge.CENTER_V -> out += ConstraintLine(cx, cy, cx, parentContent.y + parentContent.h / 2f)
            }
        }
        return out
    }
}
