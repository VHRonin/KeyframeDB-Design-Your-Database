package com.vhronin.keyframedb.projectlist.domain.usecase

import com.vhronin.keyframedb.projectlist.domain.model.Project
import com.vhronin.keyframedb.projectlist.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

class GetProjectsUseCase(
    private val repository: ProjectRepository
) {
    operator fun invoke(): Flow<List<Project>> = repository.observeProjects()
}
