package com.vhronin.keyframedb.projectlist.data.repository

import com.vhronin.keyframedb.diagrameditor.domain.model.DiagramSummary
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramRepository
import com.vhronin.keyframedb.projectlist.domain.model.Project
import com.vhronin.keyframedb.projectlist.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Отдельного хранилища для "проекта" как сущности нет — проект в этом приложении и есть
 * диаграмма с именем и id. Поэтому список проектов и их удаление — тонкая обёртка над
 * DiagramRepository (единственный экземпляр на всё приложение, см. DiagramEditorModule/
 * ProjectListModule), а не свой параллельный доступ к файлам: так кэш в JsonDiagramRepository
 * никогда не расходится с тем, что видит список — в том числе сразу после удаления.
 *
 * Список не "живой" — см. контракт в ProjectRepository.observeProjects. Экран сам перечитывает
 * его при каждом появлении (см. ProjectListViewModel.refreshProjects).
 */
class JsonProjectRepository(
    private val diagramRepository: DiagramRepository
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> = flow {
        val projects = diagramRepository.getAllDiagrams()
            .map { it.toProject() }
            .sortedByDescending { it.updatedAt }
        emit(projects)
    }

    override suspend fun deleteProject(projectId: String) {
        diagramRepository.deleteDiagram(projectId)
    }

    private fun DiagramSummary.toProject(): Project = Project(
        id = diagram.projectId,
        name = diagram.projectName.ifBlank { "Без названия" },
        updatedAt = updatedAt,
        tableCount = diagram.tables.size
    )
}
