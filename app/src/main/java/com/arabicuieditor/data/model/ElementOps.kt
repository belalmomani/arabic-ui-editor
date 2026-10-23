package com.arabicuieditor.data.model

/**
 * عمليات نقية على قائمة عناصر شاشة (الأخير في القائمة هو الأعلى في الشاشة).
 * لا تعتمد على Android أو Compose، فتُختبر مباشرة على JVM.
 */
object ElementOps {

    /** أمام العنصر التالي في الترتيب. */
    fun moveForward(list: List<UiElement>, id: String): List<UiElement> = shift(list, id, +1)

    fun moveBackward(list: List<UiElement>, id: String): List<UiElement> = shift(list, id, -1)

    fun bringToFront(list: List<UiElement>, id: String): List<UiElement> {
        val index = list.indexOfFirst { it.id == id }
        if (index < 0 || index == list.lastIndex) return list
        return list.filterIndexed { i, _ -> i != index } + list[index]
    }

    fun sendToBack(list: List<UiElement>, id: String): List<UiElement> {
        val index = list.indexOfFirst { it.id == id }
        if (index <= 0) return list
        return listOf(list[index]) + list.filterIndexed { i, _ -> i != index }
    }

    private fun shift(list: List<UiElement>, id: String, delta: Int): List<UiElement> {
        val index = list.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in list.indices) return list
        val out = list.toMutableList()
        val item = out.removeAt(index)
        out.add(target, item)
        return out
    }

    /**
     * ينسخ عنصرًا (مع أبنائه وأحداثه بمعرّفات جديدة) ويضعه فوق الأصل مباشرة،
     * مزاحًا بـ [offset] dp ليظهر أنه نسخة. يعيد القائمة الجديدة ومعرّف النسخة.
     */
    fun duplicate(
        list: List<UiElement>,
        id: String,
        offset: Float,
        copySuffix: String
    ): Pair<List<UiElement>, String>? {
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val source = list[index]
        val name = uniqueName(list, source.properties[PropertyKeys.NAME]?.takeIf { it.isNotBlank() }?.let { "$it $copySuffix" }
            ?: copySuffix)
        val copy = reId(source).let {
            it.copy(
                layout = it.layout.copy(x = it.layout.x + offset, y = it.layout.y + offset),
                properties = it.properties + (PropertyKeys.NAME to name)
            )
        }
        val out = list.toMutableList()
        out.add(index + 1, copy)
        return out to copy.id
    }

    private fun reId(e: UiElement): UiElement = e.copy(
        id = newId(),
        events = e.events.map { it.copy(id = newId()) },
        children = e.children.map { reId(it) }
    )

    /** اسم داخلي غير مستخدم: [base] إن كان حرًّا، وإلا "[base] 2" و"[base] 3"... */
    fun uniqueName(existing: List<UiElement>, base: String): String {
        val taken = existing.mapNotNull { it.properties[PropertyKeys.NAME] }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    /**
     * يعطي كل عنصر جديد اسمًا داخليًا فريدًا مبنيًا على اسمه الحالي أو على [fallbackBase]،
     * مع مراعاة العناصر الموجودة والعناصر الجديدة نفسها.
     */
    fun withUniqueNames(
        existing: List<UiElement>,
        added: List<UiElement>,
        fallbackBase: (UiElement) -> String
    ): List<UiElement> {
        val acc = existing.toMutableList()
        return added.map { e ->
            val base = e.properties[PropertyKeys.NAME]?.takeIf { it.isNotBlank() } ?: fallbackBase(e)
            val named = e.withProp(PropertyKeys.NAME, uniqueName(acc, base))
            acc += named
            named
        }
    }

    /** المستطيل المحيط بمجموعة عناصر (x, y, w, h)، أو null إن كانت فارغة. */
    fun bounds(elements: List<UiElement>): FloatArray? {
        if (elements.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (e in elements) {
            val l = e.layout
            minX = minOf(minX, l.x)
            minY = minOf(minY, l.y)
            maxX = maxOf(maxX, l.x + l.width.value)
            maxY = maxOf(maxY, l.y + l.height.value)
        }
        return floatArrayOf(minX, minY, maxX - minX, maxY - minY)
    }

    /** يزيح العناصر بحيث يقع مركز مجموعتها عند (cx, cy) ويبقى داخل إطار الشاشة قدر الإمكان. */
    fun placeCentered(
        elements: List<UiElement>,
        cx: Float,
        cy: Float,
        frameW: Float,
        frameH: Float
    ): List<UiElement> {
        val b = bounds(elements) ?: return elements
        val targetX = (cx - b[2] / 2f).coerceIn(0f, maxOf(0f, frameW - b[2]))
        val targetY = (cy - b[3] / 2f).coerceIn(0f, maxOf(0f, frameH - b[3]))
        val dx = Math.round(targetX - b[0]).toFloat()
        val dy = Math.round(targetY - b[1]).toFloat()
        return elements.map { e ->
            e.copy(layout = e.layout.copy(x = e.layout.x + dx, y = e.layout.y + dy))
        }
    }
}
