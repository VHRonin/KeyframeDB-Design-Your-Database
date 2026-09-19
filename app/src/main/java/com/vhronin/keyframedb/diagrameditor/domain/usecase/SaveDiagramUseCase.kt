package com.vhronin.keyframedb.diagrameditor.domain.usecase

import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramRepository

class SaveDiagramUseCase(
    private val repository: DiagramRepository
) {
    suspend operator fun invoke(diagram: Diagram) = repository.saveDiagram(diagram)
}
