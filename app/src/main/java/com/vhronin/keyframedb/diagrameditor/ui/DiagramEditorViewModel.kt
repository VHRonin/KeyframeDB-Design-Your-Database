package com.vhronin.keyframedb.diagrameditor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vhronin.keyframedb.diagrameditor.domain.model.Relationship
import com.vhronin.keyframedb.diagrameditor.domain.model.TableColumn
import com.vhronin.keyframedb.diagrameditor.domain.model.TableEntity
import com.vhronin.keyframedb.diagrameditor.domain.usecase.GetDiagramUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/** Какой именно атрибут какой таблицы сейчас редактируется в BottomSheet. */
data class EditingColumnRef(val tableId: String, val columnId: String)

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
    val renamingTableId: String? = null,
    val editingColumn: EditingColumnRef? = null
)

/** Одноразовые события навигации — по той же схеме, что и в ProjectListViewModel. */
sealed interface DiagramEditorNavigationEvent {
    data object NavigateBack : DiagramEditorNavigationEvent
}

class DiagramEditorViewModel(
    private val projectId: String,
    private val getDiagram: GetDiagramUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagramEditorUiState(isLoading = true))
    val uiState: StateFlow<DiagramEditorUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<DiagramEditorNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<DiagramEditorNavigationEvent> = _navigationEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            getDiagram(projectId).collect { diagram ->
                _uiState.value = _uiState.value.copy(
                    projectName = diagram.projectName,
                    tables = diagram.tables,
                    relationships = diagram.relationships,
                    isLoading = false
                )
            }
        }
    }

    /** Двигаем блок таблицы по холсту — чистая UI-логика, пока ничего не сохраняем. */
    fun onTableDrag(tableId: String, dx: Float, dy: Float) {
        updateTable(tableId) { it.copy(x = it.x + dx, y = it.y + dy) }
        // Реальное перемещение уже произошло — вот теперь меню действительно нужно прятать.
        _uiState.value = _uiState.value.copy(hasDraggedSinceSelection = true)
    }

    /** Отпустили таблицу — примагничиваем её к ближайшей точке сетки. */
    fun onTableDragEnd(tableId: String) {
        updateTable(tableId) { it.copy(x = snapToGrid(it.x), y = snapToGrid(it.y)) }
    }

    private fun snapToGrid(value: Float): Float =
        (value / GRID_STEP).roundToInt() * GRID_STEP

    fun onBackClick() {
        viewModelScope.launch {
            _navigationEvents.send(DiagramEditorNavigationEvent.NavigateBack)
        }
    }

    fun onUndoClick() {
        // Пока без реализации — появится вместе со стеком изменений
    }

    fun onExportClick() {
        // Пока без реализации — появится вместе с PDF-экспортом
    }

    fun onAddTableClick() {
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

    fun onAddRelationClick() {
        // Пока без реализации — появится вместе с рисованием связи между таблицами
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
        _uiState.value = _uiState.value.copy(
            selectedTableId = null,
            hasDraggedSinceSelection = false,
            attributeEditingTableId = null
        )
    }

    fun onRenameTableClick(tableId: String) {
        _uiState.value = _uiState.value.copy(renamingTableId = tableId)
    }

    /** Кнопка "Редактировать" в плавающем меню — включает/выключает кнопки удаления и добавления атрибутов. */
    fun onEditAttributesClick(tableId: String) {
        val isAlreadyEditing = _uiState.value.attributeEditingTableId == tableId
        _uiState.value = _uiState.value.copy(
            attributeEditingTableId = if (isAlreadyEditing) null else tableId
        )
    }

    fun onRenameDismiss() {
        _uiState.value = _uiState.value.copy(renamingTableId = null)
    }

    fun onTableRenamed(tableId: String, newName: String) {
        updateTable(tableId) { it.copy(name = newName) }
        onRenameDismiss()
    }

    fun onBuildRelationshipClick(tableId: String) {
        // Пока без реализации — следующий шаг: рисование связи от конкретной таблицы
    }

    /** Плюсик под таблицей — сразу создаём пустой атрибут и открываем его на редактирование. */
    fun onAddColumnClick(tableId: String) {
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
    }

    fun onDeleteColumn(tableId: String, columnId: String) {
        updateTable(tableId) { table -> table.copy(columns = table.columns.filterNot { it.id == columnId }) }
    }

    fun onColumnNameChanged(tableId: String, columnId: String, newName: String) {
        updateColumn(tableId, columnId) { it.copy(name = newName) }
    }

    fun onColumnTypeChanged(tableId: String, columnId: String, newType: String) {
        updateColumn(tableId, columnId) { it.copy(type = newType) }
    }

    fun onTogglePrimaryKey(tableId: String, columnId: String) {
        updateColumn(tableId, columnId) { it.copy(isPrimaryKey = !it.isPrimaryKey) }
    }

    fun onToggleForeignKey(tableId: String, columnId: String) {
        updateColumn(tableId, columnId) { it.copy(isForeignKey = !it.isForeignKey) }
    }

    private fun updateTable(tableId: String, transform: (TableEntity) -> TableEntity) {
        _uiState.value = _uiState.value.copy(
            tables = _uiState.value.tables.map { table ->
                if (table.id == tableId) transform(table) else table
            }
        )
    }

    private fun updateColumn(tableId: String, columnId: String, transform: (TableColumn) -> TableColumn) {
        updateTable(tableId) { table ->
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
    }
}
