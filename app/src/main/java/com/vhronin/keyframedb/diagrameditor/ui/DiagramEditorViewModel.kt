package com.vhronin.keyframedb.diagrameditor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vhronin.keyframedb.diagrameditor.domain.model.Relationship
import com.vhronin.keyframedb.diagrameditor.domain.model.TableEntity
import com.vhronin.keyframedb.diagrameditor.domain.usecase.GetDiagramUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagramEditorUiState(
    val projectName: String = "",
    val isSaved: Boolean = true,
    val tables: List<TableEntity> = emptyList(),
    val relationships: List<Relationship> = emptyList(),
    val isLoading: Boolean = false
)

class DiagramEditorViewModel(
    private val projectId: String,
    private val getDiagram: GetDiagramUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagramEditorUiState(isLoading = true))
    val uiState: StateFlow<DiagramEditorUiState> = _uiState.asStateFlow()

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
        _uiState.value = _uiState.value.copy(
            tables = _uiState.value.tables.map { table ->
                if (table.id == tableId) {
                    table.copy(x = table.x + dx, y = table.y + dy)
                } else {
                    table
                }
            }
        )
    }

    fun onBackClick() {
        // Пока без реализации — здесь появится переход назад к списку проектов
    }

    fun onUndoClick() {
        // Пока без реализации — появится вместе со стеком изменений
    }

    fun onExportClick() {
        // Пока без реализации — появится вместе с PDF-экспортом
    }

    fun onAddTableClick() {
        // Пока без реализации — появится вместе с созданием новой таблицы
    }

    fun onAddRelationClick() {
        // Пока без реализации — появится вместе с рисованием связи между таблицами
    }
}
