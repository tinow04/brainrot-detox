package dev.tinow.brainrotstopper.domain.runtime

import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.time.TimeSnapshot

/** What the quiz engine needs from `runtime.json`; `RuntimeRepository` is the production implementation, tests use a memory one. */
interface RuntimeStore {
    suspend fun current(): Runtime

    /** Applies a pure runtime operation in a single atomic update and records its statistics. */
    suspend fun <T> apply(now: TimeSnapshot, op: (Runtime) -> Applied<T>): T

    /** Plain runtime change without statistics (write-ahead pending). */
    suspend fun update(transform: (Runtime) -> Runtime): Runtime
}
