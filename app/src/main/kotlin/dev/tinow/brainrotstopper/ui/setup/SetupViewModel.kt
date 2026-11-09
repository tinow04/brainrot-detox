package dev.tinow.brainrotstopper.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tinow.brainrotstopper.domain.anki.CardSource
import dev.tinow.brainrotstopper.domain.permission.AnkiProbe
import dev.tinow.brainrotstopper.domain.permission.Protection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the provider check of setup step 3 (architecture.md 6) for the main app. The check talks to AnkiDroid, so it
 * only runs from the main app (on resume, after steps 1-2 turn ok, or on `$ ./erneut`), never from the lock screen's intro.
 * While a new check runs the previous result stays visible, so the row does not flicker on every resume.
 */
class SetupViewModel(private val source: () -> CardSource) : ViewModel() {
    private val _probe = MutableStateFlow<AnkiProbe>(AnkiProbe.Unknown)
    val probe: StateFlow<AnkiProbe> = _probe.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch { _probe.value = Protection.probe(source()) }
    }

    /** An earlier step is open again: forget the result, it says nothing about the new situation. */
    fun clear() {
        job?.cancel()
        job = null
        _probe.value = AnkiProbe.Unknown
    }
}
