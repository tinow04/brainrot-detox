package dev.tinow.brainrotstopper.data.system

import android.util.Log

/** Thin logging wrapper with the agreed tags (BS/Gate, BS/Svc, BS/Anki, BS/Quiz, BS/UI). Never log card content or tokens. */
object AppLog {
    const val GATE = "BS/Gate"
    const val SVC = "BS/Svc"

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        Log.w(tag, msg, t)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
    }
}
