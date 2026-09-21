package com.vhronin.keyframedb.projectlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vhronin.keyframedb.projectlist.domain.model.Project
import com.vhronin.keyframedb.projectlist.domain.usecase.DeleteProjectUseCase
import com.vhronin.keyframedb.projectlist.domain.usecase.GetProjectsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ProjectListUiState(
    val projects: List<Project> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    // Проект, для которого сейчас показан диалог подтверждения удаления — сам факт удаления
    // необратим, поэтому без явного подтверждения не обойтись (в отличие от прочих действий
    // на этом экране).
    val pendingDeleteProject: Project? = null
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
    data class OpenEditor(val projectId: String) : ProjectListNavigationEvent
}

class ProjectListViewModel(
    private val getProjects: GetProjectsUseCase,
    private val deleteProject: DeleteProjectUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectListUiState(isLoading = true))
    val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<ProjectListNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<ProjectListNavigationEvent> = _navigationEvents.receiveAsFlow()

    /**
     * Список проектов сейчас не "живой" поток (см. JsonProjectRepository) — перечитываем его
     * явно при каждом появлении экрана (см. LaunchedEffect в ProjectListScreen), а не только
     * один раз при создании ViewModel: иначе после возврата из редактора список бы не обновился
     * (ViewModel экрана "Проекты" переживает саму навигацию в редактор и обратно).
     */
    fun refreshProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            getProjects().collect { projects ->
                _uiState.value = _uiState.value.copy(projects = projects, isLoading = false)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /** У каждого нового проекта — свой уникальный id; под ним редактор сохранит первую правку. */
    fun onCreateProjectClick() {
        viewModelScope.launch {
            _navigationEvents.send(ProjectListNavigationEvent.OpenEditor(projectId = UUID.randomUUID().toString()))
        }
    }

    fun onProjectClick(project: Project) {
        viewModelScope.launch {
            _navigationEvents.send(ProjectListNavigationEvent.OpenEditor(projectId = project.id))
        }
    }

    /** Открывает диалог подтверждения — само удаление происходит только в onDeleteProjectConfirmed. */
    fun onDeleteProjectClick(project: Project) {
        _uiState.value = _uiState.value.copy(pendingDeleteProject = project)
    }

    fun onDeleteProjectDismiss() {
        _uiState.value = _uiState.value.copy(pendingDeleteProject = null)
    }

    fun onDeleteProjectConfirmed() {
        val project = _uiState.value.pendingDeleteProject ?: return
        viewModelScope.launch {
            deleteProject(project.id)
            _uiState.value = _uiState.value.copy(pendingDeleteProject = null)
            refreshProjects()
        }
    }
}
