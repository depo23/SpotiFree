package com.project.lol.ui.nativeui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Search
import compose.icons.tablericons.X
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

private val FILTERS = listOf("all" to "All", "artist" to "Artists", "track" to "Songs", "album" to "Albums", "playlist" to "Playlists")

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

/** Rounded corners that are larger at the ends of a group, smaller in the middle. */
private fun groupShape(index: Int, count: Int): Shape {
    val big = 24.dp
    val small = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            hits = emptyList(); error = null; loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(350)
        val answer = NativeCalls.call("search", JSONObject.quote(query.trim()))
        val parsed = try { parseHits(answer) } catch (e: Exception) { emptyList<SearchHit>() to (e.message ?: "Parse error") }
        hits = parsed.first
        error = parsed.second
        loading = false
    }

    fun open(hit: SearchHit) {
        when (hit.type) {
            "artist" -> onOpenArtist(hit.uri)
            "track" -> NativeUi.runJs("playFromUri(${JSONObject.quote(hit.uri)}, null)")
            "album" -> onOpenWeb("/album/" + hit.uri.substringAfterLast(":"))
            "playlist" -> onOpenWeb("/playlist/" + hit.uri.substringAfterLast(":"))
            "show" -> onOpenWeb("/show/" + hit.uri.substringAfterLast(":"))
            else -> {}
        }
    }

    val visible = if (filter == "all") hits else hits.filter { it.type == filter }
    val top = if (filter == "all") (hits.firstOrNull { it.type == "artist" } ?: hits.firstOrNull()) else null
    val rest = if (top != null) visible.filter { it.uri != top.uri } else visible

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(TablerIcons.ChevronDown, contentDescription = "Close") }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search Spotify") },
                    leadingIcon = { Icon(TablerIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(TablerIcons.X, contentDescription = "Clear") }
                        }
                    },
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }

            AnimatedVisibility(visible = hits.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(FILTERS) { (key, label) ->
                        FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading && hits.isEmpty() -> CircularWavyProgressIndicator(modifier = Modifier.align(Alignment.Center).size(72.dp))
                    error != null -> Text(
                        "Search failed: $error",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    query.trim().length < 2 -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(TablerIcons.Search, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Text("Find songs, artists, albums and playlists", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    visible.isEmpty() && !loading -> Text(
                        "Nothing found",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (top != null) {
                            item(key = "top") {
                                TopResult(top) { open(top) }
                            }
                        }
                        val labels = mapOf("artist" to "Artists", "track" to "Songs", "album" to "Albums", "playlist" to "Playlists")
                        val groups = rest.groupBy { it.type }
                        val ordered = (labels.keys + groups.keys).distinct()
                        ordered.forEach { type ->
                            val list = groups[type] ?: return@forEach
                            item(key = "h_$type") {
                                Text(
                                    labels[type] ?: "More",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp)
                                )
                            }
                            items(list.size, key = { list[it].uri }) { i ->
                                HitRow(list[i], groupShape(i, list.size)) { open(list[i]) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopResult(hit: SearchHit, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(
                hit.image,
                Modifier.size(96.dp),
                if (hit.type == "artist") CircleShape else RoundedCornerShape(20.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Top result",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    hit.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    hit.subtitle.ifEmpty { hit.type.replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, shape: Shape, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
}
