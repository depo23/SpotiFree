package com.project.lol.ui.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.PlayerPlay
import org.json.JSONObject
import java.util.Locale

private data class ArtistTrack(val uri: String, val name: String, val album: String, val image: String, val durationMs: Long)
private data class ArtistAlbum(val id: String, val name: String, val image: String, val year: String, val type: String)
private data class ArtistData(
    val name: String,
    val image: String,
    val followers: Long,
    val tracks: List<ArtistTrack>,
    val albums: List<ArtistAlbum>,
    val errors: List<String>
)

private fun parseArtist(json: String): Pair<ArtistData?, String?> {
    val o = JSONObject(json)
    if (o.has("error")) return null to o.optString("error")
    val tracks = ArrayList<ArtistTrack>()
    o.optJSONArray("tracks")?.let { a ->
        for (i in 0 until a.length()) a.optJSONObject(i)?.let {
            tracks.add(ArtistTrack(it.optString("uri"), it.optString("name"), it.optString("album"), it.optString("image"), it.optLong("duration")))
        }
    }
    val albums = ArrayList<ArtistAlbum>()
    o.optJSONArray("albums")?.let { a ->
        for (i in 0 until a.length()) a.optJSONObject(i)?.let {
            albums.add(ArtistAlbum(it.optString("id"), it.optString("name"), it.optString("image"), it.optString("year"), it.optString("type")))
        }
    }
    val errors = ArrayList<String>()
    o.optJSONArray("errors")?.let { a -> for (i in 0 until a.length()) errors.add(a.optString(i)) }
    return ArtistData(o.optString("name"), o.optString("image"), o.optLong("followers"), tracks, albums, errors) to null
}

private fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
fun NativeArtistScreen(uri: String, onBack: () -> Unit, onOpenWeb: (String) -> Unit) {
    var data by remember { mutableStateOf<ArtistData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        data = null; error = null
        val answer = NativeCalls.call("artist", JSONObject.quote(uri))
        val parsed = try { parseArtist(answer) } catch (e: Exception) { null to (e.message ?: "Parse error") }
        data = parsed.first
        error = parsed.second
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            val d = data
            if (d != null) {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                            RemoteImage(d.image, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                                        )
                                    )
                            )
                            Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                                Text(d.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                if (d.followers > 0) {
                                    Text(
                                        String.format(Locale.getDefault(), "%,d followers", d.followers),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { NativeUi.runJs("playFromUri(${JSONObject.quote(uri)}, ${JSONObject.quote(uri)})") },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Icon(TablerIcons.PlayerPlay, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play")
                        }
                    }
                    if (d.tracks.isNotEmpty()) {
                        item { SectionTitle("Popular") }
                        itemsIndexed(d.tracks) { index, t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { NativeUi.runJs("playFromUri(${JSONObject.quote(t.uri)}, ${JSONObject.quote(uri)})") }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", modifier = Modifier.width(26.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RemoteImage(t.image, Modifier.size(48.dp), RoundedCornerShape(10.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.album, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(fmtDuration(t.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (d.albums.isNotEmpty()) {
                        item { SectionTitle("Discography") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                                items(d.albums) { al ->
                                    Column(
                                        modifier = Modifier
                                            .width(150.dp)
                                            .padding(end = 14.dp)
                                            .clickable { onOpenWeb("/album/" + al.id) }
                                    ) {
                                        RemoteImage(al.image, Modifier.size(150.dp), MaterialTheme.shapes.large)
                                        Text(al.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                        Text("${al.year} · ${al.type.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (d.errors.isNotEmpty()) {
                        item { Text("Some data could not load: ${d.errors.joinToString("; ")}", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                Text(
                    error?.let { "Could not load the artist: $it" } ?: "Loading…",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                Icon(TablerIcons.ArrowLeft, contentDescription = "Back")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}
