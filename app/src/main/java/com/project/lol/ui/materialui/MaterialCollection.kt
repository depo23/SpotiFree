package com.project.lol.ui.materialui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.PlayerPlay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import org.json.JSONObject
import java.util.Locale

private data class CollectionTrack(val uri: String, val name: String, val artist: String, val image: String, val durationMs: Long)
private data class CollectionData(val name: String, val image: String, val subtitle: String, val album: Boolean, val tracks: List<CollectionTrack>, val total: Int)

private fun parseCollection(json: String): Pair<CollectionData?, String?> {
    val o = JSONObject(json)
    if (o.has("error")) return null to o.optString("error")
    val arr = o.optJSONArray("tracks")
    val tracks = (0 until (arr?.length() ?: 0)).map { i ->
        val t = arr!!.getJSONObject(i)
        CollectionTrack(t.optString("uri"), t.optString("name"), t.optString("artist"), t.optString("image"), t.optLong("duration"))
    }
    return CollectionData(o.optString("name"), o.optString("image"), o.optString("subtitle"), o.optString("kind") == "album", tracks, o.optInt("total", tracks.size).coerceAtLeast(tracks.size)) to null
}

/** Album or playlist page (playlists include mixes and "Made for" lists): header, play, download, all tracks. */
@Composable
fun MaterialCollectionScreen(uri: String, onBack: () -> Unit) {
    var data by remember(uri) { mutableStateOf<CollectionData?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var note by remember(uri) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val ctx = LocalContext.current
    val list = rememberLazyListState()
    var loadingMore by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        // Albums never change, so a kept copy is used as is; playlists are refreshed after a few minutes.
        val key = "collection:$uri"
        val cached = MaterialStore.get(ctx, key)
        cached?.let { e -> data = try { parseCollection(e.json).first } catch (_: Exception) { null } }
        val fresh = cached != null && data != null && (data!!.album || cached.ageMs() < MaterialStore.PLAYLIST_TTL)
        if (fresh) return@LaunchedEffect
        val answer = MaterialCalls.call("collection", JSONObject.quote(uri))
        val parsed = try { parseCollection(answer) } catch (e: Exception) { null to (e.message ?: "Parse error") }
        parsed.first?.let { data = it; MaterialStore.put(ctx, key, answer) }
        if (data == null) error = parsed.second
    }

    // Long playlists load 100 tracks at a time as the list nears its end.
    LaunchedEffect(list, data) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            val d = data ?: return@collect
            if (loadingMore || d.album || d.tracks.size >= d.total || last < d.tracks.size - 15) return@collect
            loadingMore = true
            val arg = JSONObject().put("uri", uri).put("offset", d.tracks.size).toString()
            val more = try { parseCollection(MaterialCalls.call("collection", arg)).first } catch (_: Exception) { null }
            if (more != null && more.tracks.isNotEmpty()) data = d.copy(tracks = d.tracks + more.tracks)
            else data = d.copy(total = d.tracks.size)
            loadingMore = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            val d = data
            when {
                d != null -> LazyColumn(state = list, contentPadding = PaddingValues(bottom = 180.dp), modifier = Modifier.statusBarsPadding()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            RemoteImage(d.image, Modifier.size(220.dp), MaterialTheme.shapes.large)
                            Text(d.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                            if (d.subtitle.isNotEmpty()) {
                                Text(d.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                            Row(Modifier.padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { MaterialUi.runJs("playFromUri(${JSONObject.quote(uri)}, ${JSONObject.quote(uri)})") }) {
                                    Icon(TablerIcons.PlayerPlay, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Play", modifier = Modifier.padding(start = 6.dp))
                                }
                                FilledTonalButton(onClick = {
                                    note = "Downloading…"
                                    scope.launch {
                                        val parts = uri.split(":")
                                        val answer = MaterialCalls.call("downloadCollection", JSONObject.quote("/${parts[1]}/${parts[2]}"))
                                        val err = try { JSONObject(answer).optString("error", "") } catch (_: Exception) { "" }
                                        note = if (err.isNotEmpty()) "Download failed: $err" else "Download started"
                                    }
                                }) {
                                    Icon(DownloadIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Download", modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    items(d.tracks) { t ->
                        MediaRow(
                            image = if (d.album) null else t.image,
                            title = t.name,
                            subtitle = t.artist,
                            trailing = if (t.durationMs > 0) (t.durationMs / 1000).let { s -> String.format(Locale.US, "%d:%02d", s / 60, s % 60) } else null,
                            onClick = { MaterialUi.runJs("playFromUri(${JSONObject.quote(t.uri)}, ${JSONObject.quote(uri)})") }
                        )
                    }
                    if (loadingMore) item { ListSkeleton(rows = 3) }
                }
                error != null -> Text("Could not load: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                else -> Box(Modifier.statusBarsPadding().padding(top = 40.dp)) { ListSkeleton(rows = 8, header = 220.dp) }
            }
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back") }
        }
    }
}
