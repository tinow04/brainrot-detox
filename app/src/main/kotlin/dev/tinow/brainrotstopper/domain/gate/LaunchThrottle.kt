package dev.tinow.brainrotstopper.domain.gate

import dev.tinow.brainrotstopper.domain.quiz.Constants

/** Remembers recent lock starts: per-package relaunch spacing and the global start-rate breaker. */
data class LaunchThrottle(private val starts: List<Pair<String, Long>> = emptyList()) {
    fun record(pkg: String, now: Long): LaunchThrottle =
        LaunchThrottle((starts + (pkg to now)).filter { now - it.second < Constants.BREAKER_WINDOW_MS })

    /** End of the relaunch spacing window of [pkg], or null when a start is allowed right now. */
    fun blockedUntil(pkg: String, now: Long): Long? {
        val last = starts.lastOrNull { it.first == pkg }?.second ?: return null
        val end = last + Constants.RELAUNCH_MIN_MS
        return if (now < end) end else null
    }

    /** More than BREAKER_STARTS starts inside BREAKER_WINDOW_MS: this attempt would be one too many. */
    fun breakerTripped(now: Long): Boolean =
        starts.count { now - it.second < Constants.BREAKER_WINDOW_MS } >= Constants.BREAKER_STARTS
}
