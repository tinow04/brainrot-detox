package dev.tinow.brainrotstopper.domain.model

import dev.tinow.brainrotstopper.domain.quiz.Constants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Anki answer buttons. Only GOOD and EASY count towards the unlock. */
@Serializable
enum class Ease(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    val counts: Boolean get() = value >= 3

    companion object {
        fun fromValue(value: Int): Ease? = entries.firstOrNull { it.value == value }
    }
}

@Serializable
sealed interface DeckSelection {
    @Serializable @SerialName("all")
    data object All : DeckSelection

    @Serializable @SerialName("single")
    data class Single(val deckId: Long, val nameSnapshot: String) : DeckSelection
}

/** `settings.json`. */
@Serializable
data class Settings(
    val schemaVersion: Int = 1,
    val blockedPackages: Set<String> = emptySet(),
    val requiredCorrect: Int = Constants.REQUIRED_DEFAULT,
    val unlockMinutes: Int = Constants.DEFAULT_DURATION_MIN,
    val deck: DeckSelection = DeckSelection.All,
    val onboardingDismissed: Boolean = false,
    /** Own media server (architecture.md 5a); empty = off. The token never goes into logs. */
    val mediaBaseUrl: String = "",
    val mediaToken: String = "",
) {
    /** Values outside the allowed ranges fall back to the defaults (a hand-edited file must not weaken the gate unexpectedly). */
    fun sanitized(): Settings = copy(
        requiredCorrect = requiredCorrect.coerceIn(Constants.REQUIRED_RANGE),
        unlockMinutes = if (unlockMinutes in Constants.DURATIONS_MIN) unlockMinutes else Constants.DEFAULT_DURATION_MIN,
    )
}

@Serializable
enum class GrantKind { QUIZ, NOTHING_DUE, EMERGENCY }

/** Free pass for one target package, measured on the monotonic clock only. */
@Serializable
data class UnlockGrant(
    val grantedAtElapsedMs: Long,
    val untilElapsedMs: Long,
    val durationMs: Long,
    val bootCount: Int,
    val grantedAtWallMs: Long,
    val kind: GrantKind,
)

@Serializable
data class PendingAnswer(
    val answerId: String,
    val noteId: Long,
    val ord: Int,
    val ease: Ease,
    val takenMs: Long,
    val repsBefore: Int? = null,
)

/** Quiz progress per target package; `required`, deck and duration are frozen when it is created. */
@Serializable
data class QuizSession(
    val sessionId: String,
    val targetPkg: String,
    val deck: DeckSelection,
    val required: Int,
    val unlockMinutes: Int,
    val correct: Int = 0,
    val answered: Int = 0,
    val wrong: Int = 0,
    val activeMs: Long = 0,
    val lastWrongElapsed: Long? = null,
    val startedElapsed: Long,
    val lastActiveElapsed: Long,
    val bootCount: Int,
    val startedWall: Long,
    val pending: PendingAnswer? = null,
)

/** One uninterrupted lock run of a target package (statistics counts blocks per episode). */
@Serializable
data class Episode(
    val pkg: String,
    val openedElapsed: Long,
    val lastAttachElapsed: Long,
    val bootCount: Int,
)

@Serializable
data class EmergencyState(
    val lastUsedElapsed: Long,
    val bootCount: Int,
    /** Wall clock of the use; only read after a reboot (the monotonic clock restarted), 0 = unknown. */
    val lastUsedWall: Long = 0,
)

@Serializable
data class Diag(
    val serviceLastConnectedWall: Long = 0,
    val serviceLastUnboundWall: Long = 0,
    val serviceErrorCount: Int = 0,
    val launchFailedCount: Int = 0,
    val settingsCorrupted: Boolean = false,
)

/** `runtime.json`: grants, session and episode live in one file so one `updateData` is atomic. */
@Serializable
data class Runtime(
    val schemaVersion: Int = 1,
    val grants: Map<String, UnlockGrant> = emptyMap(),
    val session: QuizSession? = null,
    val episode: Episode? = null,
    val emergency: EmergencyState? = null,
    val diag: Diag = Diag(),
)

@Serializable
data class DayStats(
    val blocks: Int = 0,
    val unlocks: Int = 0,
    val freeUnlocks: Int = 0,
    val emergencies: Int = 0,
    val aborted: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
    val perApp: Map<String, Int> = emptyMap(),
)

/** `stats.json`; key = epoch day in the local time zone. */
@Serializable
data class Stats(
    val schemaVersion: Int = 1,
    val days: Map<Long, DayStats> = emptyMap(),
)

/** Reference to one Anki card. */
@Serializable
data class CardRef(val noteId: Long, val ord: Int)
