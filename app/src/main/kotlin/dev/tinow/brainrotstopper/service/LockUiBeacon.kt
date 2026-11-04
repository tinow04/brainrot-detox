package dev.tinow.brainrotstopper.service

/**
 * Connects the lock activity with the gate (same process). `LockActivity.onResume` reports itself (that is the launch
 * ack), `onStop` reports that it is gone. The listener is set by the running service; without one the calls do nothing.
 */
object LockUiBeacon {
    interface Listener {
        fun onLockResumed(pkg: String)
        fun onLockGone()
    }

    @Volatile
    var listener: Listener? = null

    fun resumed(pkg: String) {
        listener?.onLockResumed(pkg)
    }

    fun gone() {
        listener?.onLockGone()
    }
}
