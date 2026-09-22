package com.vhronin.keyframedb.diagrameditor.domain.repository

import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import java.io.File

/**
 * Рендерит диаграмму в PDF-файл, готовый к шэрингу — аналог "Export to PDF" в draw.io.
 *
 * Возвращает обычный java.io.File, а не android.net.Uri: превращение файла в content://
 * (через FileProvider) — забота UI-слоя, вызывающего Intent.ACTION_SEND, а не domain
 * (см. использование результата ExportDiagramToPdfUseCase в DiagramEditorScreen).
 */
interface DiagramPdfExporter {
    suspend fun export(diagram: Diagram): File
}
