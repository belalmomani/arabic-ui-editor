package com.arabicuieditor.ui.editor.canvas

import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintLinesTest {
    private fun el(id: String, props: Map<String, String>) = UiElement(
        id = id, type = ElementTypes.BUTTON,
        layout = ElementLayout(0f, 0f, SizeSpec.fixed(50f), SizeSpec.fixed(20f)),
        properties = props
    )

    private val parent = DesignRect(0f, 0f, 300f, 400f)

    @Test
    fun leftToParentAndTopToSibling() {
        val a = el("a", mapOf("anchorL" to "parent:10", "anchorT" to "b:8"))
        val frames = mapOf(
            "a" to DesignRect(10f, 100f, 50f, 20f),
            "b" to DesignRect(10f, 40f, 80f, 30f)
        )
        val lines = ConstraintLines.compute(a, frames, parent)
        assertEquals(2, lines.size)
        assertEquals(ConstraintLine(10f, 110f, 0f, 110f), lines[0])
        assertEquals(ConstraintLine(35f, 100f, 35f, 70f), lines[1])
    }

    @Test
    fun missingTargetIsSkipped() {
        val a = el("a", mapOf("anchorR" to "ghost:5"))
        assertTrue(ConstraintLines.compute(a, mapOf("a" to DesignRect(0f, 0f, 50f, 20f)), parent).isEmpty())
    }

    @Test
    fun noAnchorsNoLines() {
        val a = el("a", emptyMap())
        assertTrue(ConstraintLines.compute(a, mapOf("a" to DesignRect(0f, 0f, 50f, 20f)), parent).isEmpty())
    }
}
