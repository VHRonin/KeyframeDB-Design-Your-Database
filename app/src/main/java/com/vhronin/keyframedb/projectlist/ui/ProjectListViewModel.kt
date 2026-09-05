package com.vhronin.keyframedb.projectlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vhronin.keyframedb.projectlist.domain.model.Project
import com.vhronin.keyframedb.projectlist.domain.usecase.GetProjectsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectListUiState(
    val projects: List<Project> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
) {
    val visibleProjects: List<Project>
        get() = if (searchQuery.isBlank()) {
            projects
        } else {
            projects.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
}

class ProjectListViewModel(
    private val getProjects: GetProjectsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectListUiState(isLoading = true))
    val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getProjects().collect { projects ->
                _uiState.value = _uiState.value.copy(projects = projects, isLoading = false)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onCreateProjectClick() {
        // Пока без реализации — здесь появится переход к созданию проекта / в редактор
    }

    fun onProjectClick(project: Project) {
        // Пока без реализации — здесь появится переход в редактор конкретного проекта
    }
}
