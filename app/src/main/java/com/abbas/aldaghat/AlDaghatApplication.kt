package com.abbas.aldaghat

import android.app.Application
import com.abbas.aldaghat.di.AppContainer

class AlDaghatApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bootstrap()
    }
}
