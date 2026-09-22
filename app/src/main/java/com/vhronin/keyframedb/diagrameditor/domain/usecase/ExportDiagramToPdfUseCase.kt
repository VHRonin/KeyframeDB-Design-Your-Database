package com.vhronin.keyframedb.diagrameditor.domain.usecase

import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramPdfExporter
import java.io.File

class ExportDiagramToPdfUseCase(
    private val exporter: DiagramPdfExporter
) {
    suspend operator fun invoke(diagram: Diagram): File = exporter.export(diagram)
}
