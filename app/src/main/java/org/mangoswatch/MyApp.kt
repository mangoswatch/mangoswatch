package org.mangoswatch

import android.app.Application
import org.mangoswatch.presentation.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyApp)   // <-- make context available in appModule
            modules(appModule)
        }
    }
}