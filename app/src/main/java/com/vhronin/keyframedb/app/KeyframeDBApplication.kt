package com.vhronin.keyframedb.app

import android.app.Application
import com.vhronin.keyframedb.diagrameditor.di.diagramEditorModule
import com.vhronin.keyframedb.projectlist.di.projectListModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KeyframeDBApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KeyframeDBApplication)
            modules(projectListModule, diagramEditorModule)
        }
    }
}