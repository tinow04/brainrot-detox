package dev.tinow.brainrotstopper.debug

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log

/**
 * Spike probe for M0 (debug only). Usage:
 * adb shell am broadcast -n dev.tinow.brainrotstopper/.debug.SpikeReceiver --es cmd <cmd> [--es deck 1] [--ei limit 1] ...
 * Commands: resolve, perm, decks, schedule, card, answer, addnote, fetch. Raw results go to logcat tag BSSpike.
 */
class SpikeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: "?"
        val pending = goAsync()
        Thread {
            val t0 = SystemClock.elapsedRealtime()
            try {
                run(context, cmd, intent)
            } catch (e: Throwable) {
                Log.i(TAG, "cmd=$cmd EXC ${e::class.java.name}: ${e.message}")
            }
            Log.i(TAG, "cmd=$cmd done ms=${SystemClock.elapsedRealtime() - t0}")
            pending.finish()
        }.start()
    }

    private fun run(ctx: Context, cmd: String, i: Intent) {
        val cr = ctx.contentResolver
        when (cmd) {
            "resolve" -> {
                val info = ctx.packageManager.resolveContentProvider(AUTHORITY, 0)
                Log.i(TAG, "cmd=resolve info=${info?.let { "${it.packageName} enabled=${it.enabled} perm=${it.readPermission}" }}")
            }
            "perm" -> {
                val r = ctx.checkSelfPermission(PERMISSION)
                Log.i(TAG, "cmd=perm granted=${r == PackageManager.PERMISSION_GRANTED}")
            }
            "decks" -> dump(cr.query(Uri.parse("content://$AUTHORITY/decks"), null, null, null, null), "decks", skip = "options")
            "schedule" -> {
                val sel = "limit=?,deckID=?"
                val args = arrayOf((i.getIntExtra("limit", 1)).toString(), i.getStringExtra("deck") ?: "1")
                dump(cr.query(Uri.parse("content://$AUTHORITY/schedule"), null, sel, args, null), "schedule")
            }
            "card" -> {
                val nid = i.getStringExtra("nid")!!
                val proj = arrayOf("question", "answer", "answer_pure", "reps", "last_review_time_secs", "deck_id")
                dump(cr.query(Uri.parse("content://$AUTHORITY/notes/$nid/cards/0"), proj, null, null, null), "card")
            }
            "answer" -> {
                val v = ContentValues().apply {
                    put("note_id", i.getStringExtra("nid")!!.toLong())
                    put("ord", 0)
                    put("answer_ease", i.getIntExtra("ease", 3))
                    put("time_taken", 1500L)
                }
                val n = cr.update(Uri.parse("content://$AUTHORITY/schedule"), v, null, null)
                Log.i(TAG, "cmd=answer updated=$n")
            }
            "addnote" -> {
                // --es mid <model id> --es flds_b64 <base64(utf-8, fields joined by 0x1f)> [--es tags x]
                val flds = String(android.util.Base64.decode(i.getStringExtra("flds_b64")!!, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val v = ContentValues().apply {
                    put("mid", i.getStringExtra("mid")!!.toLong())
                    put("flds", flds)
                    put("tags", i.getStringExtra("tags") ?: "")
                }
                Log.i(TAG, "cmd=addnote uri=${cr.insert(Uri.parse("content://$AUTHORITY/notes"), v)}")
            }
            "fetch" -> {
                // --es base http://10.0.2.2:8099 --es name "bild ä 1.png" --es token testtoken [--ez noauth true]
                val url = java.net.URL(i.getStringExtra("base") + "/" + Uri.encode(i.getStringExtra("name")))
                val c = url.openConnection() as java.net.HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 3000
                c.readTimeout = 3000
                if (!i.getBooleanExtra("noauth", false)) c.setRequestProperty("Authorization", "Bearer ${i.getStringExtra("token")}")
                val code = c.responseCode
                val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() }?.size
                Log.i(TAG, "cmd=fetch url=$url status=$code bytes=$bytes type=${c.contentType} etag=${c.getHeaderField("ETag")}")
            }
            else -> Log.i(TAG, "unknown cmd=$cmd")
        }
    }

    private fun dump(c: android.database.Cursor?, name: String, skip: String? = null) {
        if (c == null) {
            Log.i(TAG, "cmd=$name cursor=null")
            return
        }
        c.use {
            Log.i(TAG, "cmd=$name columns=${it.columnNames.joinToString()} rows=${it.count}")
            while (it.moveToNext()) {
                val row = it.columnNames.indices.joinToString(", ") { idx ->
                    val n = it.columnNames[idx]
                    if (n == skip) "$n=<skipped>" else "$n=${it.getString(idx)}"
                }
                Log.i(TAG, "cmd=$name row: $row")
            }
        }
    }

    companion object {
        const val TAG = "BSSpike"
        const val AUTHORITY = "com.ichi2.anki.flashcards"
        const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    }
}
