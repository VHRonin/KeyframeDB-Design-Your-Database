package com.vhronin.keyframedb.diagrameditor.domain.usecase

import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramRepository

private const val DEFAULT_NAME_PREFIX = "Новая диаграмма"
private val DEFAULT_NAME_PATTERN = Regex("""^${Regex.escape(DEFAULT_NAME_PREFIX)} \((\d+)\)$""")

/**
 * Имя для только что созданной, ещё ни разу не сохранённой схемы — "Новая диаграмма (N)".
 *
 * N — на единицу больше максимального номера среди уже существующих схем с таким же
 * шаблоном имени, а не просто счётчик по их количеству: иначе после удаления схемы номера
 * могли бы повториться у двух одновременно существующих ("Новая диаграмма (2)" дважды).
 */
class GenerateDefaultDiagramNameUseCase(
    private val repository: DiagramRepository
) {
    suspend operator fun invoke(): String {
        val maxNumber = repository.getAllDiagrams()
            .mapNotNull { summary ->
                DEFAULT_NAME_PATTERN.matchEntire(summary.diagram.projectName)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
            .maxOrNull() ?: 0

        return "$DEFAULT_NAME_PREFIX (${maxNumber + 1})"
    }
}
