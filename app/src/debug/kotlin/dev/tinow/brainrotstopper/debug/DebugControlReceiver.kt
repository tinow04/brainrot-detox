package dev.tinow.brainrotstopper.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.tinow.brainrotstopper.BrainrotApp
import dev.tinow.brainrotstopper.debug.seed.StatsSeed
import dev.tinow.brainrotstopper.di.AppContainer
import dev.tinow.brainrotstopper.domain.gate.GrantPolicy
import dev.tinow.brainrotstopper.domain.model.BsJson
import dev.tinow.brainrotstopper.domain.model.GrantKind
import dev.tinow.brainrotstopper.domain.model.Runtime
import dev.tinow.brainrotstopper.domain.model.Settings
import dev.tinow.brainrotstopper.domain.model.Stats
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Debug-only remote control (architecture.md 9):
 * `adb shell am broadcast -n dev.tinow.brainrotstopper/.debug.DebugControlReceiver -a dev.tinow.brainrotstopper.debug.action.<NAME> [--es k v]`.
 * Never part of the release variant (declared in src/debug/AndroidManifest.xml only).
 */
class DebugControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.removePrefix(PREFIX) ?: return
        val container = (context.applicationContext as BrainrotApp).container
        val pending = goAsync()
        container.ioScope.launch {
            try {
                handle(container, action, intent)
            } catch (e: Throwable) {
                Log.w(TAG, "action=$action failed: ${e::class.java.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(c: AppContainer, action: String, i: Intent) {
        val flags = DebugInit.flags
        val now = c.timeSource.now()
        when (action) {
            "SET_BLOCKED" -> {
                val pkgs = csv(i.getStringExtra("pkgs"))
                val on = i.getStringExtra("on") != "false"
                c.settings.update { it.copy(blockedPackages = if (on) it.blockedPackages + pkgs else it.blockedPackages - pkgs) }
            }
            "GRANT_NOW" -> {
                val pkg = i.getStringExtra("pkg") ?: run { Log.w(TAG, "GRANT_NOW needs --es pkg"); return }
                val minutes = i.getStringExtra("minutes")?.toIntOrNull() ?: c.settings.current().unlockMinutes
                val ms = flags.unlockSecondsOverride?.let { it * 1000L } ?: (minutes * 60_000L)
                c.runtime.update { it.copy(grants = GrantPolicy.prune(it.grants, now) + (pkg to GrantPolicy.create(GrantKind.QUIZ, ms, now))) }
            }
            "RESET" -> reset(c, i.getStringExtra("what") ?: "all")
            "DUMP_STATE" -> dump(c, i.getStringExtra("nonce"))
            "SET_SOURCE" -> {
                i.getStringExtra("source")?.let { flags.cardSource = it }
                i.getStringExtra("scenario")?.let { flags.scenario = it }
            }
            "SET_SHORT_DURATION" -> flags.unlockSecondsOverride = i.getStringExtra("seconds")?.toIntOrNull()
            "SET_TTL_MS" -> flags.sessionTtlMsOverride = i.getStringExtra("ms")?.toLongOrNull()
            "SIMULATE_LAUNCH_BLOCK" -> flags.simulateLaunchBlock = i.getStringExtra("on") == "true"
            "THROW_IN_HANDLER" -> flags.throwInHandler = i.getStringExtra("on") == "true"
            "SET_GATEWAY_DELAY_MS" -> {
                val ms = i.getStringExtra("ms")?.toLongOrNull() ?: 0L
                if (i.getStringExtra("stage") == "answer") flags.answerDelayMs = ms else flags.queryDelayMs = ms
            }
            "EVENT_FLOOD" -> {
                val pkg = i.getStringExtra("pkg") ?: return
                val n = i.getStringExtra("n")?.toIntOrNull() ?: 0
                flags.floodRequest = pkg to n
                c.gate?.inject(pkg, n) ?: Log.w(TAG, "EVENT_FLOOD: gate not running (service not bound)")
            }
            "FAKE_PERMISSIONS" -> flags.permissionOverride = i.getStringExtra("state")?.takeIf { it in setOf("off", "unbound", "restricted", "ok") }
            "SET_WALL_OFFSET_MS" -> flags.wallOffsetMs = i.getStringExtra("ms")?.toLongOrNull() ?: 0L
            "SEED_STATS" -> {
                val seed = if (i.getStringExtra("preset") == "empty") StatsSeed.empty() else StatsSeed.week(now.epochDay)
                c.stats.update { seed }
            }
            else -> Log.w(TAG, "unknown action $action")
        }
        Log.i(TAG, "action=$action ok")
    }

    private suspend fun reset(c: AppContainer, what: String) {
        when (what) {
            "grants" -> c.runtime.update { it.copy(grants = emptyMap()) }
            "session" -> c.runtime.update { it.copy(session = null, episode = null) }
            "stats" -> c.stats.update { Stats() }
            "settings" -> c.settings.update { Settings() }
            "all" -> {
                c.runtime.update { Runtime() }
                c.stats.update { Stats() }
                c.settings.update { Settings() }
                DebugInit.flags.reset()
            }
            else -> Log.w(TAG, "RESET: unknown what=$what")
        }
    }

    private suspend fun dump(c: AppContainer, nonce: String?) {
        val now = c.timeSource.now()
        val settings = c.settings.current()
        val rt = c.runtime.current()
        val stats = c.stats.current()
        val f = DebugInit.flags
        val json = buildJsonObject {
            nonce?.let { put("nonce", it) }
            put("now", buildJsonObject {
                put("elapsedMs", now.elapsedMs); put("wallMs", now.wallMs); put("bootCount", now.bootCount); put("epochDay", now.epochDay)
            })
            put("blocked", JsonArray(settings.blockedPackages.sorted().map(::JsonPrimitive)))
            put("settings", BsJson.encodeToJsonElement(Settings.serializer(), settings.copy(mediaToken = "")))
            put("grants", buildJsonObject {
                rt.grants.forEach { (pkg, g) ->
                    put(pkg, buildJsonObject {
                        put("kind", g.kind.name); put("bootCount", g.bootCount); put("valid", GrantPolicy.isValid(g, now))
                        put("remainingMs", GrantPolicy.remainingMs(g, now)); put("durationMs", g.durationMs)
                    })
                }
            })
            put("session", rt.session?.let { BsJson.encodeToJsonElement(dev.tinow.brainrotstopper.domain.model.QuizSession.serializer(), it) } ?: JsonNull)
            put("episode", rt.episode?.let { BsJson.encodeToJsonElement(dev.tinow.brainrotstopper.domain.model.Episode.serializer(), it) } ?: JsonNull)
            put("emergency", rt.emergency?.let { BsJson.encodeToJsonElement(dev.tinow.brainrotstopper.domain.model.EmergencyState.serializer(), it) } ?: JsonNull)
            put("diag", BsJson.encodeToJsonElement(dev.tinow.brainrotstopper.domain.model.Diag.serializer(), rt.diag))
            put("statsDays", stats.days.size)
            put("statsToday", stats.days[now.epochDay]?.let { BsJson.encodeToJsonElement(dev.tinow.brainrotstopper.domain.model.DayStats.serializer(), it) } ?: JsonNull)
            put("flags", buildJsonObject {
                put("sessionTtlMsOverride", f.sessionTtlMsOverride); put("unlockSecondsOverride", f.unlockSecondsOverride)
                put("simulateLaunchBlock", f.simulateLaunchBlock); put("throwInHandler", f.throwInHandler)
                put("wallOffsetMs", f.wallOffsetMs); put("cardSource", f.cardSource); put("scenario", f.scenario)
                put("answerDelayMs", f.answerDelayMs); put("queryDelayMs", f.queryDelayMs)
                put("permissionOverride", f.permissionOverride)
                put("flood", f.floodRequest?.let { "${it.first}:${it.second}" })
            })
        }
        Log.i("BSState", json.toString())
    }

    private fun csv(value: String?): Set<String> = value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    companion object {
        const val PREFIX = "dev.tinow.brainrotstopper.debug.action."
        private const val TAG = "BS/Debug"
    }
}
