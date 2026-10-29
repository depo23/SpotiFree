package com.project.lol.ui.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Search
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

data class SearchHit(
    val uri: String,
    val name: String,
    val subtitle: String,
    val image: String?,
    val type: String
)

private val TYPE_ORDER = listOf("artist" to "Artists", "track" to "Songs", "album" to "Albums", "playlist" to "Playlists")

private fun parseHits(json: String): Pair<List<SearchHit>, String?> {
    val text = json.trim()
    if (text.startsWith("{")) {
        val err = JSONObject(text).optString("error", "")
        return emptyList<SearchHit>() to err.ifEmpty { "Unexpected answer" }
    }
    val arr = JSONArray(text)
    val hits = ArrayList<SearchHit>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val uri = o.optString("id", "")
        if (!uri.startsWith("spotify:")) continue
        val type = uri.split(":").getOrNull(1) ?: "other"
        val artists = o.optJSONArray("artists")
        val names = ArrayList<String>()
        if (artists != null) for (k in 0 until artists.length()) names.add(artists.optString(k))
        val image = o.optString("image", "").takeIf { it.isNotEmpty() && it != "null" }
        hits.add(SearchHit(uri, o.optString("name", ""), names.joinToString(", "), image, type))
    }
    return hits to null
}

@Composable
fun NativeSearchScreen(
    initialQuery: String,
    onClose: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenWeb: (String) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            hits = emptyList(); error = null; loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(400)
        val answer = NativeCalls.call("search", JSONObject.quote(query.trim()))
        val parsed = try { parseHits(answer) } catch (e: Exception) { emptyList<SearchHit>() to (e.message ?: "Parse error") }
        hits = parsed.first
        error = parsed.second
        loading = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(TablerIcons.ChevronDown, contentDescription = "Close") }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search songs, artists, albums") },
                    leadingIcon = { Icon(TablerIcons.Search, contentDescription = null) },
                    shape = CircleShape
                )
            }
            if (loading) Text("Searching…", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text("Search failed: $it", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                val known = TYPE_ORDER.map { it.first }.toSet()
                val groups = TYPE_ORDER.map { (t, label) -> label to hits.filter { it.type == t } } +
                    listOf("More" to hits.filter { it.type !in known })
                groups.forEach { (label, list) ->
                    if (list.isNotEmpty()) {
                        item(key = "h_$label") {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(list, key = { it.uri }) { hit ->
                            HitRow(hit) {
                                when (hit.type) {
                                    "artist" -> onOpenArtist(hit.uri)
                                    "track" -> NativeUi.runJs("playFromUri(${JSONObject.quote(hit.uri)}, null)")
                                    "album" -> onOpenWeb("/album/" + hit.uri.substringAfterLast(":"))
                                    "playlist" -> onOpenWeb("/playlist/" + hit.uri.substringAfterLast(":"))
                                    "show" -> onOpenWeb("/show/" + hit.uri.substringAfterLast(":"))
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteImage(
            hit.image,
            Modifier.size(52.dp),
            if (hit.type == "artist") CircleShape else RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(hit.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = hit.subtitle.ifEmpty { hit.type.replaceFirstChar { it.uppercase() } }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
