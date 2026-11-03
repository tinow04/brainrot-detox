package dev.tinow.brainrotstopper.di

import android.app.Application
import dev.tinow.brainrotstopper.hooks.DebugFlags
import dev.tinow.brainrotstopper.hooks.NoDebugFlags

/** Manual dependency container, one per process. Grows with the milestones (stores, engine, gateways). */
class AppContainer(val app: Application) {
    @Volatile
    var debugFlags: DebugFlags = NoDebugFlags
}
