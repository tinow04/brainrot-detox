package dev.tinow.brainrotstopper.lock.quiz

import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import dev.tinow.brainrotstopper.BrainrotApp
import dev.tinow.brainrotstopper.data.system.AppLog
import dev.tinow.brainrotstopper.domain.anki.AnkiCapabilities

/**
 * Keeps the [QuizEngine] across configuration changes of the lock screen (dark mode, font size). The engine works in the
 * app scope, so a pipeline step that is running when the screen closes still finishes.
 */
class QuizViewModel(app: BrainrotApp) : ViewModel() {
    val engine: QuizEngine

    init {
        val c = app.container
        val pm = app.packageManager
        engine = QuizEngine(
            scope = c.ioScope,
            source = c.cardSource(),
            runtime = c.runtime,
            settings = { c.settings.current() },
            stats = { c.stats.current() },
            time = c.timeSource,
            capabilities = AnkiCapabilities(),
            images = { names -> c.mediaImages.load(names) },
            labelOf = { pkg ->
                try {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    pkg.substringAfterLast('.')
                }
            },
            flags = c.debugFlags,
            log = { AppLog.i("BS/Quiz", it) },
            onUnlockLeft = { c.ankiSync.request() },
        )
    }

    override fun onCleared() = engine.release()
}
