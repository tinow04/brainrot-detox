package dev.tinow.brainrotstopper.dummy

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Fullscreen test target app for blocking spikes. Logs resume/focus with uptimeMillis. */
class DummyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("slow", false)) Thread.sleep(800)

        val label = getString(R.string.dummy_label)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(getColor(R.color.dummy_bg))
        }
        root.addView(TextView(this).apply {
            text = label
            textSize = 32f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "pip"
            setOnClickListener { enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build()) }
        })
        root.addView(Button(this).apply {
            text = "dialog"
            setOnClickListener {
                AlertDialog.Builder(this@DummyActivity).setMessage("dialog").setPositiveButton("ok", null).show()
            }
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        Log.i("BSDummy", "BSDummy resumed t=${SystemClock.uptimeMillis()} pkg=$packageName")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.i("BSDummy", "BSDummy focus=$hasFocus t=${SystemClock.uptimeMillis()} pkg=$packageName")
    }
}
