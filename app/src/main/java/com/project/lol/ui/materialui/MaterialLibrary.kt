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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Home
import compose.icons.tablericons.Search
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject

data class LibraryItem(val uri: String, val name: String, val image: String, val subtitle: String, val kind: String)

// Survives leaving the tab; refreshed each time the tab opens.
private var cachedLibrary: List<LibraryItem>? = null

private fun parseLibrary(json: String): Pair<List<LibraryItem>?, String?> {
    val o = JSONObject(json)
    if (o.has("error")) return null to o.optString("error")
    val arr = o.optJSONArray("items") ?: return null to "No items"
    return (0 until arr.length()).map { i ->
        val it = arr.getJSONObject(i)
        LibraryItem(it.optString("uri"), it.optString("name"), it.optString("image"), it.optString("subtitle"), it.optString("kind"))
    } to null
}

private val FILTERS = listOf("playlist" to "Playlists", "artist" to "Artists", "album" to "Albums", "podcast" to "Podcasts")

private fun kindLabel(kind: String) = when (kind) {
    "playlist" -> "Playlist"
    "artist" -> "Artist"
    "album" -> "Album"
    "podcast" -> "Podcast"
    "prerelease" -> "Pre-saved"
    else -> ""
}

/** Everything in the user's library, searchable and filterable by type. */
@Composable
fun MaterialLibraryScreen(onOpen: (LibraryItem) -> Unit, onHome: () -> Unit) {
    var items by remember { mutableStateOf(cachedLibrary) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    val ctx = LocalContext.current
    LaunchedEffect(reload) {
        // Saved library draws at once, then is refreshed each time the tab opens.
        if (items == null) MaterialStore.get(ctx, "library")?.let { e -> items = try { parseLibrary(e.json).first } catch (_: Exception) { null } }
        error = null
        val answer = MaterialCalls.call("library", JSONObject.quote(""))
        val parsed = try { parseLibrary(answer) } catch (e: Exception) { null to (e.message ?: "Parse error") }
        parsed.first?.let { items = it; cachedLibrary = it; MaterialStore.put(ctx, "library", answer) }
        error = if (items != null && parsed.first == null) null else parsed.second
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onHome) { Icon(TablerIcons.Home, contentDescription = "Home") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search in Your Library") },
                leadingIcon = { Icon(TablerIcons.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FILTERS) { (kind, label) ->
                    FilterChip(selected = filter == kind, onClick = { filter = if (filter == kind) null else kind }, label = { Text(label) })
                }
            }
            val list = items
            when {
                list != null -> {
                    val q = query.trim().lowercase()
                    val shown = list.filter { (filter == null || it.kind == filter || (filter == "album" && it.kind == "prerelease")) &&
                        (q.isEmpty() || it.name.lowercase().contains(q) || it.subtitle.lowercase().contains(q)) }
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)) {
                        items(shown, key = { it.uri }) { item ->
                            MediaRow(
                                image = item.image,
                                title = item.name,
                                subtitle = listOf(kindLabel(item.kind), item.subtitle).filter { it.isNotEmpty() }.joinToString(" · "),
                                round = item.kind == "artist",
                                size = 56,
                                onClick = { onOpen(item) }
                            )
                        }
                        if (shown.isEmpty()) item { Text("Nothing here", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load your library: $error", color = MaterialTheme.colorScheme.error)
                    Text("Tap to retry", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).clickable { reload++ })
                }
                else -> Box(Modifier.padding(top = 8.dp)) { ListSkeleton(rows = 10) }
            }
        }
    }
}
