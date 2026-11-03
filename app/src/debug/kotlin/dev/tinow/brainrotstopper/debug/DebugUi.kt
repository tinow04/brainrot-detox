package dev.tinow.brainrotstopper.debug

import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** Debug variant: logs the window bounds of tagged elements so scripts can tap them by tag. */
fun Modifier.debugBounds(tag: String): Modifier = onGloballyPositioned { c ->
    val r = c.boundsInWindow()
    Log.i("BS/UI", "btn=$tag x=${r.center.x.toInt()} y=${r.center.y.toInt()} w=${r.width.toInt()} h=${r.height.toInt()}")
}
