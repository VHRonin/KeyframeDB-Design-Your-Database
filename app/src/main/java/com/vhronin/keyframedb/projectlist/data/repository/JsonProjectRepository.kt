package com.vhronin.keyframedb.projectlist.data.repository

import android.content.Context
import com.vhronin.keyframedb.diagrameditor.domain.model.Diagram
import com.vhronin.keyframedb.projectlist.domain.model.Project
import com.vhronin.keyframedb.projectlist.domain.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Отдельного хранилища для "проекта" как сущности нет — проект в этом приложении и есть
 * диаграмма с именем и id. Поэтому список проектов строим как сводку по тем же файлам,
 * что пишет JsonDiagramRepository (filesDir/diagrams/<projectId>.json): читаем каждый файл,
 * берём из него имя и число таблиц, а дату изменения — из mtime самого файла.
 *
 * Список не "живой" (без FileObserver) — см. контракт в ProjectRepository.observeProjects.
 * Экран сам перечитывает его при каждом появлении (в том числе при возврате из редактора,
 * см. ProjectListViewModel.refreshProjects/ProjectListScreen), этого достаточно для одного
 * локального пользователя без параллельного редактирования.
 */
class JsonProjectRepository(
    private val context: Context
) : ProjectRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val diagramsDir: File
        get() = File(context.filesDir, "diagrams")

    override fun observeProjects(): Flow<List<Project>> = flow {
        emit(readProjects())
    }

    private suspend fun readProjects(): List<Project> = withContext(Dispatchers.IO) {
        val files = diagramsDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?: emptyArray()

        files.mapNotNull { file ->
            runCatching {
                val diagram = json.decodeFromString<Diagram>(file.readText())
                diagram.toProject(updatedAt = file.lastModified())
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    private fun Diagram.toProject(updatedAt: Long): Project = Project(
        id = projectId,
        name = projectName.ifBlank { "Без названия" },
        updatedAt = updatedAt,
        tableCount = tables.size
    )
}
