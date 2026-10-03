package com.vhronin.keyframedb.diagrameditor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.diagrameditor.domain.model.Relationship
import com.vhronin.keyframedb.diagrameditor.domain.model.RelationshipType
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColor
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColumn
import com.vhronin.keyframedb.diagrameditor.domain.model.TableEntity
import com.vhronin.keyframedb.diagrameditor.domain.usecase.ExportDiagramToPdfUseCase
import com.vhronin.keyframedb.diagrameditor.domain.usecase.GenerateDefaultDiagramNameUseCase
import com.vhronin.keyframedb.diagrameditor.domain.usecase.GetDiagramUseCase
import com.vhronin.keyframedb.diagrameditor.domain.usecase.SaveDiagramUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Какой именно атрибут какой таблицы сейчас редактируется в BottomSheet. */
data class EditingColumnRef(val tableId: String, val columnId: String)

/**
 * Режим выбора АТРИБУТОВ для новой связи (не таблиц целиком — тап по строке конкретного
 * атрибута сразу даёт и id таблицы, и id атрибута внутри неё).
 *
 * fromTableId == null, пока не выбрали первый атрибут вообще (запуск с кнопки на тулбаре,
 * без заранее известного источника) — тогда засчитывается тап по строке атрибута в любой
 * таблице. Если fromTableId уже известен (запуск из меню конкретной таблицы, см.
 * onBuildRelationshipClick), а fromColumnId ещё null — источник как таблица уже выбран,
 * но тапнуть нужно именно по строке атрибута внутри неё, тапы по чужим таблицам на этом
 * шаге игнорируются (см. onLinkColumnTap). Как только оба поля заполнены — источник выбран
 * целиком, и следующий тап по строке атрибута (в любой таблице, включая ту же самую —
 * см. комментарий в onLinkColumnTap про самоссылающиеся связи) задаёт цель связи.
 */
data class LinkingState(val fromTableId: String? = null, val fromColumnId: String? = null)

/** Оба атрибута для новой связи уже выбраны — осталось подтвердить вид связи в BottomSheet. */
data class PendingRelationship(
    val fromTableId: String,
    val fromColumnId: String,
    val toTableId: String,
    val toColumnId: String,
    val type: RelationshipType = RelationshipType.ONE_TO_MANY
)

data class DiagramEditorUiState(
    val projectName: String = "",
    val isSaved: Boolean = true,
    val tables: List<TableEntity> = emptyList(),
    val relationships: List<Relationship> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTableId: String? = null,
    // Таблицу уже выделили, но ещё ни разу не подвигали с момента выделения —
    // именно в этом окне и должно быть видно плавающее меню (см. onTableSelected/onTableDragEnd).
    val hasDraggedSinceSelection: Boolean = false,
    // Таблица, для которой сейчас включён режим редактирования атрибутов
    // (кнопки удаления атрибута и добавления атрибута видны только пока это её id).
    val attributeEditingTableId: String? = null,
    // Режим создания новой связи — null, если сейчас не создаём (см. LinkingState выше).
    val linkingState: LinkingState? = null,
    // Обе таблицы для новой связи выбраны — открыт BottomSheet выбора вида связи.
    val pendingRelationship: PendingRelationship? = null,
    // Существующая связь, которую сейчас редактируют/удаляют (тап по подписи на холсте).
    val editingRelationshipId: String? = null,
    val renamingTableId: String? = null,
    val editingColumn: EditingColumnRef? = null,
    // Шторка переименования самой схемы (тап по названию в топ-баре) — своё имя без ref на таблицу.
    val isRenamingProject: Boolean = false,
    // Есть ли что отменять — только чтобы притушить кнопку "Отменить" в топ-баре, когда стек пуст.
    val canUndo: Boolean = false,
    // Идёт рендер PDF в фоне (см. onExportClick) — кнопка экспорта в топ-баре на это время
    // блокируется и показывает вместо иконки спиннер, чтобы повторный тап не запускал
    // параллельный экспорт того же файла.
    val isExporting: Boolean = false
)

/** Одноразовые события навигации — по той же схеме, что и в ProjectListViewModel. */
sealed interface DiagramEditorNavigationEvent {
    data object NavigateBack : DiagramEditorNavigationEvent

    /** PDF готов на диске — экран открывает системный диалог "Поделиться" через FileProvider. */
    data class SharePdf(val file: File) : DiagramEditorNavigationEvent

    /** Экспорт не удался (например, ошибка записи на диск) — экран покажет короткое сообщение. */
    data object ExportFailed : DiagramEditorNavigationEvent
}

class DiagramEditorViewModel(
    private val projectId: String,
    private val getDiagram: GetDiagramUseCase,
    private val saveDiagram: SaveDiagramUseCase,
    private val generateDefaultDiagramName: GenerateDefaultDiagramNameUseCase,
    private val exportDiagramToPdf: ExportDiagramToPdfUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagramEditorUiState(isLoading = true))
    val uiState: StateFlow<DiagramEditorUiState> = _uiState.asStateFlow()

    // Стек отмены: снимки (tables, relationships) ДО каждого мутирующего действия.
    // Обычный список, а не что-то более хитрое — таблиц и связей мало, лишний снимок
    // стоит копейки, а код при этом простой (в духе всего остального в этом экране).
    private val undoStack = mutableListOf<Pair<List<TableEntity>, List<Relationship>>>()

    // Ключ последнего "непрерывного" действия — перетаскивание, печать в поле, перебор
    // цветов/видов связи. Пока новый вызов приходит с ТЕМ ЖЕ ключом, новый снимок не
    // делаем: считаем это продолжением того же шага отмены (иначе одна отмена откатывала
    // бы один пиксель сдвига или одну напечатанную букву). null — действие атомарное,
    // снимок обязателен всегда (см. pushUndoSnapshot).
    private var lastCoalesceKey: String? = null

    private fun pushUndoSnapshot(coalesceKey: String? = null) {
        if (coalesceKey != null && coalesceKey == lastCoalesceKey) return
        undoStack.add(_uiState.value.tables to _uiState.value.relationships)
        if (undoStack.size > MAX_UNDO_STEPS) undoStack.removeAt(0)
        lastCoalesceKey = coalesceKey
        _uiState.value = _uiState.value.copy(canUndo = true)
    }

    private val _navigationEvents = Channel<DiagramEditorNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<DiagramEditorNavigationEvent> = _navigationEvents.receiveAsFlow()

    // Последний вариант диаграммы, который точно лежит на диске — null, пока не пришла
    // первая загрузка (см. init). Служит и "печкой" для сравнения (изменилось ли что-то с
    // прошлого сохранения), и защитой от гонки: пока он null, автосохранение не пишет на
    // диск, чтобы не затереть реальные данные пустым состоянием ещё до того, как они загрузились.
    private var lastPersistedSnapshot: Diagram? = null

    init {
        viewModelScope.launch {
            getDiagram(projectId).collect { diagram ->
                // lastPersistedSnapshot остаётся как есть на диске (в том числе с пустым
                // именем) — а не resolvedName ниже: иначе автосохранение не увидит разницы
                // и никогда не запишет на диск сгенерированное имя новой схемы.
                lastPersistedSnapshot = diagram
                // Пустое имя означает, что схему вообще ни разу не сохраняли (см.
                // JsonDiagramRepository.readFromDisk) — переименование всегда требует
                // непустой строки (см. RenameProjectSheet), так что у реально сохранённой
                // схемы имя пустым быть не может.
                val resolvedName = diagram.projectName.ifBlank { generateDefaultDiagramName() }
                _uiState.value = _uiState.value.copy(
                    projectName = resolvedName,
                    tables = diagram.tables,
                    relationships = diagram.relationships,
                    isLoading = false,
                    isSaved = resolvedName == diagram.projectName
                )
            }
        }

        // Автосохранение: следим за той частью состояния, что реально относится к диаграмме
        // (имя схемы, таблицы, связи — не за меню/шторками/выделением), и как только она
        // отличается от lastPersistedSnapshot, сразу гасим "Сохранено" в топ-баре, а сам файл
        // на диске переписываем с debounce — чтобы не дёргать диск на каждый пиксель
        // перетаскивания или каждую напечатанную букву, а только когда пользователь ненадолго
        // остановился.
        viewModelScope.launch {
            uiState
                .map { state -> Diagram(projectId, state.projectName, state.tables, state.relationships) }
                .distinctUntilChanged()
                .onEach { snapshot ->
                    if (lastPersistedSnapshot != null && snapshot != lastPersistedSnapshot) {
                        _uiState.update { it.copy(isSaved = false) }
                    }
                }
                .debounce(AUTO_SAVE_DEBOUNCE_MS)
                .collect { snapshot -> persist(snapshot) }
        }
    }

    /** Действительно пишет снимок на диск — но только если он ещё не совпадает с уже сохранённым. */
    private suspend fun persist(snapshot: Diagram) {
        if (lastPersistedSnapshot == null || snapshot == lastPersistedSnapshot) return
        saveDiagram(snapshot)
        lastPersistedSnapshot = snapshot
        _uiState.update { it.copy(isSaved = true) }
    }

    /**
     * Двигаем блок таблицы по холсту. Само перемещение сохранится на диск автоматически
     * (см. автосохранение в init) — здесь только чистая UI-логика перетаскивания.
     */
    fun onTableDrag(tableId: String, dx: Float, dy: Float) {
        updateTable(tableId, coalesceKey = "drag:$tableId") { it.copy(x = it.x + dx, y = it.y + dy) }
        // Реальное перемещение уже произошло — вот теперь меню действительно нужно прятать.
        _uiState.value = _uiState.value.copy(hasDraggedSinceSelection = true)
    }

    /** Отпустили таблицу — примагничиваем её к ближайшей точке сетки. */
    fun onTableDragEnd(tableId: String) {
        // Тот же ключ, что и в onTableDrag — снап к сетке считается частью того же
        // перетаскивания, а не отдельным шагом отмены. Дальше окно закрываем.
        updateTable(tableId, coalesceKey = "drag:$tableId") { it.copy(x = snapToGrid(it.x), y = snapToGrid(it.y)) }
        lastCoalesceKey = null
    }

    private fun snapToGrid(value: Float): Float =
        (value / GRID_STEP).roundToInt() * GRID_STEP

    /**
     * Уходя с экрана, не ждём debounce автосохранения — досохраняем немедленно, если
     * есть что: иначе правка, сделанная за миг до "Назад", могла бы потеряться.
     */
    fun onBackClick() {
        viewModelScope.launch {
            val state = _uiState.value
            persist(Diagram(projectId, state.projectName, state.tables, state.relationships))
            _navigationEvents.send(DiagramEditorNavigationEvent.NavigateBack)
        }
    }

    /** Кнопка "Отменить" в топ-баре — откатывает последний шаг стека изменений. */
    fun onUndoClick() {
        val previous = undoStack.removeLastOrNull() ?: return
        lastCoalesceKey = null
        // Что бы сейчас ни было открыто (меню, шторка, режим связывания) — закрываем: после
        // отката оно может указывать на объект, которого уже нет, или на устаревшее значение.
        _uiState.value = _uiState.value.copy(
            tables = previous.first,
            relationships = previous.second,
            canUndo = undoStack.isNotEmpty(),
            selectedTableId = null,
            hasDraggedSinceSelection = false,
            attributeEditingTableId = null,
            linkingState = null,
            pendingRelationship = null,
            editingRelationshipId = null,
            renamingTableId = null,
            editingColumn = null
        )
    }

    /**
     * Кнопка экспорта в топ-баре — рендерит PDF из ТЕКУЩЕГО состояния на экране (а не из
     * последней сохранённой на диск версии, см. lastPersistedSnapshot): пользователь ждёт
     * "экспортируй то, что я вижу", а не то, что успело долежать до автосохранения.
     * Сам PDF рендерится в фоне (см. PdfDiagramExporter), поэтому UI не блокируется —
     * isExporting только не даёт запустить второй экспорт поверх первого.
     */
    fun onExportClick() {
        if (_uiState.value.isExporting) return
        val state = _uiState.value
        val diagram = Diagram(projectId, state.projectName, state.tables, state.relationships)
        _uiState.value = state.copy(isExporting = true)
        viewModelScope.launch {
            val result = runCatching { exportDiagramToPdf(diagram) }
            _uiState.value = _uiState.value.copy(isExporting = false)
            result.fold(
                onSuccess = { file -> _navigationEvents.send(DiagramEditorNavigationEvent.SharePdf(file)) },
                onFailure = { _navigationEvents.send(DiagramEditorNavigationEvent.ExportFailed) }
            )
        }
    }

    /** Тап по названию схемы в топ-баре — открывает шторку переименования. */
    fun onRenameProjectClick() {
        _uiState.value = _uiState.value.copy(isRenamingProject = true)
    }

    fun onProjectRenameDismiss() {
        _uiState.value = _uiState.value.copy(isRenamingProject = false)
    }

    /**
     * Переименование самой схемы — в отличие от таблиц/атрибутов, никак не связано
     * с tables/relationships, поэтому в общий стек отмены не попадает (там и так пусто
     * при первом действии, а откатывать имя схемы отдельной кнопкой "Отменить" не нужно).
     */
    fun onProjectRenamed(newName: String) {
        _uiState.value = _uiState.value.copy(projectName = newName, isRenamingProject = false)
    }

    fun onAddTableClick() {
        pushUndoSnapshot()
        val index = _uiState.value.tables.size
        val newTable = TableEntity(
            id = UUID.randomUUID().toString(),
            name = "new_table",
            // Раскладываем новые таблицы небольшой "лесенкой", чтобы не ставить их
            // ровно друг на друга — а дальше пользователь просто перетащит куда нужно.
            x = 16f + (index % 4) * 32f,
            y = 16f + (index / 4) * 32f
        )
        _uiState.value = _uiState.value.copy(tables = _uiState.value.tables + newTable)
    }

    /** Кнопка "Связь" на тулбаре — то же самое, но без заранее известной первой таблицы. */
    fun onAddRelationClick() {
        _uiState.value = _uiState.value.copy(
            linkingState = LinkingState(),
            selectedTableId = null,
            hasDraggedSinceSelection = false
        )
    }

    /**
     * Выбор таблицы (тап или начало долгого нажатия перед перетаскиванием).
     * Заново открываем окно показа плавающего меню и гасим режим редактирования атрибутов,
     * если выделили другую таблицу.
     */
    fun onTableSelected(tableId: String) {
        val attributeEditingTableId =
            if (_uiState.value.attributeEditingTableId == tableId) tableId else null
        _uiState.value = _uiState.value.copy(
            selectedTableId = tableId,
            hasDraggedSinceSelection = false,
            attributeEditingTableId = attributeEditingTableId
        )
    }

    /** Тап по пустому месту холста — снимаем выделение и выключаем редактирование атрибутов. */
    fun onCanvasTap() {
        if (_uiState.value.linkingState != null) {
            onCancelLinking()
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedTableId = null,
            hasDraggedSinceSelection = false,
            attributeEditingTableId = null
        )
    }

    /** Кнопка "Переименовать" в плавающем меню — открывает шторку и прячет само меню. */
    fun onRenameTableClick(tableId: String) {
        _uiState.value = _uiState.value.copy(
            renamingTableId = tableId,
            selectedTableId = null,
            hasDraggedSinceSelection = false
        )
    }

    /** Кнопка "Редактировать" в плавающем меню — включает/выключает кнопки удаления и добавления атрибутов, само меню прячет. */
    fun onEditAttributesClick(tableId: String) {
        val isAlreadyEditing = _uiState.value.attributeEditingTableId == tableId
        _uiState.value = _uiState.value.copy(
            attributeEditingTableId = if (isAlreadyEditing) null else tableId,
            selectedTableId = null,
            hasDraggedSinceSelection = false
        )
    }

    fun onRenameDismiss() {
        _uiState.value = _uiState.value.copy(renamingTableId = null)
        lastCoalesceKey = null
    }

    fun onTableRenamed(tableId: String, newName: String) {
        updateTable(tableId) { it.copy(name = newName) }
        onRenameDismiss()
    }

    /**
     * Выбор цвета таблицы в той же шторке переименования — применяется сразу по тапу на кружок.
     * Схлопываем по id таблицы: перебор нескольких цветов подряд, пока решаешь, — один шаг отмены.
     */
    fun onTableColorChanged(tableId: String, color: TableColor) {
        updateTable(tableId, coalesceKey = "table-color:$tableId") { it.copy(color = color) }
    }

    /**
     * Кнопка "Удалить таблицу" в шторке переименования — вместе с таблицей удаляем и все связи,
     * которые её касались (иначе они остались бы висеть на несуществующий id — DiagramCanvas/
     * PdfDiagramExporter такие молча пропускают через ?: return@forEach, но смысла в них уже нет).
     */
    fun onDeleteTable(tableId: String) {
        pushUndoSnapshot()
        val state = _uiState.value
        _uiState.value = state.copy(
            tables = state.tables.filterNot { it.id == tableId },
            relationships = state.relationships.filterNot { it.fromTableId == tableId || it.toTableId == tableId },
            renamingTableId = null,
            selectedTableId = state.selectedTableId.takeIf { it != tableId },
            attributeEditingTableId = state.attributeEditingTableId.takeIf { it != tableId }
        )
    }

    /** Кнопка "Связь" в плавающем меню — входим в режим связывания с уже известной первой таблицей. */
    fun onBuildRelationshipClick(tableId: String) {
        _uiState.value = _uiState.value.copy(
            linkingState = LinkingState(fromTableId = tableId),
            selectedTableId = null,
            hasDraggedSinceSelection = false
        )
    }

    /**
     * Тап по строке конкретного атрибута во время режима связывания — приходит сразу с id
     * его таблицы и id самого атрибута (в отличие от старого связывания по таблицам целиком,
     * здесь ровно этой пары достаточно, чтобы однозначно задать точку связи).
     *
     * Если источник ещё не выбран целиком (fromColumnId == null): когда таблица-источник уже
     * была зафиксирована заранее (запуск из меню конкретной таблицы — fromTableId != null),
     * тап засчитывается только по строке внутри НЕЁ — тап по чужой таблице на этом шаге
     * молча игнорируется, чтобы случайный тап не подменил уже явно выбранный источник.
     *
     * Если источник уже выбран целиком, тап по ТОЙ ЖЕ строке — отмена (тот же жест "тапнуть
     * по источнику ещё раз", что раньше был на уровне целой таблицы). Тап по любой ДРУГОЙ
     * строке, в том числе в той же самой таблице — источник и цель выбраны, открываем
     * BottomSheet подтверждения вида связи. Разрешать цель в той же таблице, что и источник, —
     * осознанное расширение по сравнению со старым связыванием таблиц целиком: атрибут может
     * ссылаться на другой атрибут своей же таблицы (например, self-relation вроде
     * `employee.manager_id -> employee.id`), а раньше такая связь была в принципе невозможна.
     */
    fun onLinkColumnTap(tableId: String, columnId: String) {
        val linking = _uiState.value.linkingState ?: return
        val knownFromTableId = linking.fromTableId
        if (linking.fromColumnId == null) {
            if (knownFromTableId != null && tableId != knownFromTableId) return
            _uiState.value = _uiState.value.copy(
                linkingState = linking.copy(fromTableId = tableId, fromColumnId = columnId)
            )
            return
        }
        if (tableId == knownFromTableId && columnId == linking.fromColumnId) {
            onCancelLinking()
            return
        }
        _uiState.value = _uiState.value.copy(
            linkingState = null,
            pendingRelationship = PendingRelationship(
                fromTableId = knownFromTableId!!,
                fromColumnId = linking.fromColumnId,
                toTableId = tableId,
                toColumnId = columnId
            )
        )
    }

    /** Отмена режима связывания — по кнопке закрытия баннера или тапу по пустому месту холста. */
    fun onCancelLinking() {
        _uiState.value = _uiState.value.copy(linkingState = null)
    }

    /** Выбор вида связи в BottomSheet ещё не созданной связи. */
    fun onPendingRelationshipTypeChanged(type: RelationshipType) {
        val pending = _uiState.value.pendingRelationship ?: return
        _uiState.value = _uiState.value.copy(pendingRelationship = pending.copy(type = type))
    }

    /**
     * Кнопка "Создать связь" в BottomSheet — сохраняем связь, если такой ещё нет.
     * Дубликат теперь проверяем по паре конкретных атрибутов, а не паре таблиц: в отличие
     * от старого связывания таблиц целиком, между одной и той же парой таблиц теперь вполне
     * может быть несколько связей через разные атрибуты (например, составной внешний ключ
     * из двух отдельных полей) — такие больше не считаются дубликатами друг друга.
     */
    fun onConfirmPendingRelationship() {
        val pending = _uiState.value.pendingRelationship ?: return
        val alreadyLinked = _uiState.value.relationships.any {
            it.fromColumnId == pending.fromColumnId && it.toColumnId == pending.toColumnId
        }
        if (!alreadyLinked) {
            pushUndoSnapshot()
            val newRelationship = Relationship(
                id = UUID.randomUUID().toString(),
                fromTableId = pending.fromTableId,
                fromColumnId = pending.fromColumnId,
                toTableId = pending.toTableId,
                toColumnId = pending.toColumnId,
                type = pending.type
            )
            _uiState.value = _uiState.value.copy(relationships = _uiState.value.relationships + newRelationship)
        }
        _uiState.value = _uiState.value.copy(pendingRelationship = null)
    }

    /** Закрытие BottomSheet новой связи без подтверждения — просто отменяем создание. */
    fun onDismissPendingRelationship() {
        _uiState.value = _uiState.value.copy(pendingRelationship = null)
    }

    /** Тап по подписи существующей связи на холсте — открываем её на редактирование/удаление. */
    fun onRelationshipBadgeClick(relationshipId: String) {
        _uiState.value = _uiState.value.copy(editingRelationshipId = relationshipId)
    }

    /** Смена вида связи прямо в BottomSheet редактирования — применяется сразу. */
    fun onEditingRelationshipTypeChanged(type: RelationshipType) {
        val relationshipId = _uiState.value.editingRelationshipId ?: return
        pushUndoSnapshot(coalesceKey = "relationship-type:$relationshipId")
        _uiState.value = _uiState.value.copy(
            relationships = _uiState.value.relationships.map { relationship ->
                if (relationship.id == relationshipId) relationship.copy(type = type) else relationship
            }
        )
    }

    /** Кнопка "Удалить связь" в BottomSheet редактирования. */
    fun onDeleteRelationship(relationshipId: String) {
        pushUndoSnapshot()
        _uiState.value = _uiState.value.copy(
            relationships = _uiState.value.relationships.filterNot { it.id == relationshipId },
            editingRelationshipId = null
        )
    }

    fun onRelationshipEditDismiss() {
        _uiState.value = _uiState.value.copy(editingRelationshipId = null)
        lastCoalesceKey = null
    }

    /** Плюсик под таблицей — сразу создаём пустой атрибут и открываем его на редактирование. */
    fun onAddColumnClick(tableId: String) {
        pushUndoSnapshot()
        val newColumn = TableColumn(id = UUID.randomUUID().toString(), name = "", type = "")
        _uiState.value = _uiState.value.copy(
            tables = _uiState.value.tables.map { table ->
                if (table.id == tableId) table.copy(columns = table.columns + newColumn) else table
            },
            editingColumn = EditingColumnRef(tableId, newColumn.id)
        )
    }

    fun onColumnClick(tableId: String, columnId: String) {
        _uiState.value = _uiState.value.copy(editingColumn = EditingColumnRef(tableId, columnId))
    }

    fun onColumnEditDismiss() {
        _uiState.value = _uiState.value.copy(editingColumn = null)
        lastCoalesceKey = null
    }

    /**
     * Удаление атрибута теперь каскадно чистит и связи, которые опирались именно на него —
     * связь строится между конкретными атрибутами, так что иначе она осталась бы висеть на
     * несуществующий columnId (та же идея, что и в onDeleteTable для целой таблицы). Поэтому,
     * в отличие от большинства других правок таблицы, здесь нельзя обойтись общей updateTable —
     * нужно одним снимком поменять и tables, и relationships.
     */
    fun onDeleteColumn(tableId: String, columnId: String) {
        pushUndoSnapshot()
        val state = _uiState.value
        _uiState.value = state.copy(
            tables = state.tables.map { table ->
                if (table.id == tableId) table.copy(columns = table.columns.filterNot { it.id == columnId }) else table
            },
            relationships = state.relationships.filterNot {
                (it.fromTableId == tableId && it.fromColumnId == columnId) ||
                        (it.toTableId == tableId && it.toColumnId == columnId)
            }
        )
    }

    fun onColumnNameChanged(tableId: String, columnId: String, newName: String) {
        updateColumn(tableId, columnId, coalesceKey = "column-name:$tableId:$columnId") { it.copy(name = newName) }
    }

    fun onColumnTypeChanged(tableId: String, columnId: String, newType: String) {
        updateColumn(tableId, columnId, coalesceKey = "column-type:$tableId:$columnId") { it.copy(type = newType) }
    }

    fun onTogglePrimaryKey(tableId: String, columnId: String) {
        updateColumn(tableId, columnId) { it.copy(isPrimaryKey = !it.isPrimaryKey) }
    }

    fun onToggleForeignKey(tableId: String, columnId: String) {
        updateColumn(tableId, columnId) { it.copy(isForeignKey = !it.isForeignKey) }
    }

    /**
     * Централизованная точка мутации таблиц — через неё проходит большинство действий,
     * поэтому здесь же удобно вести стек отмены. coalesceKey — см. pushUndoSnapshot;
     * null (по умолчанию) означает "самостоятельное действие", снимок делается всегда.
     */
    private fun updateTable(tableId: String, coalesceKey: String? = null, transform: (TableEntity) -> TableEntity) {
        pushUndoSnapshot(coalesceKey)
        _uiState.value = _uiState.value.copy(
            tables = _uiState.value.tables.map { table ->
                if (table.id == tableId) transform(table) else table
            }
        )
    }

    private fun updateColumn(
        tableId: String,
        columnId: String,
        coalesceKey: String? = null,
        transform: (TableColumn) -> TableColumn
    ) {
        updateTable(tableId, coalesceKey) { table ->
            table.copy(
                columns = table.columns.map { column ->
                    if (column.id == columnId) transform(column) else column
                }
            )
        }
    }

    companion object {
        /** Шаг сетки в dp. Должен совпадать с шагом точек на холсте (DiagramEditorScreen). */
        const val GRID_STEP = 16f
        /** Сколько шагов отмены храним одновременно — дальше самые старые просто выталкиваются. */
        private const val MAX_UNDO_STEPS = 50
        /** Пауза после последнего изменения, прежде чем автосохранение реально пишет на диск. */
        private const val AUTO_SAVE_DEBOUNCE_MS = 600L
    }
}
