package com.project.lol.ui.materialui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import org.json.JSONObject
import java.util.Locale

private data class CollectionTrack(val uri: String, val name: String, val artist: String, val image: String, val durationMs: Long)
private data class CollectionData(val name: String, val image: String, val subtitle: String, val album: Boolean, val tracks: List<CollectionTrack>)

private fun parseCollection(json: String): Pair<CollectionData?, String?> {
    val o = JSONObject(json)
    if (o.has("error")) return null to o.optString("error")
    val arr = o.optJSONArray("tracks")
    val tracks = (0 until (arr?.length() ?: 0)).map { i ->
        val t = arr!!.getJSONObject(i)
        CollectionTrack(t.optString("uri"), t.optString("name"), t.optString("artist"), t.optString("image"), t.optLong("duration"))
    }
    return CollectionData(o.optString("name"), o.optString("image"), o.optString("subtitle"), o.optString("kind") == "album", tracks) to null
}

/** Album or playlist page (playlists include mixes and "Made for" lists): header, play, download, all tracks. */
@Composable
fun MaterialCollectionScreen(uri: String, onBack: () -> Unit) {
    var data by remember(uri) { mutableStateOf<CollectionData?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var note by remember(uri) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uri) {
        val parsed = try { parseCollection(MaterialCalls.call("collection", JSONObject.quote(uri))) } catch (e: Exception) { null to (e.message ?: "Parse error") }
        data = parsed.first
        error = parsed.second
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            val d = data
            when {
                d != null -> LazyColumn(contentPadding = PaddingValues(bottom = 180.dp), modifier = Modifier.statusBarsPadding()) {
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
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { MaterialUi.runJs("playFromUri(${JSONObject.quote(t.uri)}, ${JSONObject.quote(uri)})") }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!d.album) RemoteImage(t.image, Modifier.size(48.dp), RoundedCornerShape(6.dp))
                            Column(Modifier.weight(1f).padding(start = if (d.album) 0.dp else 12.dp)) {
                                Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (t.durationMs > 0) {
                                val s = t.durationMs / 1000
                                Text(String.format(Locale.US, "%d:%02d", s / 60, s % 60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                error != null -> Text("Could not load: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                else -> Box(Modifier.statusBarsPadding().padding(top = 40.dp)) { ListSkeleton(rows = 8, header = 220.dp) }
            }
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back") }
        }
    }
}
