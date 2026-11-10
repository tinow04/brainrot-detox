package dev.tinow.brainrotstopper.service

/**
 * Connects the lock activity with the gate (same process). [acked]: the start reached the activity (`onCreate`/`onNewIntent`,
 * also while the screen is off); [resumed]: it is really in front (`onResume`); [gone]: `onStop`. Only [resumed] and
 * [gone] decide whether the gate treats the lock as visible: an ack whose activity never comes to the front must not
 * leave the gate believing in a lock (M6-T7: target app usable with a stale "lock visible"). The listener is set by the
 * running service; without one the calls do nothing.
 */
object LockUiBeacon {
    interface Listener {
        fun onLockResumed(pkg: String)
        fun onLockGone()
        fun onLockAcked(pkg: String) {}
    }

    @Volatile
    var listener: Listener? = null

    fun resumed(pkg: String) {
        listener?.onLockResumed(pkg)
    }

    fun acked(pkg: String) {
        listener?.onLockAcked(pkg)
    }

    fun gone() {
        listener?.onLockGone()
    }
}
