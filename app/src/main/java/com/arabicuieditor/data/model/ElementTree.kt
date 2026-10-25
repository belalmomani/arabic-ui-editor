package com.arabicuieditor.data.model

/** موضع عنصر في الشجرة: معرّف أبيه (null = جذر الشاشة) وترتيبه بين إخوته. */
data class TreeLocation(val parentId: String?, val index: Int)

/**
 * عمليات نقية على شجرة عناصر الشاشة (عناصر داخل حاويات داخل حاويات...).
 * لا تعتمد على Android أو Compose، فتُختبر مباشرة على JVM.
 * الترتيب داخل القائمة هو ترتيب الرسم (الأخير فوق) وترتيب الصف/العمود.
 */
object ElementTree {

    /** الأنواع التي تقبل عناصر أبناء. */
    fun canContain(type: String): Boolean = type == ElementTypes.CONTAINER || type == ElementTypes.CARD

    /** كل العناصر بترتيب الرسم: الأب قبل أبنائه، والإخوة اللاحقون فوق السابقين. */
    fun flatten(roots: List<UiElement>): List<UiElement> {
        val out = ArrayList<UiElement>()
        fun walk(list: List<UiElement>) {
            for (e in list) {
                out += e
                if (e.children.isNotEmpty()) walk(e.children)
            }
        }
        walk(roots)
        return out
    }

    fun find(roots: List<UiElement>, id: String): UiElement? {
        for (e in roots) {
            if (e.id == id) return e
            find(e.children, id)?.let { return it }
        }
        return null
    }

    fun contains(roots: List<UiElement>, id: String): Boolean = find(roots, id) != null

    fun count(roots: List<UiElement>): Int = flatten(roots).size

    fun locate(roots: List<UiElement>, id: String, parentId: String? = null): TreeLocation? {
        roots.forEachIndexed { index, e ->
            if (e.id == id) return TreeLocation(parentId, index)
            locate(e.children, id, e.id)?.let { return it }
        }
        return null
    }

    /** عمق العنصر: 0 للمستوى الأعلى. -1 إن لم يوجد. */
    fun depthOf(roots: List<UiElement>, id: String): Int {
        fun walk(list: List<UiElement>, depth: Int): Int {
            for (e in list) {
                if (e.id == id) return depth
                val d = walk(e.children, depth + 1)
                if (d >= 0) return d
            }
            return -1
        }
        return walk(roots, 0)
    }

    /** معرّفات كل أحفاد العنصر (دون العنصر نفسه). */
    fun descendantIds(element: UiElement): Set<String> = flatten(element.children).map { it.id }.toSet()

    /** يطبّق [transform] على العنصر ذي المعرّف أينما كان في الشجرة. */
    fun update(roots: List<UiElement>, id: String, transform: (UiElement) -> UiElement): List<UiElement> =
        roots.map { e ->
            when {
                e.id == id -> transform(e)
                e.children.isEmpty() -> e
                else -> e.copy(children = update(e.children, id, transform))
            }
        }

    fun remove(roots: List<UiElement>, id: String): List<UiElement> =
        roots.filterNot { it.id == id }.map { e ->
            if (e.children.isEmpty()) e else e.copy(children = remove(e.children, id))
        }

    /**
     * يدرج [element] في أبناء [parentId] (أو الجذر إن كان null) عند [index]؛ null = في النهاية.
     * إن لم يوجد الأب لا يُدرج شيء.
     */
    fun insert(roots: List<UiElement>, parentId: String?, index: Int?, element: UiElement): List<UiElement> {
        if (parentId == null) return insertAt(roots, index, element)
        return update(roots, parentId) { p -> p.copy(children = insertAt(p.children, index, element)) }
    }

    private fun insertAt(list: List<UiElement>, index: Int?, element: UiElement): List<UiElement> {
        val at = (index ?: list.size).coerceIn(0, list.size)
        return list.toMutableList().also { it.add(at, element) }
    }

    /** يطبّق [transform] على قائمة الإخوة التي تحوي العنصر (المستوى الأعلى أو أبناء أب ما). */
    fun transformSiblings(
        roots: List<UiElement>,
        id: String,
        transform: (List<UiElement>) -> List<UiElement>
    ): List<UiElement> {
        if (roots.any { it.id == id }) return transform(roots)
        return roots.map { e ->
            if (e.children.isEmpty()) e else e.copy(children = transformSiblings(e.children, id, transform))
        }
    }

    /** نسخة بمعرّفات جديدة للعنصر وأبنائه وأحداثه. */
    fun reId(e: UiElement): UiElement = e.copy(
        id = newId(),
        events = e.events.map { it.copy(id = newId()) },
        children = e.children.map { reId(it) }
    )
}
