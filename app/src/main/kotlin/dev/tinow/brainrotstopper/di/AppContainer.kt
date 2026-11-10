package dev.tinow.brainrotstopper.di

import android.app.Application
import androidx.datastore.dataStoreFile
import android.content.pm.ApplicationInfo
import dev.tinow.brainrotstopper.data.anki.AndroidProviderGateway
import dev.tinow.brainrotstopper.data.anki.AnkiContentProviderSource
import dev.tinow.brainrotstopper.data.anki.DelayingGateway
import dev.tinow.brainrotstopper.data.media.MediaImages
import dev.tinow.brainrotstopper.domain.anki.AnkiCapabilities
import dev.tinow.brainrotstopper.domain.anki.CardSource
import dev.tinow.brainrotstopper.data.store.RuntimeRepository
import dev.tinow.brainrotstopper.data.store.SettingsRepository
import dev.tinow.brainrotstopper.data.store.StatsRepository
import dev.tinow.brainrotstopper.data.system.AppLog
import dev.tinow.brainrotstopper.data.system.SystemTimeSource
import dev.tinow.brainrotstopper.domain.time.TimeSource
import dev.tinow.brainrotstopper.hooks.DebugFlags
import dev.tinow.brainrotstopper.hooks.NoDebugFlags
import dev.tinow.brainrotstopper.service.AnkiSyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manual dependency container, one per process. DataStore allows one instance per file per process, so the repositories
 * are created once here (files in `files/datastore/`). Grows with the milestones (engine, gateways).
 */
class AppContainer(val app: Application) {
    @Volatile
    var debugFlags: DebugFlags = NoDebugFlags

    /** The running gate controller (set by the accessibility service while it is bound); the debug hooks use it. */
    @Volatile
    var gate: dev.tinow.brainrotstopper.service.GateController? = null

    /** Scope of all persistence work; a failing child never cancels its siblings. */
    val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val timeSource: TimeSource by lazy { SystemTimeSource(app.contentResolver) { debugFlags.wallOffsetMs } }

    val stats: StatsRepository by lazy { StatsRepository(app.dataStoreFile("stats.json"), ioScope) }

    val runtime: RuntimeRepository by lazy {
        RuntimeRepository(app.dataStoreFile("runtime.json"), ioScope, stats) { AppLog.w("BS/Store", "stats write failed: ${it.javaClass.simpleName}") }
    }

    /** Optional AnkiDroid sync after a grant (M6-T7), sent through the running gate. */
    val ankiSync: AnkiSyncTrigger by lazy {
        AnkiSyncTrigger(ioScope, { settings.current() }, runtime, timeSource, { gate }, log = { AppLog.i("BS/Sync", it) })
    }

    val settings: SettingsRepository by lazy {
        SettingsRepository(
            file = app.dataStoreFile("settings.json"),
            mirror = File(app.filesDir, "settings.mirror.json"),
            scope = ioScope,
            onCorrupted = { ioScope.launch { runtime.markSettingsCorrupted() } },
        )
    }

    /** Card images from the own media server (architecture.md 5a). Cleartext http only for the emulator host in debuggable builds. */
    val mediaImages: MediaImages by lazy {
        val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        MediaImages(
            settings = settings,
            cacheDir = File(app.cacheDir, "media"),
            clock = System::currentTimeMillis,
            cleartextHosts = if (debuggable) setOf("10.0.2.2") else emptySet(),
            log = { AppLog.i("BS/Media", it) },
        )
    }

    /** Debug builds install a factory for scripted card sources here (scenario name to source); release leaves it null. */
    @Volatile
    var fakeCardSource: ((String) -> CardSource)? = null

    private val realCardSource: CardSource by lazy {
        AnkiContentProviderSource(DelayingGateway(AndroidProviderGateway(app)) { debugFlags.gatewayDelayMs(it) }, AnkiCapabilities(), log = { AppLog.w("BS/Anki", it) })
    }

    /** The card source for a new lock screen; read per lock so that `SET_SOURCE` takes effect without a restart. */
    fun cardSource(): CardSource {
        val flags = debugFlags
        if (flags.cardSource == "fake") fakeCardSource?.let { return it(flags.scenario) }
        return realCardSource
    }
}
