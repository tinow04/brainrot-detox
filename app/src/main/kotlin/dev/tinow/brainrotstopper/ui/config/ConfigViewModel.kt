package dev.tinow.brainrotstopper.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tinow.brainrotstopper.data.media.MediaConfig as MediaServerConfig
import dev.tinow.brainrotstopper.data.media.MediaHealth
import dev.tinow.brainrotstopper.data.store.SettingsRepository
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.CardSource
import dev.tinow.brainrotstopper.domain.anki.DeckInfo
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.lock.quiz.QuizEngine
import dev.tinow.brainrotstopper.ui.Strings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What AnkiDroid answered for the deck list. */
sealed interface DeckLoad {
    data object Loading : DeckLoad
    data class Ready(val decks: List<DeckInfo>) : DeckLoad
    data class Failed(val error: AnkiError) : DeckLoad
}

/** The `bilder` text fields as typed (saved with a short delay) and the result of the last `$ ./test`. */
data class MediaForm(val url: String = "", val token: String = "", val loaded: Boolean = false, val check: MediaCheck? = null)

/**
 * B2 state. Stepper, segments and deck radio write `settings.json` at once (a running session keeps its frozen values,
 * architecture.md 4). The deck list comes from AnkiDroid on every resume of the tab (main app only, never from the lock
 * screen). URL and token are saved 400 ms after the last keystroke; the token never reaches a log.
 */
class ConfigViewModel(
    private val settings: SettingsRepository,
    private val source: () -> CardSource,
    private val mediaCheck: suspend (MediaServerConfig) -> MediaHealth,
    private val cleartextHosts: Set<String> = emptySet(),
    private val log: (String) -> Unit = {},
) : ViewModel() {
    private val decks = MutableStateFlow<DeckLoad>(DeckLoad.Loading)
    private val media = MutableStateFlow(MediaForm())
    private var deckJob: Job? = null
    private var saveJob: Job? = null
    private var testJob: Job? = null

    val ui: StateFlow<ConfigState?> = combine(settings.flow, decks, media) { s, d, m -> state(s, d, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val s = settings.flow.first()
            media.value = MediaForm(s.mediaBaseUrl, s.mediaToken, loaded = true)
        }
    }

    fun refresh() {
        if (deckJob?.isActive == true) return
        deckJob = viewModelScope.launch {
            val r = source().listDecks()
            decks.value = when (r) {
                is AnkiResult.Ok -> DeckLoad.Ready(r.value)
                is AnkiResult.Err -> DeckLoad.Failed(r.error)
            }
            log("decks " + if (r is AnkiResult.Ok) "ok ${r.value.size}" else "error ${(r as AnkiResult.Err).error.javaClass.simpleName}")
        }
    }

    fun setRequired(value: Int) = save("required") { it.copy(requiredCorrect = ConfigModel.required(value)) }

    fun setDuration(index: Int) = save("duration") { it.copy(unlockMinutes = ConfigModel.minutesAt(index)) }

    fun setDeck(index: Int) {
        val d = decks.value as? DeckLoad.Ready ?: return
        val item = ConfigModel.deckItems(d.decks).getOrNull(index) ?: return
        save("deck") { it.copy(deck = ConfigModel.selectionOf(item)) }
    }

    fun setMediaUrl(url: String) = editMedia { it.copy(url = url) }

    fun setMediaToken(token: String) = editMedia { it.copy(token = token) }

    fun testMedia() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            saveJob?.join()
            val m = media.value
            val config = MediaServerConfig.parse(m.url, m.token, cleartextHosts)
            if (config == null) {
                media.value = m.copy(check = if (m.url.isBlank() || m.token.isBlank()) null else MediaCheck.Invalid)
                log("media test invalid")
                return@launch
            }
            media.value = m.copy(check = MediaCheck.Checking)
            val result = when (mediaCheck(config)) {
                MediaHealth.Ok -> MediaCheck.Ok
                MediaHealth.TokenRejected -> MediaCheck.TokenRejected
                MediaHealth.Unreachable -> MediaCheck.Failed
            }
            media.value = media.value.copy(check = result)
            log("media test ${result.name.lowercase()}")
        }
    }

    private fun editMedia(change: (MediaForm) -> MediaForm) {
        if (!media.value.loaded) return
        testJob?.cancel()
        media.value = change(media.value).copy(check = null)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            val m = media.value
            settings.update { it.copy(mediaBaseUrl = m.url.trim(), mediaToken = m.token.trim()) }
            log("media saved url=${if (m.url.isBlank()) "empty" else "set"} token=${if (m.token.isBlank()) "empty" else "set"}")
        }
    }

    fun setAnkiSync(on: Boolean) = save("sync") { it.copy(ankiSyncAfterUnlock = on) }

    private fun save(what: String, change: (Settings) -> Settings) {
        viewModelScope.launch {
            val s = settings.update(change)
            log("set $what required=${s.requiredCorrect} minutes=${s.unlockMinutes} deck=${deckLog(s.deck)} sync=${s.ankiSyncAfterUnlock}")
        }
    }

    private fun deckLog(d: DeckSelection) = when (d) {
        DeckSelection.All -> "all"
        is DeckSelection.Single -> d.deckId.toString()
    }

    private fun state(s: Settings, d: DeckLoad, m: MediaForm): ConfigState {
        val items = (d as? DeckLoad.Ready)?.let { ConfigModel.deckItems(it.decks) }.orEmpty()
        val index = ConfigModel.deckIndex(items, s.deck)
        val missing = (s.deck as? DeckSelection.Single)?.takeIf { d is DeckLoad.Ready && index < 0 }?.nameSnapshot
        val check = when {
            m.check != null -> m.check
            m.url.isBlank() || m.token.isBlank() -> MediaCheck.Off
            else -> MediaCheck.Untested
        }
        return ConfigState(
            required = s.requiredCorrect,
            durations = Constants.DURATIONS_MIN,
            durationIndex = ConfigModel.durationIndex(s.unlockMinutes),
            decks = items,
            deckIndex = index,
            ankiConnected = d is DeckLoad.Ready,
            media = MediaConfig(m.url, m.token, check),
            ankiReason = (d as? DeckLoad.Failed)?.let { QuizEngine.errorTexts(it.error).title.substringAfter("] ") },
            missingDeck = missing,
            hints = listOf(Strings.CONFIG_HINT_NEXT, Strings.CONFIG_HINT_DECK),
            ankiSync = s.ankiSyncAfterUnlock,
        )
    }

    private companion object {
        const val SAVE_DELAY_MS = 400L
    }
}
