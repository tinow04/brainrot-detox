package dev.tinow.brainrotstopper.domain.quiz

import dev.tinow.brainrotstopper.domain.model.QuizSession
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot

sealed interface SessionResolution {
    val session: QuizSession

    /** No usable session: a fresh one at 0, with `required`, deck and duration frozen from the settings. */
    data class New(override val session: QuizSession) : SessionResolution

    /** Same target within the idle TTL. */
    data class Resume(override val session: QuizSession) : SessionResolution

    /** Other target within the idle TTL: `correct` carries over (architecture.md 3B). */
    data class Retarget(override val session: QuizSession) : SessionResolution
}

object SessionPolicy {
    /** A session lives for [Constants.SESSION_TTL_MS] of idle time on the same boot; time going backwards means stale. */
    fun isFresh(session: QuizSession, now: TimeSnapshot, ttlMs: Long = Constants.SESSION_TTL_MS): Boolean {
        if (now.bootCount < 0 || session.bootCount != now.bootCount) return false
        val idle = now.elapsedMs - session.lastActiveElapsed
        return idle in 0 until ttlMs
    }

    fun resolve(
        existing: QuizSession?, target: String, settings: Settings, now: TimeSnapshot,
        ttlMs: Long = Constants.SESSION_TTL_MS,
        newId: () -> String,
    ): SessionResolution {
        if (existing == null || !isFresh(existing, now, ttlMs)) return SessionResolution.New(create(target, settings.sanitized(), now, newId()))
        return if (existing.targetPkg == target) SessionResolution.Resume(existing)
        else SessionResolution.Retarget(existing.copy(targetPkg = target))
    }

    fun create(target: String, settings: Settings, now: TimeSnapshot, id: String) = QuizSession(
        sessionId = id,
        targetPkg = target,
        deck = settings.deck,
        required = settings.requiredCorrect,
        unlockMinutes = settings.unlockMinutes,
        startedElapsed = now.elapsedMs,
        lastActiveElapsed = now.elapsedMs,
        bootCount = now.bootCount,
        startedWall = now.wallMs,
    )
}
