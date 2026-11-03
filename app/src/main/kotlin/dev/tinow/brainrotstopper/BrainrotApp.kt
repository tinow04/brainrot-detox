package dev.tinow.brainrotstopper

import android.app.Application
import dev.tinow.brainrotstopper.debug.DebugInit
import dev.tinow.brainrotstopper.di.AppContainer

class BrainrotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DebugInit.install(container)
    }
}
