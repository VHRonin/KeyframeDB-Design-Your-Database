package com.vhronin.keyframedb.projectlist.data.repository

import com.vhronin.keyframedb.projectlist.domain.model.Project
import com.vhronin.keyframedb.projectlist.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Временная реализация в памяти — заглушка на этапе, пока не готов
 * data-слой с реальным хранением (JSON-файл проекта на диске).
 * Данные соответствуют HTML-мокапу главного экрана.
 */
class FakeProjectRepository : ProjectRepository {

    private val projects = MutableStateFlow(
        listOf(
            Project(id = "1", name = "e-commerce", updatedAtLabel = "Изменено 2 часа назад", tableCount = 2),
            Project(id = "2", name = "university-db", updatedAtLabel = "Изменено вчера", tableCount = 3),
            Project(id = "3", name = "booking-app", updatedAtLabel = "Изменено 3 дня назад", tableCount = 3)
        )
    )

    override fun observeProjects(): Flow<List<Project>> = projects
}