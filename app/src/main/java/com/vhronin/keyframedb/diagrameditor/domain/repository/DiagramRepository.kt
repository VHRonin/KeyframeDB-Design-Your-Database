package com.vhronin.keyframedb.diagrameditor.domain.repository

import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import kotlinx.coroutines.flow.Flow

interface DiagramRepository {
    fun observeDiagram(projectId: String): Flow<Diagram>
    suspend fun saveDiagram(diagram: Diagram)
}
