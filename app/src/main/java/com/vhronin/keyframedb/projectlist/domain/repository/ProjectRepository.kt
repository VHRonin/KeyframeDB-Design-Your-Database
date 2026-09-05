package com.vhronin.keyframedb.projectlist.domain.repository

import com.vhronin.keyframedb.projectlist.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
}
