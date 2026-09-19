package com.vhronin.keyframedb.projectlist.di

import com.vhronin.keyframedb.projectlist.data.repository.JsonProjectRepository
import com.vhronin.keyframedb.projectlist.domain.repository.ProjectRepository
import com.vhronin.keyframedb.projectlist.domain.usecase.GetProjectsUseCase
import com.vhronin.keyframedb.projectlist.ui.ProjectListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val projectListModule = module {
    single<ProjectRepository> { JsonProjectRepository(androidContext()) }
    factory { GetProjectsUseCase(get()) }
    viewModel { ProjectListViewModel(get()) }
}