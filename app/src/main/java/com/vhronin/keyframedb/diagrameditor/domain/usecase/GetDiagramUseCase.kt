package com.vhronin.keyframedb.diagrameditor.domain.usecase

import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramRepository
import kotlinx.coroutines.flow.Flow

class GetDiagramUseCase(
    private val repository: DiagramRepository
) {
    operator fun invoke(projectId: String): Flow<Diagram> = repository.observeDiagram(projectId)
}
