package com.vhronin.keyframedb.diagrameditor.di

import com.vhronin.keyframedb.diagrameditor.data.repository.JsonDiagramRepository
import com.vhronin.keyframedb.diagrameditor.domain.repository.DiagramRepository
import com.vhronin.keyframedb.diagrameditor.domain.usecase.GetDiagramUseCase
import com.vhronin.keyframedb.diagrameditor.domain.usecase.SaveDiagramUseCase
import com.vhronin.keyframedb.diagrameditor.ui.DiagramEditorViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val diagramEditorModule = module {
    single<DiagramRepository> { JsonDiagramRepository(androidContext()) }
    factory { GetDiagramUseCase(get()) }
    factory { SaveDiagramUseCase(get()) }
    viewModel { (projectId: String) -> DiagramEditorViewModel(projectId, get(), get()) }
}
