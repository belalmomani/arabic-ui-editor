package com.arabicuieditor.data

import com.arabicuieditor.data.model.Axis
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementOps
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.LayoutEditing
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.TreeLocation
import com.arabicuieditor.data.model.UiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** شجرة العناصر (حاويات متداخلة) وعمليات التغليف والنقل. */
class ElementTreeTest {

    private fun leaf(id: String, x: Float = 0f, y: Float = 0f, props: Map<String, String> = emptyMap()) = UiElement(
        id = id, type = ElementTypes.BUTTON,
        layout = ElementLayout(x, y, SizeSpec.fixed(100f), SizeSpec.fixed(40f)),
        properties = props
    )

    private fun group(
        id: String,
        children: List<UiElement>,
        layout: ChildLayout = ChildLayout.ABSOLUTE,
        x: Float = 0f,
        y: Float = 0f
    ) = UiElement(
        id = id, type = ElementTypes.CONTAINER,
        layout = ElementLayout(x, y, SizeSpec.fixed(200f), SizeSpec.fixed(200f)),
        childLayout = layout, children = children
    )

    /** a, p[c1, q[d]], z */
    private fun tree() = listOf(
        leaf("a"),
        group("p", listOf(leaf("c1"), group("q", listOf(leaf("d"))))),
        leaf("z")
    )

    private fun ids(list: List<UiElement>) = ElementTree.flatten(list).map { it.id }

    @Test
    fun flattenIsDepthFirstParentBeforeChildren() {
        assertEquals(listOf("a", "p", "c1", "q", "d", "z"), ids(tree()))
        assertEquals(6, ElementTree.count(tree()))
    }

    @Test
    fun locateFindsParentIndexAndDepth() {
        assertEquals(TreeLocation("q", 0), ElementTree.locate(tree(), "d"))
        assertEquals(TreeLocation(null, 2), ElementTree.locate(tree(), "z"))
        assertEquals(TreeLocation("p", 1), ElementTree.locate(tree(), "q"))
        assertNull(ElementTree.locate(tree(), "missing"))
        assertEquals(2, ElementTree.depthOf(tree(), "d"))
        assertEquals(-1, ElementTree.depthOf(tree(), "missing"))
    }

    @Test
    fun updateAndRemoveReachNestedElements() {
        val updated = ElementTree.update(tree(), "d") { it.copy(properties = mapOf(PropertyKeys.NAME to "عميق")) }
        assertEquals("عميق", ElementTree.find(updated, "d")!!.properties[PropertyKeys.NAME])
        val removed = ElementTree.remove(tree(), "q")
        assertEquals(listOf("a", "p", "c1", "z"), ids(removed))
    }

    @Test
    fun insertPlacesElementInsideTheParentAtIndex() {
        val inserted = ElementTree.insert(tree(), "p", 0, leaf("new"))
        assertEquals(listOf("new", "c1", "q"), ElementTree.find(inserted, "p")!!.children.map { it.id })
        val atRoot = ElementTree.insert(tree(), null, 1, leaf("r"))
        assertEquals(listOf("a", "r", "p", "z"), atRoot.map { it.id })
        // أب غير موجود: لا يُدرج شيء
        assertEquals(ids(tree()), ids(ElementTree.insert(tree(), "nope", null, leaf("x"))))
    }

    @Test
    fun siblingReorderingWorksInsideAContainer() {
        val moved = ElementTree.transformSiblings(tree(), "c1") { ElementOps.moveForward(it, "c1") }
        assertEquals(listOf("q", "c1"), ElementTree.find(moved, "p")!!.children.map { it.id })
        // المستوى الأعلى لم يتأثر
        assertEquals(listOf("a", "p", "z"), moved.map { it.id })
    }

    @Test
    fun moveToReparentsAndRejectsInvalidTargets() {
        val moved = LayoutEditing.moveTo(tree(), "d", null, 0, localX = 7f, localY = 9f)!!
        assertEquals(listOf("d", "a", "p", "z"), moved.map { it.id })
        assertEquals(7f, moved.first().layout.x, 0f)
        assertEquals(9f, moved.first().layout.y, 0f)
        assertEquals(listOf("c1", "q"), ElementTree.find(moved, "p")!!.children.map { it.id })

        // لا نقل إلى النفس ولا إلى أحد الأحفاد
        assertNull(LayoutEditing.moveTo(tree(), "p", "q", null))
        assertNull(LayoutEditing.moveTo(tree(), "p", "p", null))
        // ولا إلى عنصر لا يقبل أبناء (زر)
        assertNull(LayoutEditing.moveTo(tree(), "z", "a", null))
        assertNull(LayoutEditing.moveTo(tree(), "missing", null, null))
    }

    @Test
    fun wrapTakesTheSlotAndPropertiesOfTheElement() {
        val roots = listOf(
            leaf("a", x = 30f, y = 40f, props = mapOf(PropertyKeys.WEIGHT to "2", PropertyKeys.NAME to "زر")),
            leaf("b")
        )
        val wrapped = LayoutEditing.wrap(roots, "a", ChildLayout.ROW, "w", "حاوية", 100f, 40f)!!
        assertEquals(listOf("w", "b"), wrapped.map { it.id })
        val w = wrapped.first()
        assertEquals(ChildLayout.ROW, w.childLayout)
        assertEquals(30f, w.layout.x, 0f)
        assertEquals(40f, w.layout.y, 0f)
        assertEquals(SizeMode.WRAP_CONTENT, w.layout.width.mode)
        assertEquals("2", w.properties[PropertyKeys.WEIGHT])
        val child = w.children.single()
        assertEquals("a", child.id)
        assertEquals(0f, child.layout.x, 0f)
        assertNull(child.properties[PropertyKeys.WEIGHT])
        assertEquals("زر", child.properties[PropertyKeys.NAME])
    }

    @Test
    fun unwrapFreesChildrenInPlaceAndKeepsTheirVisualPosition() {
        val roots = listOf(group("g", listOf(leaf("c1", x = 5f, y = 6f), leaf("c2")), x = 50f, y = 60f), leaf("z"))
        val freed = LayoutEditing.unwrap(roots, "g", parentIsAbsolute = true)!!
        assertEquals(listOf("c1", "c2", "z"), freed.map { it.id })
        assertEquals(55f, freed[0].layout.x, 0f)
        assertEquals(66f, freed[0].layout.y, 0f)
        // حاوية فارغة لا تُفك
        assertNull(LayoutEditing.unwrap(listOf(group("e", emptyList())), "e", true))
    }

    @Test
    fun sizeModeKeepsASensibleValue() {
        val e = leaf("a")
        val fraction = LayoutEditing.withSizeMode(e, Axis.HORIZONTAL, SizeMode.FRACTION, frameSize = 100f, available = 200f)
        assertEquals(SizeSpec(SizeMode.FRACTION, 50f), fraction.layout.width)
        val fixed = LayoutEditing.withSizeMode(fraction, Axis.HORIZONTAL, SizeMode.FIXED, 123.4f, 200f)
        assertEquals(SizeSpec.fixed(123f), fixed.layout.width)
        val match = LayoutEditing.withSizeMode(e, Axis.VERTICAL, SizeMode.MATCH_PARENT, 80f, 300f)
        assertEquals(SizeMode.MATCH_PARENT, match.layout.height.mode)
        assertEquals(e.layout.width, match.layout.width) // المحور الآخر لم يتغير
        // النسبة تُقيَّد بين 1 و100
        assertEquals(100f, LayoutEditing.withPercent(fraction, Axis.HORIZONTAL, 500).layout.width.value, 0f)
        assertEquals(e, LayoutEditing.withPercent(e, Axis.HORIZONTAL, 30)) // لا أثر لغير النسبة
    }

    @Test
    fun duplicatingANestedElementGivesFreshIdsAndNames() {
        val named = group("g", listOf(leaf("c", props = mapOf(PropertyKeys.NAME to "ابن"))))
            .let { it.copy(properties = mapOf(PropertyKeys.NAME to "حاوية")) }
        val roots = listOf(named)
        val (list, copyId) = ElementOps.duplicate(roots, "g", 12f, "نسخة", roots)!!
        val copy = list[1]
        assertEquals(copyId, copy.id)
        assertNotEquals("c", copy.children.single().id)
        assertEquals("حاوية نسخة", copy.properties[PropertyKeys.NAME])
        val names = ElementTree.flatten(list).mapNotNull { it.properties[PropertyKeys.NAME] }
        assertEquals(names.size, names.toSet().size)
        assertFalse(ElementTree.flatten(list).map { it.id }.let { it.size != it.toSet().size })
    }

    @Test
    fun onlyContainersAndCardsAcceptChildren() {
        assertTrue(ElementTree.canContain(ElementTypes.CONTAINER))
        assertTrue(ElementTree.canContain(ElementTypes.CARD))
        assertFalse(ElementTree.canContain(ElementTypes.BUTTON))
        assertFalse(ElementTree.canContain(ElementTypes.LIST))
    }
}
