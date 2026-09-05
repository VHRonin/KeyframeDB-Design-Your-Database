package com.vhronin.keyframedb.projectlist.di

import com.vhronin.keyframedb.projectlist.data.repository.FakeProjectRepository
import com.vhronin.keyframedb.projectlist.domain.repository.ProjectRepository
import com.vhronin.keyframedb.projectlist.domain.usecase.GetProjectsUseCase
import com.vhronin.keyframedb.projectlist.ui.ProjectListViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Когда появится реальная реализация репозитория (JSON-хранилище),
 * достаточно заменить FakeProjectRepository здесь — domain и presentation
 * останутся без изменений.
 */
val projectListModule = module {
    single<ProjectRepository> { FakeProjectRepository() }
    factory { GetProjectsUseCase(get()) }
    viewModel { ProjectListViewModel(get()) }
}