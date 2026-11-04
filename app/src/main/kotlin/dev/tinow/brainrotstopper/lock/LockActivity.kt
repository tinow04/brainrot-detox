package dev.tinow.brainrotstopper.lock

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.content.Intent
import dev.tinow.brainrotstopper.service.LockUiBeacon

/** M0-S2 spike lock screen (replaced by the Compose LockRoot in M1/M4). */
class LockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1e1e2e")))
        setContentView(TextView(this).apply {
            text = "$ ./lock ${intent.getStringExtra(EXTRA_TARGET_PKG)}"
            setTextColor(Color.parseColor("#cdd6f4"))
            textSize = 20f
            gravity = Gravity.CENTER
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_TARGET_PKG)?.let { LockUiBeacon.resumed(it) }
    }

    override fun onResume() {
        super.onResume()
        Log.i("BS/Svc", "lock-resumed t=${SystemClock.uptimeMillis()}")
        intent.getStringExtra(EXTRA_TARGET_PKG)?.let { LockUiBeacon.resumed(it) }
    }

    override fun onStop() {
        super.onStop()
        LockUiBeacon.gone()
    }

    companion object {
        const val EXTRA_TARGET_PKG = "target_pkg"
    }
}
