package com.project.lol.ui.materialui

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Cache for Material UI answers (the page's JSON): in memory for the session and on disk
 * across launches, so screens draw at once and refresh behind the scenes.
 */
object MaterialStore {
    data class Entry(val json: String, val savedAt: Long) {
        fun ageMs() = System.currentTimeMillis() - savedAt
    }

    private const val MAX_FILES = 120
    private val memory = LruCache<String, Entry>(60)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dir(ctx: Context) = File(ctx.filesDir, "material_cache").apply { mkdirs() }

    private fun file(ctx: Context, key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir(ctx), "$hash.json")
    }

    /** Memory first, then disk. */
    suspend fun get(ctx: Context, key: String): Entry? {
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(ctx, key)
            if (!f.isFile) return@withContext null
            try { Entry(f.readText(), f.lastModified()).also { memory.put(key, it) } } catch (_: Exception) { null }
        }
    }

    /** Only answers without an error are kept. */
    fun put(ctx: Context, key: String, json: String) {
        if (try { org.json.JSONObject(json).has("error") } catch (_: Exception) { true }) return
        memory.put(key, Entry(json, System.currentTimeMillis()))
        scope.launch {
            try {
                file(ctx, key).writeText(json)
                val files = dir(ctx).listFiles()?.sortedBy { it.lastModified() } ?: return@launch
                files.take((files.size - MAX_FILES).coerceAtLeast(0)).forEach { it.delete() }
            } catch (_: Exception) {}
        }
    }

    fun clear(ctx: Context) {
        memory.evictAll()
        scope.launch { dir(ctx).listFiles()?.forEach { it.delete() } }
    }

    /** Loads an artist page ahead of a tap; skipped when a fresh copy is already kept. */
    private val prefetching = HashSet<String>()
    fun prefetchArtist(ctx: Context, uri: String) {
        val key = "artist:$uri"
        synchronized(prefetching) { if (!prefetching.add(uri)) return }
        scope.launch {
            val cached = get(ctx, key)
            if (cached == null || cached.ageMs() > ARTIST_TTL) put(ctx, key, MaterialCalls.call("artist", org.json.JSONObject.quote(uri)))
        }
    }

    const val ARTIST_TTL = 30 * 60_000L
    const val PLAYLIST_TTL = 5 * 60_000L
}
