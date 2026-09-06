package com.vhronin.keyframedb.projectlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vhronin.keyframedb.projectlist.domain.model.Project
import com.vhronin.keyframedb.projectlist.domain.usecase.GetProjectsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

/**
 * Одноразовые события навигации. ViewModel ничего не знает про NavController —
 * она просто сообщает "открой редактор" / "куда-то перейди", а экран решает как.
 */
sealed interface ProjectListNavigationEvent {
    data class OpenEditor(val projectId: String?) : ProjectListNavigationEvent
}

class ProjectListViewModel(
    private val getProjects: GetProjectsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectListUiState(isLoading = true))
    val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<ProjectListNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<ProjectListNavigationEvent> = _navigationEvents.receiveAsFlow()

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
        viewModelScope.launch {
            _navigationEvents.send(ProjectListNavigationEvent.OpenEditor(projectId = null))
        }
    }

    fun onProjectClick(project: Project) {
        viewModelScope.launch {
            _navigationEvents.send(ProjectListNavigationEvent.OpenEditor(projectId = project.id))
        }
    }
}
