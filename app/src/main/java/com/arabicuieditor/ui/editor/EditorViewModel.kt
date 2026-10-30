package com.arabicuieditor.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.arabicuieditor.ArabicUiEditorApp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.Axis
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ComponentPreset
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.DesignThemes
import com.arabicuieditor.data.model.ElementOps
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.LayoutEditing
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.SCREEN_TYPE
import com.arabicuieditor.data.model.ScreenOps
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.storage.ALLOWED_GRIDS
import com.arabicuieditor.data.storage.UserComponent
import com.arabicuieditor.ui.editor.canvas.AlignEdge
import com.arabicuieditor.ui.editor.canvas.SmartAlign
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.data.model.withProp
import com.arabicuieditor.ui.editor.canvas.DesignRect
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.LayoutResult
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EditorState {
    data object Loading : EditorState
    data object NotFound : EditorState
    data class Ready(val project: Project) : EditorState
}

enum class ReorderOp { FORWARD, BACKWARD, TO_FRONT, TO_BACK }

/** ما يُسجَّل في سجل التراجع: الشاشات وشاشة البداية (حجم المعاينة إعداد عرض ولا يُسجَّل). */
private data class DesignSnapshot(
    val screens: List<UiScreen>,
    val startScreenId: String?,
    val designSystem: DesignSystem
)

private fun Project.snapshot() = DesignSnapshot(screens, startScreenId, designSystem)

/**
 * يحمّل مشروعًا ويطبّق عليه التعديلات مع حفظ تلقائي وتراجع/إعادة:
 * - حفظ مؤجل بعد 400ms من آخر تعديل (يجمع التعديلات المتتالية، مثل السحب).
 * - حفظ فوري عند إيقاف الشاشة أو الخروج منها.
 * - كل تعديل يمر عبر [edit] الذي يسجّل لقطة في [EditHistory] (تتضمن تعديل الخصائص والتحريك
 *   والحذف والترتيب والإضافة وعمليات الشاشات وشاشة البداية). التراجع يستعيد اللقطة ثم يُحفظ كأي تعديل.
 *
 * حالة الواجهة المؤقتة (الشاشة الحالية، العنصر المحدد) هنا أيضًا ولا تُحفظ في الملف.
 * سجل التراجع يبقى في الذاكرة فقط: يُفقد عند إغلاق المحرر، لكن التصميم نفسه محفوظ.
 *
 * العناصر شجرة (حاويات داخل حاويات). مواضع الإفلات والإطارات كلها بإحداثيات الشاشة عند حجم
 * المعاينة الحالي، وتحوَّل هنا إلى إحداثيات محلية داخل الأب عند التخزين.
 */
class EditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as ArabicUiEditorApp).container.projects
    private val projectId: String = handle.get<String>("projectId").orEmpty()
    private val libraryRepo = (app as ArabicUiEditorApp).container.library

    private val _userComponents = MutableStateFlow<List<UserComponent>>(emptyList())
    /** المكتبة الشخصية (مشتركة بين كل المشاريع). */
    val userComponents: StateFlow<List<UserComponent>> = _userComponents.asStateFlow()

    private val _state = MutableStateFlow<EditorState>(EditorState.Loading)
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private val _currentScreenId = MutableStateFlow<String?>(null)
    val currentScreenId: StateFlow<String?> = _currentScreenId.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val history = EditHistory<DesignSnapshot>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            _userComponents.value = try {
                withContext(Dispatchers.IO) { libraryRepo.list() }
            } catch (e: Exception) {
                emptyList()
            }
        }
        viewModelScope.launch {
            val project = try {
                withContext(Dispatchers.IO) { repository.load(projectId) }
            } catch (e: Exception) {
                null
            }
            if (project == null) {
                _state.value = EditorState.NotFound
            } else {
                _currentScreenId.value = project.effectiveStartScreenId
                _state.value = EditorState.Ready(project)
            }
        }
    }

    private fun str(id: Int): String = getApplication<Application>().getString(id)

    private fun readyProject(): Project? = (_state.value as? EditorState.Ready)?.project

    // ---- التحديد والتنقل ----

    fun selectScreen(id: String) {
        if (readyProject()?.screens?.none { it.id == id } != false) return
        _currentScreenId.value = id
        _selectedId.value = null
    }

    fun selectElement(id: String?) {
        _selectedId.value = id
    }

    // ---- إدارة الشاشات ----

    fun addScreen() {
        val project = readyProject() ?: return
        val base = getApplication<Application>().getString(R.string.editor_new_screen_name, project.screens.size + 1)
        val screen = UiScreen(id = newId(), name = ScreenOps.uniqueName(project.screens, base))
        edit { it.copy(screens = it.screens + screen) }
        _currentScreenId.value = screen.id
        _selectedId.value = null
    }

    fun renameScreen(id: String, name: String) {
        if (name.isBlank()) return
        edit { it.copy(screens = ScreenOps.rename(it.screens, id, name)) }
    }

    fun duplicateScreen(id: String) {
        val project = readyProject() ?: return
        val (list, copyId) = ScreenOps.duplicate(project.screens, id, str(R.string.copy_suffix)) ?: return
        edit { it.copy(screens = list) }
        _currentScreenId.value = copyId
        _selectedId.value = null
    }

    /** يحذف شاشة (لا تُحذف الشاشة الوحيدة). يعيد true إن حُذفت. */
    fun deleteScreen(id: String): Boolean {
        val project = readyProject() ?: return false
        val result = ScreenOps.delete(project.screens, id, project.startScreenId) ?: return false
        edit { it.copy(screens = result.screens, startScreenId = result.startScreenId) }
        if (_currentScreenId.value == id || result.screens.none { it.id == _currentScreenId.value }) {
            _currentScreenId.value = result.showScreenId
            _selectedId.value = null
        }
        return true
    }

    fun setStartScreen(id: String) {
        edit { p -> if (p.screens.any { it.id == id }) p.copy(startScreenId = id) else p }
    }

    // ---- نظام التصميم ----

    fun setDesignColor(name: String, argb: Int) {
        if (name !in Tokens.colorNames) return
        edit("ds:color:$name") { it.copy(designSystem = it.designSystem.withColor(name, argb)) }
    }

    fun setDesignRadius(name: String, dp: Int) {
        if (name !in Tokens.radiusNames) return
        edit("ds:radius:$name") { it.copy(designSystem = it.designSystem.withRadius(name, dp)) }
    }

    fun setDesignTextSize(name: String, sp: Int) {
        if (name !in Tokens.textNames) return
        edit("ds:text:$name") { it.copy(designSystem = it.designSystem.withTextSize(name, sp)) }
    }

    /** يطبّق سمة جاهزة (كل الألوان) ويبقي الزوايا وأحجام النصوص. */
    fun applyDesignTheme(themeId: String) {
        val theme = DesignThemes.all.firstOrNull { it.id == themeId } ?: return
        edit { it.copy(designSystem = it.designSystem.copy(colors = theme.colors).completed()) }
    }

    fun resetDesignSystem() {
        edit { it.copy(designSystem = DesignSystem.DEFAULT) }
    }

    // ---- المحاذاة الذكية ----

    /** إعدادات الالتقاط والشبكة؛ إعداد عرض لا يدخل في التراجع. */
    fun setSnapObjects(enabled: Boolean) {
        edit { it.copy(snapObjects = enabled) }
    }

    fun setGridSize(size: Int) {
        if (size !in ALLOWED_GRIDS) return
        edit { it.copy(gridSize = size) }
    }

    /** يحاذي العنصر الحر المحدد داخل منطقة محتوى أبيه. */
    fun alignSelected(edge: AlignEdge) {
        val id = _selectedId.value ?: return
        val project = readyProject() ?: return
        val screen = currentScreen() ?: return
        val layout = layoutOf(project, screen)
        if (id !in layout.freeIds) return
        val frame = layout.frames[id] ?: return
        setElementRect(id, SmartAlign.alignIn(frame, layout.parentContentOf(id), edge))
    }

    /**
     * يوزّع العناصر الحرة المتشاركة مع العنصر المحدد في الأب نفسه بفجوات متساوية (الأول والأخير ثابتان).
     * يحتاج 3 عناصر حرة على الأقل. خطوة تراجع واحدة.
     */
    fun distributeSelected(horizontal: Boolean) {
        val id = _selectedId.value ?: return
        val project = readyProject() ?: return
        val screen = currentScreen() ?: return
        val layout = layoutOf(project, screen)
        if (id !in layout.freeIds) return
        val parent = layout.parentOf[id]
        val frames = layout.freeIds
            .filter { layout.parentOf[it] == parent }
            .mapNotNull { sid -> layout.frames[sid]?.let { sid to it } }
        val moved = SmartAlign.distribute(frames, horizontal)
        if (moved.isEmpty()) return
        val content = layout.parentContentOf(id)
        editScreen(screen.id) { s ->
            var roots = s.elements
            for ((mid, r) in moved) {
                roots = ElementTree.update(roots, mid) { e ->
                    e.copy(
                        layout = e.layout.copy(
                            x = (r.x - content.x).roundToInt().toFloat(),
                            y = (r.y - content.y).roundToInt().toFloat()
                        )
                    )
                }
            }
            s.copy(elements = roots)
        }
    }

    /** يضبط حجم المعاينة؛ 0 يعني العودة إلى حجم تصميم الشاشة. لا يدخل في سجل التراجع. */
    fun setPreviewSize(width: Float, height: Float) {
        val valid = width > 0f && height > 0f
        val w = if (valid) width.coerceIn(MIN_SCREEN_DP, MAX_SCREEN_DP) else 0f
        val h = if (valid) height.coerceIn(MIN_SCREEN_DP, MAX_SCREEN_DP) else 0f
        edit { it.copy(previewWidth = w, previewHeight = h) }
    }

    /** حجم التصميم الأساسي للشاشة (المرجع الذي يُقاس عليه سلوك التثبيت عند تغيّر المعاينة). */
    fun setScreenSize(screenId: String, width: Float, height: Float) {
        val w = width.coerceIn(MIN_SCREEN_DP, MAX_SCREEN_DP)
        val h = height.coerceIn(MIN_SCREEN_DP, MAX_SCREEN_DP)
        editScreen(screenId) { it.copy(width = w, height = h) }
    }

    fun setScreenLayout(layout: ChildLayout) {
        val screen = currentScreen() ?: return
        if (layout == ChildLayout.CONSTRAINT) return
        editScreen(screen.id) { it.copy(childLayout = layout) }
    }

    /** يضبط خاصية في تخطيط جذر الشاشة (محاذاة، مسافة، توزيع، مسافات داخلية). */
    fun setScreenProperty(key: String, value: String) {
        val screen = currentScreen() ?: return
        val def = PropertyRegistry.def(key) ?: return
        if (SCREEN_TYPE !in def.appliesTo) return
        val normalized = PropertyRegistry.normalize(key, value) ?: return
        editScreen(screen.id, key = "screenprop:${screen.id}:$key") { it.withProp(key, normalized) }
    }

    // ---- تعديل التصميم ----

    /**
     * يضيف عنصرًا ويحدده. ([centerX], [centerY]) بإحداثيات الشاشة عند المعاينة الحالية.
     * [atPoint] true عند الإفلات بالسحب: يدخل أعمق حاوية تحت النقطة وبترتيب الإفلات في الصف/العمود.
     * false عند النقر: يدخل الحاوية المحددة (أو حاوية العنصر المحدد)، وإلا الجذر.
     */
    fun addElement(type: String, centerX: Float, centerY: Float, atPoint: Boolean = false) {
        val label = str(elementLabelRes(type))
        insertElements(listOf(newElement(type, 0f, 0f, label)), centerX, centerY, atPoint)
    }

    /** يضيف مكوّنًا جاهزًا من المكتبة (عنصر أو عدة عناصر) ويحدد أول عنصر فيه. */
    fun addPreset(preset: ComponentPreset, centerX: Float, centerY: Float, atPoint: Boolean = false) {
        val built = preset.build { str(it) }
        insertElements(built, centerX, centerY, atPoint)
    }

    // ---- المكتبة الشخصية ----

    /** يحفظ العنصر المحدد (بأبنائه) في المكتبة الشخصية باسم [name]. لا يغيّر المشروع ولا التراجع. */
    fun saveSelectedToLibrary(name: String) {
        val screen = currentScreen() ?: return
        val element = _selectedId.value?.let { ElementTree.find(screen.elements, it) } ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                _userComponents.value = withContext(Dispatchers.IO) { libraryRepo.add(name, element) }
            } catch (_: Exception) {
                // فشل الحفظ لا يمسّ التصميم؛ تبقى القائمة كما هي.
            }
        }
    }

    /** يدرج نسخة بمعرّفات جديدة من مكوّن شخصي في الشاشة الحالية (مثل [addPreset]). */
    fun addUserComponent(component: UserComponent, centerX: Float, centerY: Float, atPoint: Boolean = false) {
        insertElements(listOf(ElementTree.reId(component.root)), centerX, centerY, atPoint)
    }

    fun renameUserComponent(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                _userComponents.value = withContext(Dispatchers.IO) { libraryRepo.rename(id, name) }
            } catch (_: Exception) {
            }
        }
    }

    fun deleteUserComponent(id: String) {
        viewModelScope.launch {
            try {
                _userComponents.value = withContext(Dispatchers.IO) { libraryRepo.delete(id) }
            } catch (_: Exception) {
            }
        }
    }

    private fun insertElements(raw: List<UiElement>, centerX: Float, centerY: Float, atPoint: Boolean) {
        val project = readyProject() ?: return
        val screen = currentScreen() ?: return
        val layout = layoutOf(project, screen)

        val parentId: String? = if (atPoint) {
            LayoutEngine.containerAt(layout, centerX, centerY)
        } else {
            addTargetForTap(screen)
        }
        val parentLayout = layoutKindOf(screen, parentId)
        val parentFrame = parentId?.let { layout.frames[it] } ?: DesignRect(0f, 0f, layout.width, layout.height)

        // النقر على «إضافة» داخل حاوية: يوضع العنصر في مركزها. أما الإفلات فعند النقطة نفسها.
        val cx = if (!atPoint && parentId != null) parentFrame.w / 2f else centerX - parentFrame.x
        val cy = if (!atPoint && parentId != null) parentFrame.h / 2f else centerY - parentFrame.y
        val placed = ElementOps.placeCentered(raw, cx, cy, parentFrame.w, parentFrame.h)
        val named = ElementOps.withUniqueNames(ElementTree.flatten(screen.elements), placed) { e -> str(elementLabelRes(e.type)) }

        val index: Int? = if (atPoint && (parentLayout == ChildLayout.COLUMN || parentLayout == ChildLayout.ROW)) {
            val siblings = childrenOf(screen, parentId).map { it.id }
            LayoutEngine.dropIndex(layout, siblings, parentLayout == ChildLayout.COLUMN, centerX, centerY)
        } else null

        editScreen(screen.id) { s ->
            var roots = s.elements
            named.forEachIndexed { i, e ->
                roots = ElementTree.insert(roots, parentId, index?.plus(i), e)
            }
            s.copy(elements = roots)
        }
        _selectedId.value = named.firstOrNull()?.id
    }

    /** وجهة الإضافة بالنقر: الحاوية المحددة، أو أب العنصر المحدد، وإلا الجذر. */
    private fun addTargetForTap(screen: UiScreen): String? {
        val selected = _selectedId.value?.let { ElementTree.find(screen.elements, it) } ?: return null
        return if (ElementTree.canContain(selected.type)) selected.id
        else ElementTree.locate(screen.elements, selected.id)?.parentId
    }

    private fun layoutKindOf(screen: UiScreen, parentId: String?): ChildLayout =
        if (parentId == null) screen.childLayout
        else ElementTree.find(screen.elements, parentId)?.childLayout ?: ChildLayout.ABSOLUTE

    private fun childrenOf(screen: UiScreen, parentId: String?): List<UiElement> =
        if (parentId == null) screen.elements
        else ElementTree.find(screen.elements, parentId)?.children.orEmpty()

    /**
     * يضبط إطار عنصر. [rect] بإحداثيات الشاشة عند المعاينة الحالية.
     * - الموضع يُخزَّن محليًا ويتغيّر فقط للعنصر الحر (أبوه بتخطيط حر).
     * - البُعد الذي تغيّر فقط يصبح ثابتًا؛ فتحريك عنصر «ملء الأب» لا يفقد نمطه.
     */
    fun setElementRect(elementId: String, rect: DesignRect) {
        val project = readyProject() ?: return
        val screen = currentScreen() ?: return
        val layout = layoutOf(project, screen)
        val frame = layout.frames[elementId] ?: return
        val content = layout.parentContentOf(elementId)
        val isFree = elementId in layout.freeIds
        editScreen(screen.id, key = "rect:$elementId") { s ->
            s.copy(elements = ElementTree.update(s.elements, elementId) { e ->
                var l = e.layout
                if (isFree) {
                    l = l.copy(
                        x = (rect.x - content.x).roundToInt().toFloat(),
                        y = (rect.y - content.y).roundToInt().toFloat()
                    )
                }
                if (rect.w != frame.w) l = l.copy(width = SizeSpec.fixed(rect.w.roundToInt().toFloat()))
                if (rect.h != frame.h) l = l.copy(height = SizeSpec.fixed(rect.h.roundToInt().toFloat()))
                e.copy(layout = l)
            })
        }
    }

    /** يغيّر نمط بُعد العنصر المحدد (ثابت، ملء الأب، حسب المحتوى، نسبة) مع حفظ حجمه الحالي. */
    fun setSizeMode(elementId: String, axis: Axis, mode: SizeMode) {
        val project = readyProject() ?: return
        val screen = currentScreen() ?: return
        val layout = layoutOf(project, screen)
        val frame = layout.frames[elementId] ?: return
        val content = layout.parentContentOf(elementId)
        val frameSize = if (axis == Axis.HORIZONTAL) frame.w else frame.h
        val available = if (axis == Axis.HORIZONTAL) content.w else content.h
        editScreen(screen.id) { s ->
            s.copy(elements = ElementTree.update(s.elements, elementId) {
                LayoutEditing.withSizeMode(it, axis, mode, frameSize, available)
            })
        }
    }

    fun setPercent(elementId: String, axis: Axis, percent: Int) {
        val screen = currentScreen() ?: return
        editScreen(screen.id, key = "percent:$elementId:$axis") { s ->
            s.copy(elements = ElementTree.update(s.elements, elementId) { LayoutEditing.withPercent(it, axis, percent) })
        }
    }

    /** يغيّر طريقة ترتيب أبناء حاوية. */
    fun setChildLayout(elementId: String, layout: ChildLayout) {
        val screen = currentScreen() ?: return
        if (layout == ChildLayout.CONSTRAINT) return
        editScreen(screen.id) { s ->
            s.copy(elements = ElementTree.update(s.elements, elementId) { e ->
                if (ElementTree.canContain(e.type)) e.copy(childLayout = layout) else e
            })
        }
    }

    /**
     * يضبط خاصية عنصر بعد التحقق منها عبر [PropertyRegistry]. القيم غير الصالحة تُتجاهل.
     * الاسم الداخلي الفارغ مسموح؛ تعرض الواجهة حينها اسم نوع العنصر.
     */
    fun setProperty(elementId: String, key: String, value: String) {
        val screen = currentScreen() ?: return
        val normalized = PropertyRegistry.normalize(key, value) ?: return
        editScreen(screen.id, key = "prop:$elementId:$key") { s ->
            s.copy(elements = ElementTree.update(s.elements, elementId) { it.withProp(key, normalized) })
        }
    }

    fun deleteSelected() {
        val id = _selectedId.value ?: return
        val screen = currentScreen() ?: return
        editScreen(screen.id) { s -> s.copy(elements = ElementTree.remove(s.elements, id)) }
        _selectedId.value = null
    }

    fun duplicateSelected() {
        val id = _selectedId.value ?: return
        val screen = currentScreen() ?: return
        var copyId: String? = null
        val suffix = str(R.string.copy_suffix)
        editScreen(screen.id) { s ->
            s.copy(elements = ElementTree.transformSiblings(s.elements, id) { siblings ->
                val result = ElementOps.duplicate(siblings, id, DUPLICATE_OFFSET, suffix, s.elements)
                if (result == null) siblings else {
                    copyId = result.second
                    result.first
                }
            })
        }
        copyId?.let { _selectedId.value = it }
    }

    fun reorderSelected(op: ReorderOp) {
        val id = _selectedId.value ?: return
        val screen = currentScreen() ?: return
        editScreen(screen.id) { s ->
            s.copy(elements = ElementTree.transformSiblings(s.elements, id) { siblings ->
                when (op) {
                    ReorderOp.FORWARD -> ElementOps.moveForward(siblings, id)
                    ReorderOp.BACKWARD -> ElementOps.moveBackward(siblings, id)
                    ReorderOp.TO_FRONT -> ElementOps.bringToFront(siblings, id)
                    ReorderOp.TO_BACK -> ElementOps.sendToBack(siblings, id)
                }
            })
        }
    }

    /** يغلّف العنصر المحدد بحاوية جديدة بالتخطيط المطلوب (صف/عمود/صندوق/حر) ويحدد الحاوية. */
    fun wrapSelected(layout: ChildLayout) {
        val id = _selectedId.value ?: return
        val project = readyProject() ?: return
        val screen = currentScreen() ?: return
        val frame = layoutOf(project, screen).frames[id] ?: return
        val containerId = newId()
        val name = ElementOps.uniqueName(ElementTree.flatten(screen.elements), str(R.string.element_container))
        val wrapped = LayoutEditing.wrap(screen.elements, id, layout, containerId, name, frame.w, frame.h) ?: return
        editScreen(screen.id) { it.copy(elements = wrapped) }
        _selectedId.value = containerId
    }

    /** يفك الحاوية المحددة: أبناؤها يحلّون محلها في أبيها. */
    fun unwrapSelected() {
        val id = _selectedId.value ?: return
        val project = readyProject() ?: return
        val screen = currentScreen() ?: return
        val layout = layoutOf(project, screen)
        val container = ElementTree.find(screen.elements, id) ?: return
        val unwrapped = LayoutEditing.unwrap(screen.elements, id, parentIsAbsolute = id in layout.freeIds) ?: return
        editScreen(screen.id) { it.copy(elements = unwrapped) }
        _selectedId.value = container.children.firstOrNull()?.id
    }

    /**
     * إفلات عنصر مسحوب عند النقطة ([x], [y]) بإحداثيات الشاشة:
     * - فوق حاوية أخرى (أو خارج حاويته إلى الجذر): ينتقل إليها.
     * - داخل حاويته نفسها في صف/عمود: يُعاد ترتيبه حسب النقطة.
     * - داخل حاويته الحرة: لا شيء (حُدِّث موضعه أثناء السحب).
     */
    fun dropElement(elementId: String, x: Float, y: Float) {
        val project = readyProject() ?: return
        val screen = currentScreen() ?: return
        val layout = layoutOf(project, screen)
        val location = ElementTree.locate(screen.elements, elementId) ?: return
        val element = ElementTree.find(screen.elements, elementId) ?: return
        val exclude = ElementTree.descendantIds(element) + elementId
        val target = LayoutEngine.containerAt(layout, x, y, exclude)
        val targetLayout = layoutKindOf(screen, target)
        val isFlow = targetLayout == ChildLayout.COLUMN || targetLayout == ChildLayout.ROW

        if (target == location.parentId) {
            if (!isFlow) return
            val siblings = childrenOf(screen, target).filter { it.id != elementId }.map { it.id }
            val newIndex = LayoutEngine.dropIndex(layout, siblings, targetLayout == ChildLayout.COLUMN, x, y)
            if (newIndex == location.index) return
            val moved = LayoutEditing.moveTo(screen.elements, elementId, target, newIndex) ?: return
            editScreen(screen.id) { it.copy(elements = moved) }
            return
        }

        val frame = layout.frames[elementId] ?: return
        val parentContent = layout.contents[target ?: LayoutEngine.ROOT]
            ?: DesignRect(0f, 0f, layout.width, layout.height)
        val index = if (isFlow) {
            val siblings = childrenOf(screen, target).map { it.id }
            LayoutEngine.dropIndex(layout, siblings, targetLayout == ChildLayout.COLUMN, x, y)
        } else null
        val moved = LayoutEditing.moveTo(
            screen.elements, elementId, target, index,
            localX = (frame.x - parentContent.x).roundToInt().toFloat(),
            localY = (frame.y - parentContent.y).roundToInt().toFloat()
        ) ?: return
        editScreen(screen.id) { it.copy(elements = moved) }
    }

    private fun currentScreen(): UiScreen? {
        val project = readyProject() ?: return null
        return project.screens.firstOrNull { it.id == _currentScreenId.value }
            ?: project.screens.firstOrNull()
    }

    private fun layoutOf(project: Project, screen: UiScreen): LayoutResult {
        val (w, h) = project.previewSizeFor(screen)
        return LayoutEngine.layout(screen, w, h)
    }

    private fun editScreen(screenId: String, key: String? = null, transform: (UiScreen) -> UiScreen) {
        edit(key) { p -> p.copy(screens = p.screens.map { if (it.id == screenId) transform(it) else it }) }
    }

    // ---- التراجع والإعادة ----

    fun undo() {
        val current = readyProject() ?: return
        val previous = history.undo(current.snapshot()) ?: return
        applySnapshot(current, previous)
    }

    fun redo() {
        val current = readyProject() ?: return
        val next = history.redo(current.snapshot()) ?: return
        applySnapshot(current, next)
    }

    private fun applySnapshot(current: Project, snapshot: DesignSnapshot) {
        commit(
            current.copy(
                screens = snapshot.screens,
                startScreenId = snapshot.startScreenId,
                designSystem = snapshot.designSystem
            )
        )
        // لا نترك تحديدًا أو شاشة حالية تشير إلى شيء اختفى بعد التراجع.
        if (snapshot.screens.none { it.id == _currentScreenId.value }) {
            _currentScreenId.value = snapshot.screens.firstOrNull()?.id
        }
        val visible = snapshot.screens.firstOrNull { it.id == _currentScreenId.value }
        val selected = _selectedId.value
        if (visible == null || selected == null || !ElementTree.contains(visible.elements, selected)) {
            _selectedId.value = null
        }
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    // ---- الحفظ التلقائي ----

    /**
     * يطبّق تعديلًا على المشروع ويسجّله في سجل التراجع ثم يجدول حفظه.
     * [key] يدمج التعديلات المتتالية المتشابهة في خطوة تراجع واحدة. التعديل الذي لا يغيّر شيئًا يُتجاهل.
     */
    fun edit(key: String? = null, transform: (Project) -> Project) {
        val current = _state.value as? EditorState.Ready ?: return
        val next = transform(current.project)
        if (next == current.project) return
        if (next.snapshot() != current.project.snapshot()) {
            history.record(current.project.snapshot(), key)
            refreshHistoryFlags()
        }
        commit(next)
    }

    private fun commit(project: Project) {
        _state.value = EditorState.Ready(project)
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    /** يحفظ فورًا إن وُجدت تعديلات غير محفوظة. */
    fun flush() {
        saveJob?.cancel()
        if (dirty) {
            viewModelScope.launch { saveNow() }
        }
    }

    private suspend fun saveNow() {
        val snapshot = (_state.value as? EditorState.Ready)?.project ?: return
        dirty = false
        try {
            val saved = withContext(Dispatchers.IO) { repository.save(snapshot) }
            _saveFailed.value = false
            // نحدّث وقت التعديل فقط كي لا نمسح تعديلات حدثت أثناء الحفظ.
            _state.update { s ->
                if (s is EditorState.Ready) EditorState.Ready(s.project.copy(updatedAt = saved.updatedAt)) else s
            }
        } catch (e: CancellationException) {
            // أُلغي الحفظ بسبب تعديل أحدث؛ الحفظ التالي سيشمله.
            dirty = true
            throw e
        } catch (e: Exception) {
            dirty = true
            _saveFailed.value = true
        }
    }

    override fun onCleared() {
        // آخر فرصة: حفظ متزامن لأن viewModelScope يُلغى الآن.
        if (dirty) {
            val project = (_state.value as? EditorState.Ready)?.project
            if (project != null) {
                try {
                    repository.save(project)
                } catch (_: Exception) {
                }
            }
        }
        super.onCleared()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 400L
        const val DUPLICATE_OFFSET = 12f
        const val MIN_SCREEN_DP = 200f
        const val MAX_SCREEN_DP = 1600f
    }
}
