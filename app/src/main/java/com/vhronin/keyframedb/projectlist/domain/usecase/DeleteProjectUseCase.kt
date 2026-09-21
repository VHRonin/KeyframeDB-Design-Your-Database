package com.vhronin.keyframedb.projectlist.domain.usecase

import com.vhronin.keyframedb.projectlist.domain.repository.ProjectRepository

class DeleteProjectUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(projectId: String) = repository.deleteProject(projectId)
}
