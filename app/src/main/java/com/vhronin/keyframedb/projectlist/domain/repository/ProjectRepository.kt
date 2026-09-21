package com.vhronin.keyframedb.projectlist.domain.repository

import com.vhronin.keyframedb.projectlist.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    /**
     * Список проектов на момент подписки — не бесконечный живой поток, обновлений после
     * первой эмиссии не будет (см. JsonProjectRepository). Чтобы получить свежие данные,
     * нужно подписаться заново.
     */
    fun observeProjects(): Flow<List<Project>>

    suspend fun deleteProject(projectId: String)
}
