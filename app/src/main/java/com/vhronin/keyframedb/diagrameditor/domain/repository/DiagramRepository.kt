package com.vhronin.keyframedb.diagrameditor.domain.repository

import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.diagrameditor.domain.model.DiagramSummary
import kotlinx.coroutines.flow.Flow

interface DiagramRepository {
    fun observeDiagram(projectId: String): Flow<Diagram>
    suspend fun saveDiagram(diagram: Diagram)
    suspend fun deleteDiagram(projectId: String)

    /**
     * Все сохранённые диаграммы разом — источник и для списка проектов, и для подбора
     * дефолтного имени новой схемы. Единственная реализация (JsonDiagramRepository) —
     * общий на всё приложение синглтон, поэтому кэш не может разойтись с тем, что видят
     * оба потребителя.
     */
    suspend fun getAllDiagrams(): List<DiagramSummary>
}
