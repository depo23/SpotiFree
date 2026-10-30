package com.project.lol.ui.nativeui

import com.project.lol.webview.WebViewHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class NativePlayerState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val cover: String = "",
    val playing: Boolean = false,
    val fav: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: String = "false",
    val duration: Long = 0L,
    val position: Long = 0L,
    val video: Boolean = false
) {
    val hasTrack: Boolean get() = title.isNotEmpty()
}

/** Player state fed by the web player, and the commands the native layer sends back. */
object NativeUi {
    val player = MutableStateFlow(NativePlayerState())

    private fun clean(v: String): String = if (v == "null") "" else v

    fun onStatus(json: String) {
        try {
            val o = JSONObject(json)
            player.update {
                it.copy(
                    title = clean(o.optString("track", "")),
                    artist = clean(o.optString("artist", "")),
                    album = clean(o.optString("album", "")),
                    cover = clean(o.optString("cover", "")),
                    playing = o.optBoolean("playing", false),
                    fav = o.optBoolean("fav", false),
                    shuffle = o.optString("shuffle", "off").let { s -> s == "shuffle" || s == "smart" },
                    repeat = o.optString("repeat", "false"),
                    video = o.optBoolean("video", false),
                    duration = o.optLong("duration", 0L),
                    position = if (o.has("position")) o.optLong("position", 0L) else it.position
                )
            }
        } catch (_: Exception) {
        }
    }

    fun onPosition(ms: Long) {
        player.update { it.copy(position = ms) }
    }

    fun runJs(script: String) {
        val wv = WebViewHost.webView ?: return
        wv.post { wv.evaluateJavascript(script, null) }
    }

    fun togglePlay() = runJs("actPlayPause(${!player.value.playing})")
    fun previous() = runJs("actSkipBack()")
    fun next() = runJs("actSkipForward()")
    fun shuffle() = runJs("actToggleShuffle()")
    fun repeat() = runJs("actRepeat()")
    fun like() = runJs("actAddToFav()")
    fun seek(ms: Long) = runJs("actSeek($ms)")
}

/** Calls into the page (search, artist data, play) and waits for the JSON answer. */
object NativeCalls {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val counter = AtomicInteger()

    fun deliver(id: Int, json: String) {
        pending.remove(id)?.complete(json)
    }

    suspend fun call(op: String, arg: String): String {
        val id = counter.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pending[id] = deferred
        val script = "window.splNative&&window.splNative($id,${JSONObject.quote(op)},${JSONObject.quote(arg)})"
        withContext(Dispatchers.Main) {
            val wv = WebViewHost.webView
            if (wv == null) deferred.complete("{\"error\":\"no webview\"}") else wv.evaluateJavascript(script, null)
        }
        val result = withTimeoutOrNull(25_000L) { deferred.await() }
        if (result == null) pending.remove(id)
        return result ?: "{\"error\":\"timeout\"}"
    }
}
