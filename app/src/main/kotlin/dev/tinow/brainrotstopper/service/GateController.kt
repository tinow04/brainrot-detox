package dev.tinow.brainrotstopper.service

import dev.tinow.brainrotstopper.domain.gate.GateDiag
import dev.tinow.brainrotstopper.domain.gate.GateEffect
import dev.tinow.brainrotstopper.domain.gate.GateEnv
import dev.tinow.brainrotstopper.domain.gate.GateInput
import dev.tinow.brainrotstopper.domain.gate.GateModel
import dev.tinow.brainrotstopper.domain.gate.GateSnapshot
import dev.tinow.brainrotstopper.domain.gate.GateState
import dev.tinow.brainrotstopper.domain.gate.GateStateMachine
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.quiz.Constants
import dev.tinow.brainrotstopper.domain.time.TimeSource
import dev.tinow.brainrotstopper.hooks.DebugFlags
import dev.tinow.brainrotstopper.hooks.NoDebugFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shell around the pure [GateStateMachine] (architecture.md 2). It has no Android types, so JVM tests drive it in
 * virtual time; the service passes the real launcher, stores and clock.
 *
 * Everything that can change the decision becomes a command in ONE unlimited channel that a single coroutine works off
 * in order: window events, the re-evaluate timer, screen on, store changes and lock UI reports. Nothing here blocks the
 * accessibility callback. Until the stores delivered their first values the loop waits (commands stay queued), so the
 * gate never decides on a half-empty snapshot.
 *
 * The channel is unlimited instead of conflated (as architecture.md first said): a conflated channel could drop the
 * event of a blocked app when an ignored overlay event follows within the same millisecond.
 */
class GateController(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val settings: Flow<Settings>,
    private val runtime: Flow<Runtime>,
    private val protectedProvider: ProtectedSetProvider,
    private val launcher: LockLauncher,
    private val diag: GateDiagSink,
    private val tracker: ForegroundTracker,
    private val flags: () -> DebugFlags = { NoDebugFlags },
    private val log: (String) -> Unit = {},
) : LockUiBeacon.Listener {

    private sealed interface Cmd {
        data class Event(val pkg: String, val cls: String?) : Cmd
        data object Timer : Cmd
        data object ScreenOn : Cmd
        data object SettingsChanged : Cmd
        data object Connected : Cmd
        data class LockResumed(val pkg: String) : Cmd
        data object LockGone : Cmd
    }

    private val commands = Channel<Cmd>(Channel.UNLIMITED)
    private val snapshot = MutableStateFlow<GateSnapshot?>(null)
    private val protectedSets = MutableStateFlow<ProtectedSets?>(null)

    private var model = GateModel()
    private var timerJob: Job? = null

    @Volatile
    private var lockVisiblePkg: String? = null

    @Volatile
    private var goneJob: Job? = null

    /** Last decision state, readable for diagnostics and tests. */
    val state: GateState get() = model.state

    fun start() {
        LockUiBeacon.listener = this
        scope.launch {
            combine(settings, runtime, protectedSets.filterNotNull()) { s, r, p ->
                GateSnapshot(blockedPackages = s.blockedPackages, grants = r.grants, dynamicProtected = p.protectedPackages)
            }.distinctUntilChanged().collect { next ->
                val hadSnapshot = snapshot.value != null
                snapshot.value = next
                if (hadSnapshot) commands.trySend(Cmd.SettingsChanged)
            }
        }
        scope.launch {
            while (true) {
                refreshProtected()
                delay(Constants.PROTECTED_REFRESH_MS)
            }
        }
        scope.launch {
            for (cmd in commands) {
                try {
                    handle(cmd)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // Catch-all: a bug in one step must not end the loop (and with it the protection).
                    log("handler error ${t::class.java.simpleName}: ${t.message}")
                    runCatching { diag.handlerError() }
                }
            }
        }
    }

    fun stop() {
        if (LockUiBeacon.listener === this) LockUiBeacon.listener = null
        commands.close()
    }

    // --- inputs, safe to call from any thread, never block ---------------------------------------------------------

    fun onAccessibilityEvent(pkg: String, cls: String?) {
        commands.trySend(Cmd.Event(pkg, cls))
    }

    fun onConnected() {
        commands.trySend(Cmd.Connected)
    }

    fun onScreenOn() {
        commands.trySend(Cmd.ScreenOn)
        scope.launch { refreshProtected() }
    }

    /** Debug (EVENT_FLOOD): [n] synthetic events for [pkg]. */
    fun inject(pkg: String, n: Int) {
        repeat(n) { commands.trySend(Cmd.Event(pkg, null)) }
    }

    override fun onLockResumed(pkg: String) {
        goneJob?.cancel()
        lockVisiblePkg = pkg
        tracker.lockActive = true
        commands.trySend(Cmd.LockResumed(pkg))
    }

    override fun onLockGone() {
        lockVisiblePkg = null
        goneJob?.cancel()
        // The next foreground event usually explains where the user went; only look again if none comes.
        goneJob = scope.launch {
            delay(Constants.LOCK_GONE_GRACE_MS)
            commands.trySend(Cmd.LockGone)
        }
    }

    /** Re-reads home launcher, input methods and dialer; call after package changes. A change re-evaluates the gate. */
    suspend fun refreshProtected() {
        val loaded = try {
            withTimeoutOrNull(LOAD_TIMEOUT_MS) { protectedProvider.load() }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            log("protected sets failed: ${t.message}")
            null
        }
        // On failure keep the old sets; the static protection always applies and the gate must not wait forever.
        val sets = loaded ?: protectedSets.value ?: ProtectedSets()
        tracker.ignoredDynamic = sets.inputMethods
        if (sets != protectedSets.value) log("protected dynamic=${sets.protectedPackages.sorted()} ime=${sets.inputMethods.sorted()}")
        protectedSets.value = sets
    }

    // --- loop -------------------------------------------------------------------------------------------------------

    private suspend fun handle(cmd: Cmd) {
        val snap = snapshot.filterNotNull().first()
        val time = timeSource.now()
        val input: GateInput = when (cmd) {
            is Cmd.Event -> {
                if (flags().throwInHandler) throw IllegalStateException("debug THROW_IN_HANDLER")
                goneJob?.cancel()
                when (val r = tracker.onEvent(cmd.pkg, cmd.cls)) {
                    ForegroundTracker.Result.Ignored -> return
                    is ForegroundTracker.Result.Foreground -> {
                        log("fg pkg=${r.pkg}")
                        GateInput.Event(r.pkg, cmd.cls)
                    }
                }
            }
            Cmd.Timer -> GateInput.Timer
            Cmd.ScreenOn -> GateInput.ScreenOn
            Cmd.SettingsChanged -> GateInput.SettingsChanged
            Cmd.Connected -> GateInput.Connected
            is Cmd.LockResumed -> GateInput.LockVisibleChanged(cmd.pkg)
            Cmd.LockGone -> {
                if (lockVisiblePkg != null) return // a new lock instance came up in the meantime
                tracker.lockActive = false
                GateInput.LockVisibleChanged(null)
            }
        }
        val env = GateEnv(time.elapsedMs, tracker.current, snap, time, lockVisiblePkg)
        val (next, effects) = GateStateMachine.step(model, input, env)
        model = next
        tracker.lockActive = lockVisiblePkg != null || next.state is GateState.LockRequested || next.state is GateState.LockVisible
        for (effect in effects) run(effect)
    }

    private fun run(effect: GateEffect) {
        when (effect) {
            is GateEffect.StartLock -> {
                log("lock pkg=${effect.pkg} reason=${if (effect.retry) "retry" else "blocked"}")
                if (flags().simulateLaunchBlock) log("lock start suppressed (debug SIMULATE_LAUNCH_BLOCK)") else launcher.start(effect.pkg, effect.retry)
            }
            GateEffect.GoHome -> {
                log("home")
                launcher.goHome()
            }
            is GateEffect.ScheduleReeval -> schedule(effect.atElapsedMs)
            GateEffect.CancelTimers -> {
                timerJob?.cancel()
                timerJob = null
            }
            is GateEffect.Diag -> {
                log("diag ${effect.kind}")
                when (effect.kind) {
                    GateDiag.Breaker -> diag.breaker()
                    GateDiag.LaunchFailed -> diag.launchFailed()
                }
            }
        }
    }

    /** One timer at a time; a later schedule replaces the earlier one (architecture.md 3A). */
    private fun schedule(atElapsedMs: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            val wait = atElapsedMs - timeSource.now().elapsedMs
            if (wait > 0) delay(wait)
            commands.trySend(Cmd.Timer)
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
