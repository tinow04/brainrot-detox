package dev.tinow.brainrotstopper.lock.quiz

import dev.tinow.brainrotstopper.data.anki.CardHtml
import dev.tinow.brainrotstopper.data.anki.CardSide
import dev.tinow.brainrotstopper.domain.anki.AnkiCapabilities
import dev.tinow.brainrotstopper.domain.anki.AnkiError
import dev.tinow.brainrotstopper.domain.anki.AnkiResult
import dev.tinow.brainrotstopper.domain.anki.CardSource
import dev.tinow.brainrotstopper.domain.anki.DeckCounts
import dev.tinow.brainrotstopper.domain.anki.NextCard
import dev.tinow.brainrotstopper.domain.anki.RenderedCard
import dev.tinow.brainrotstopper.domain.gate.GateSnapshot
import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Ease
import dev.tinow.brainrotstopper.domain.model.EmergencyState
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.PendingAnswer
import dev.tinow.brainrotstopper.domain.model.QuizSession
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.quiz.Phase
import dev.tinow.brainrotstopper.domain.quiz.QueueDecision
import dev.tinow.brainrotstopper.domain.quiz.QueuePolicy
import dev.tinow.brainrotstopper.domain.quiz.QuizCtx
import dev.tinow.brainrotstopper.domain.quiz.QuizEffect
import dev.tinow.brainrotstopper.domain.quiz.QuizEvent
import dev.tinow.brainrotstopper.domain.quiz.QuizReducer
import dev.tinow.brainrotstopper.domain.quiz.QuizState
import dev.tinow.brainrotstopper.domain.runtime.EmergencyButton
import dev.tinow.brainrotstopper.domain.runtime.EmergencyPolicy
import dev.tinow.brainrotstopper.domain.runtime.RuntimeOps
import dev.tinow.brainrotstopper.domain.runtime.RuntimeStore
import dev.tinow.brainrotstopper.domain.stats.StreakCalculator
import dev.tinow.brainrotstopper.domain.time.TimeSource
import dev.tinow.brainrotstopper.hooks.DebugFlags
import dev.tinow.brainrotstopper.hooks.NoDebugFlags
import dev.tinow.brainrotstopper.lock.IntroStateFactory
import dev.tinow.brainrotstopper.lock.LockEntry
import dev.tinow.brainrotstopper.lock.screens.IntroState
import dev.tinow.brainrotstopper.ui.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** A card as the UI needs it: the raw sides, the four interval labels and the inlined images (names to data URIs). */
data class CardContent(val rendered: RenderedCard, val intervals: List<String>?, val images: Map<String, String>) {
    val ref: CardRef get() = rendered.ref
}

/** Numbers for the unlock screen (A5). */
data class UnlockInfo(val minutes: Int, val answered: Int, val correct: Int, val durationMs: Long, val streakDays: Int)

/** Everything the lock UI draws: the reducer state plus what the effects fetched. */
data class QuizView(
    val quiz: QuizState = QuizState(),
    val appLabel: String = "",
    val deckLabel: String = "",
    val intro: IntroState? = null,
    val card: CardContent? = null,
    val unlock: UnlockInfo? = null,
)

/** One-off requests to the activity. */
sealed interface QuizCommand {
    /** Press HOME and finish the lock task. */
    data object GoHome : QuizCommand

    /** Start the target app and finish. */
    data class StartTarget(val pkg: String) : QuizCommand

    /** The lock is not needed (any more): just finish. */
    data object Finish : QuizCommand
}

/**
 * Runs the quiz (architecture.md 3B, 4): feeds events into the pure [QuizReducer] and executes its effects against the
 * [CardSource] and `runtime.json`. The answer pipeline is write-ahead and idempotent: pending is stored before the
 * answer goes to AnkiDroid, a kill at any step is repaired by [QuizEffect.RecoverPending] on the next attach, and the
 * counters change only in the single atomic commit.
 *
 * Nothing here touches AnkiDroid before [start]: the intro is built from stored state only (architecture.md 5).
 */
class QuizEngine(
    private val scope: CoroutineScope,
    private val source: CardSource,
    private val runtime: RuntimeStore,
    private val settings: suspend () -> Settings,
    private val stats: suspend () -> Stats,
    private val time: TimeSource,
    private val capabilities: AnkiCapabilities = AnkiCapabilities(),
    /** Image names to data URIs; must not throw (missing images simply stay placeholders). */
    private val images: suspend (List<String>) -> Map<String, String> = { emptyMap() },
    private val labelOf: (String) -> String = { it },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val flags: DebugFlags = NoDebugFlags,
    private val ctx: QuizCtx = QuizCtx(),
    private val log: (String) -> Unit = {},
) {
    private val monitor = Any()
    private var state = QuizState()
    private var meta = QuizView()
    private val _view = MutableStateFlow(QuizView())
    val view: StateFlow<QuizView> = _view

    private val _commands = Channel<QuizCommand>(Channel.UNLIMITED)
    val commands: Flow<QuizCommand> = _commands.receiveAsFlow()

    private var attachJob: Job? = null
    private var loadJob: Job? = null
    private var pollJob: Job? = null

    @Volatile
    private var visible = true

    /** Last use of the emergency exit as stored in `runtime.json`; read at attach and after each emergency commit. */
    @Volatile
    private var emergencyState: EmergencyState? = null
    private var pollWaitingSeq: Int? = null

    /** Target of the last attach (for [setVisible]); null before the first attach. */
    @Volatile
    private var attachedPkg: String? = null

    // ------------------------------------------------------------------ UI API

    /** The lock came up for [pkg]: resume check, episode and session bookkeeping, then the intro (stored state only). */
    fun attach(pkg: String) {
        attachJob?.cancel()
        attachJob = scope.launch { guarded("attach") { doAttach(pkg) } }
    }

    fun start() = dispatch(QuizEvent.Start(now()))
    fun reveal() = dispatch(QuizEvent.Reveal(now()))
    fun rate(ease: Ease) = dispatch(QuizEvent.Rate(ease, now()))
    fun next() = dispatch(QuizEvent.Continue)
    fun cancel() = dispatch(QuizEvent.Cancel)
    fun cancelTap() = dispatch(QuizEvent.CancelTap(now()))
    fun openTarget() = dispatch(QuizEvent.OpenTarget)
    fun retry() = dispatch(QuizEvent.Retry(now()))
    fun emergency() = dispatch(QuizEvent.Emergency(now()))

    /** The emergency button of the error screen right now (the screen polls it every second). */
    fun emergencyButton(): EmergencyButton {
        val since = synchronized(monitor) { state.errorSince.takeIf { state.phase == Phase.Error } } ?: return EmergencyButton.Hidden
        return EmergencyPolicy.button(since, emergencyState, time.now(), ctx.emergencyEnabled)
    }

    /**
     * The lock screen went to the background: the waiting screen stops polling AnkiDroid until it is back, and the open
     * episode remembers when the lock was last seen (statistics debounce, RuntimeOps.leave).
     */
    fun setVisible(value: Boolean) {
        visible = value
        if (!value) {
            attachedPkg?.let { pkg -> scope.launch { runCatching { val t = time.now(); runtime.apply(t) { RuntimeOps.leave(it, pkg, t) } } } }
            return
        }
        val seq = synchronized(monitor) { pollWaitingSeq.also { pollWaitingSeq = null } } ?: return
        dispatch(QuizEvent.PollDue(seq))
    }

    /** The screen is gone: stop what only serves the screen. Answer pipelines run on (they are crash-safe anyway). */
    fun release() {
        attachJob?.cancel(); loadJob?.cancel(); pollJob?.cancel()
    }

    val phase get() = synchronized(monitor) { state.phase }

    // ------------------------------------------------------------------ reducer loop

    private fun now() = time.now().elapsedMs

    internal fun dispatch(event: QuizEvent) {
        val effects = synchronized(monitor) {
            val (next, effects) = QuizReducer.reduce(state, event, ctx)
            // Phase changes for scripts and diagnosis (no card content): `phase Question 2/5 answered=3`, `phase Error ... error=Unreachable`.
            if (next.phase != state.phase || next.error != state.error || event is QuizEvent.Attach) {
                val error = next.error?.let { " error=${it.javaClass.simpleName}" }.orEmpty()
                log("phase ${next.phase} ${next.correct}/${next.required} answered=${next.answered}$error")
            }
            state = next
            publish()
            effects
        }
        for (effect in effects) execute(effect)
    }

    /** Must be called inside [monitor]. */
    private fun publish() {
        val card = meta.card?.takeIf { it.ref == state.card }
        _view.value = meta.copy(quiz = state, card = card)
    }

    private inline fun editMeta(block: (QuizView) -> QuizView) = synchronized(monitor) {
        meta = block(meta)
        publish()
    }

    private suspend inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Fail-closed: the lock stays (blank background, back goes HOME); never crash the process from here.
            log("quiz $what failed: ${e.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------------ attach

    private suspend fun doAttach(pkg: String) {
        attachedPkg = pkg
        val cfg = settings()
        val rt = runtime.current()
        emergencyState = rt.emergency
        val t = time.now()
        when (LockEntry.decide(pkg, GateSnapshot(cfg.blockedPackages, rt.grants), t)) {
            LockEntry.Dismiss -> _commands.trySend(QuizCommand.Finish)
            LockEntry.OpenTarget -> _commands.trySend(QuizCommand.StartTarget(pkg))
            LockEntry.Show -> {
                val info = runtime.apply(t) { RuntimeOps.attach(it, pkg, cfg, t, flags.sessionTtlMsOverride ?: Constants.SESSION_TTL_MS, newId) }
                val session = runtime.current().session
                if (session == null) {
                    _commands.trySend(QuizCommand.Finish)
                    return
                }
                val intro = IntroStateFactory.build(labelOf(pkg), session, stats(), t)
                editMeta { QuizView(appLabel = intro.app, deckLabel = intro.deckLabel, intro = intro) }
                dispatch(
                    QuizEvent.Attach(
                        target = pkg, required = session.required, correct = session.correct, answered = session.answered,
                        grantKind = info.grantKind, hasPending = session.pending != null, now = t.elapsedMs,
                    ),
                )
            }
        }
    }

    // ------------------------------------------------------------------ effects

    private fun execute(effect: QuizEffect) {
        when (effect) {
            is QuizEffect.LoadNext -> {
                loadJob?.cancel()
                loadJob = scope.launch { loadNext(effect.seq) }
            }
            is QuizEffect.SubmitAnswer -> scope.launch { pipeline(effect.seq) { submit(effect) } }
            is QuizEffect.RecoverPending -> scope.launch { pipeline(effect.seq) { recover(effect.seq) } }
            is QuizEffect.CommitGrant -> scope.launch { commitGrant(effect) }
            is QuizEffect.SchedulePoll -> {
                pollJob?.cancel()
                pollJob = scope.launch {
                    delay(effect.delayMs)
                    if (visible) dispatch(QuizEvent.PollDue(effect.seq)) else synchronized(monitor) { pollWaitingSeq = effect.seq }
                }
            }
            QuizEffect.DiscardPending -> scope.launch { runCatching { runtime.update { RuntimeOps.discardPending(it) } } }
            QuizEffect.CancelEpisode -> scope.launch { runCatching { runtime.apply(time.now()) { RuntimeOps.cancel(it) } } }
            QuizEffect.GoHomeAndFinish -> _commands.trySend(QuizCommand.GoHome)
            is QuizEffect.StartTarget -> _commands.trySend(QuizCommand.StartTarget(effect.pkg))
        }
    }

    // -- loading the next card

    private suspend fun loadNext(seq: Int) {
        val decision = try {
            decideNext()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("quiz load failed: ${e.javaClass.simpleName}")
            QueueDecision.Error(AnkiError.Unexpected(e.javaClass.simpleName))
        }
        dispatch(QuizEvent.NextResult(seq, decision, now()))
    }

    private suspend fun decideNext(): QueueDecision {
        val session = runtime.current().session ?: return QueueDecision.Error(AnkiError.Unexpected("no session"))
        val deck: DeckSelection = session.deck
        val cursor = source.nextCard(deck)
        val refCursor: AnkiResult<CardRef?> = when (cursor) {
            is AnkiResult.Err -> cursor
            is AnkiResult.Ok -> AnkiResult.Ok(cursor.value?.ref)
        }
        // The counts are only needed when the cursor is empty (queue matrix rows 3 to 6).
        val counts: AnkiResult<DeckCounts> =
            if (cursor is AnkiResult.Ok && cursor.value == null) source.deckCounts(deck) else AnkiResult.Ok(DeckCounts(0, 0, 0))
        val decision = QueuePolicy.decide(refCursor, { counts }, session.lastWrongElapsed, now())
        if (decision !is QueueDecision.Card) return decision
        val next: NextCard = (cursor as AnkiResult.Ok).value ?: return QueueDecision.Error(AnkiError.BadData)

        val rendered = when (val r = source.render(decision.ref)) {
            is AnkiResult.Err -> return QueueDecision.Error(r.error)
            is AnkiResult.Ok -> r.value
        }
        // Rendering never blocks a card, but a card with nothing on either side is bad data (architecture.md 5).
        if (!CardHtml.hasContent(rendered)) return QueueDecision.Error(AnkiError.BadData)

        val names = (CardHtml.imageNames(rendered, CardSide.Question) + CardHtml.imageNames(rendered, CardSide.Answer)).distinct()
        val inlined = if (names.isEmpty()) emptyMap() else try {
            images(names)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyMap()
        }
        val labels = next.intervalLabels?.takeIf { it.size == 4 }
        editMeta { it.copy(card = CardContent(rendered, labels, inlined)) }
        return decision
    }

    // -- answer pipeline (architecture.md 4)

    private suspend fun pipeline(seq: Int, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("quiz answer pipeline failed: ${e.javaClass.simpleName}")
            dispatch(QuizEvent.AnswerFailed(seq, AnkiError.Unexpected(e.javaClass.simpleName), now()))
        }
    }

    private suspend fun fail(seq: Int, error: AnkiError) = dispatch(QuizEvent.AnswerFailed(seq, error, now()))

    private suspend fun submit(e: QuizEffect.SubmitAnswer) {
        var repsBefore: Int? = null
        if (capabilities.answerVerify) {
            when (val r = source.cardProgress(e.card)) {
                is AnkiResult.Err -> return fail(e.seq, r.error) // nothing stored, nothing sent yet
                is AnkiResult.Ok -> repsBefore = r.value
            }
        }
        val pending = PendingAnswer(newId(), e.card.noteId, e.card.ord, e.ease, e.takenMs, repsBefore)
        runtime.update { RuntimeOps.setPending(it, pending, time.now()) }
        send(e.seq, pending)
    }

    /** Pending is stored: send, verify (once more on a miss) and commit. */
    private suspend fun send(seq: Int, pending: PendingAnswer) {
        val ref = CardRef(pending.noteId, pending.ord)
        val sent = source.answer(ref, pending.ease, pending.takenMs)
        if (capabilities.answerVerify) {
            // Even a failed call may have been applied: look before giving up.
            var applied = applied(ref, pending)
            if (applied is AnkiResult.Ok && !applied.value) {
                if (sent is AnkiResult.Ok) {
                    log("quiz answer not visible after send, resending once")
                    val again = source.answer(ref, pending.ease, pending.takenMs)
                    if (again is AnkiResult.Err) return fail(seq, again.error)
                    applied = applied(ref, pending)
                } else {
                    return fail(seq, (sent as AnkiResult.Err).error)
                }
            }
            when (applied) {
                is AnkiResult.Err -> return fail(seq, applied.error)
                is AnkiResult.Ok -> if (!applied.value) return fail(seq, AnkiError.AnswerNotSaved)
            }
        } else if (sent is AnkiResult.Err) {
            return fail(seq, sent.error)
        }
        commit(seq)
    }

    private suspend fun applied(ref: CardRef, pending: PendingAnswer): AnkiResult<Boolean> =
        when (val r = source.cardProgress(ref)) {
            is AnkiResult.Err -> r
            is AnkiResult.Ok -> AnkiResult.Ok(r.value > (pending.repsBefore ?: 0))
        }

    /** Resume after the process died (or the screen closed) with an unfinished answer. */
    private suspend fun recover(seq: Int) {
        val session = runtime.current().session
        val pending = session?.pending
        if (session == null) return fail(seq, AnkiError.Unexpected("no session"))
        if (pending == null) return dispatch(QuizEvent.AnswerSaved(seq, session.correct, session.answered, unlocked = false))
        if (!capabilities.answerVerify) {
            // Without a way to check, resending could answer twice: drop it, count nothing (architecture.md 4).
            runtime.update { RuntimeOps.discardPending(it) }
            return dispatch(QuizEvent.AnswerSaved(seq, session.correct, session.answered, unlocked = false))
        }
        val ref = CardRef(pending.noteId, pending.ord)
        when (val a = applied(ref, pending)) {
            is AnkiResult.Err -> fail(seq, a.error)
            is AnkiResult.Ok -> if (a.value) commit(seq) else send(seq, pending)
        }
    }

    private suspend fun commit(seq: Int) {
        val before = runtime.current().session
        val pending = before?.pending
        val t = time.now()
        val override = flags.unlockSecondsOverride?.let { it * 1000L }
        val info = runtime.apply(t) { RuntimeOps.commitAnswer(it, t, override) }
        if (info.unlocked && before != null) {
            val unlock = unlockInfo(before, before.answered + 1, before.correct + 1, before.activeMs + (pending?.takenMs ?: 0), t)
            editMeta { it.copy(unlock = unlock) }
            return dispatch(QuizEvent.AnswerSaved(seq, unlock.correct, unlock.answered, unlocked = true))
        }
        val s = info.session ?: return fail(seq, AnkiError.Unexpected("no session"))
        dispatch(QuizEvent.AnswerSaved(seq, s.correct, s.answered, unlocked = false))
    }

    private suspend fun unlockInfo(s: QuizSession, answered: Int, correct: Int, durationMs: Long, t: dev.tinow.brainrotstopper.domain.time.TimeSnapshot): UnlockInfo {
        val minutes = flags.unlockSecondsOverride?.let { maxOf(1, (it + 59) / 60) } ?: s.unlockMinutes
        val streak = runCatching { StreakCalculator.streak(stats(), t.epochDay) }.getOrDefault(0)
        return UnlockInfo(minutes, answered, correct, durationMs, streak)
    }

    // -- grants without a quiz

    private suspend fun commitGrant(e: QuizEffect.CommitGrant) {
        val granted = try {
            val t = time.now()
            when (e.kind) {
                GrantKind.NOTHING_DUE -> {
                    val before = runtime.current().session
                    val override = flags.unlockSecondsOverride?.let { it * 1000L }
                    val ok = runtime.apply(t) { RuntimeOps.commitNothingDue(it, t, override) }
                    if (ok && before != null) {
                        val unlock = unlockInfo(before, before.answered, before.correct, before.activeMs, t)
                        editMeta { it.copy(unlock = unlock) }
                    }
                    ok
                }
                GrantKind.EMERGENCY -> {
                    val target = synchronized(monitor) { state.target } ?: return dispatch(QuizEvent.GrantResult(e.seq, e.kind, false, now()))
                    val minutes = Constants.EMERGENCY_GRANT_MIN
                    val ok = try {
                        runtime.apply(t) { RuntimeOps.commitEmergency(it, target, t) }
                    } finally {
                        runCatching { emergencyState = runtime.current().emergency }
                    }
                    if (ok) editMeta { it.copy(unlock = UnlockInfo(minutes, 0, 0, 0, 0)) }
                    ok
                }
                GrantKind.QUIZ -> false
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Throwable) {
            log("quiz grant failed: ${ex.javaClass.simpleName}")
            false
        }
        dispatch(QuizEvent.GrantResult(e.seq, e.kind, granted, now()))
    }

    companion object {
        /** Texts of the error screens (architecture.md 5). */
        fun errorTexts(error: AnkiError): Strings.ErrorTexts = when (error) {
            AnkiError.NotInstalledOrApiOff -> Strings.ERR_NOT_AVAILABLE
            AnkiError.PermissionMissing -> Strings.ERR_PERMISSION
            AnkiError.StorageNotConfigured -> Strings.ERR_STORAGE
            AnkiError.Unreachable -> Strings.ERR_UNREACHABLE
            AnkiError.DeckMissing -> Strings.ERR_DECK_MISSING
            AnkiError.BadData, is AnkiError.Unexpected -> Strings.ERR_BAD_DATA
            AnkiError.AnswerNotSaved -> Strings.ERR_ANSWER_NOT_SAVED
        }
    }
}
