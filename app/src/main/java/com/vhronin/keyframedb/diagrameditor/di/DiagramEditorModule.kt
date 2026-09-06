package com.vhronin.keyframedb.diagrameditor.di

import com.vhronin.keyframedb.diagrameditor.data.repository.FakeDiagramRepository
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramRepository
import com.vhronin.keyframedb.diagrameditor.domain.usecase.GetDiagramUseCase
import com.vhronin.keyframedb.diagrameditor.ui.DiagramEditorViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Когда появится реальная реализация репозитория (JSON-хранилище),
 * достаточно заменить FakeDiagramRepository здесь — domain и presentation
 * останутся без изменений.
 */
val diagramEditorModule = module {
    single<DiagramRepository> { FakeDiagramRepository() }
    factory { GetDiagramUseCase(get()) }
    viewModel { (projectId: String) -> DiagramEditorViewModel(projectId, get()) }
}
