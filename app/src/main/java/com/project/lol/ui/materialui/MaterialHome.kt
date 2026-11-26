package com.project.lol.ui.materialui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.World
import org.json.JSONObject

private data class HomeItem(val uri: String, val name: String, val image: String, val subtitle: String, val kind: String)
private data class HomeSection(val title: String, val grid: Boolean, val items: List<HomeItem>)
private data class HomeData(val greeting: String, val sections: List<HomeSection>)

// Survives leaving and re-entering the screen, so home doesn't reload every time.
private var cachedHome: HomeData? = null

private fun parseHome(json: String): Pair<HomeData?, String?> {
    val o = JSONObject(json)
    if (o.has("error")) return null to o.optString("error")
    val secs = o.optJSONArray("sections") ?: return null to "No sections"
    val sections = (0 until secs.length()).map { i ->
        val s = secs.getJSONObject(i)
        val arr = s.optJSONArray("items")
        val items = (0 until (arr?.length() ?: 0)).map { j ->
            val it = arr!!.getJSONObject(j)
            HomeItem(it.optString("uri"), it.optString("name"), it.optString("image"), it.optString("subtitle"), it.optString("kind"))
        }
        HomeSection(s.optString("title"), s.optString("style") == "grid", items)
    }.filter { it.items.isNotEmpty() }
    return HomeData(o.optString("greeting"), sections) to null
}

/** Spotify's home feed (greeting, shortcuts, suggestion rows) drawn natively. */
@Composable
fun MaterialHomeScreen(onOpenArtist: (String) -> Unit, onOpenWeb: (String) -> Unit, onShowWeb: () -> Unit) {
    var data by remember { mutableStateOf(cachedHome) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        if (data != null && reload == 0) return@LaunchedEffect
        error = null
        val answer = MaterialCalls.call("home", if (reload > 0) "true" else "false")
        val parsed = try { parseHome(answer) } catch (e: Exception) { null to (e.message ?: "Parse error") }
        parsed.first?.let { data = it; cachedHome = it }
        error = parsed.second
    }

    fun open(item: HomeItem) {
        when {
            item.uri.startsWith("spotify:artist:") -> onOpenArtist(item.uri)
            else -> MaterialUi.runJs("playFromUri(${JSONObject.quote(item.uri)}, ${JSONObject.quote(item.uri)})")
        }
    }

    fun webPath(item: HomeItem): String {
        val parts = item.uri.split(":")
        return if (parts.size >= 3) "/${parts[1]}/${parts[2]}" else "/"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            val d = data
            if (d != null) {
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp), modifier = Modifier.statusBarsPadding()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                d.greeting.ifEmpty { "Home" },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { reload++ }) { Icon(TablerIcons.Refresh, contentDescription = "Refresh") }
                            IconButton(onClick = onShowWeb) { Icon(TablerIcons.World, contentDescription = "Web player") }
                        }
                    }
                    d.sections.forEach { sec ->
                        if (sec.title.isNotEmpty()) item { HomeTitle(sec.title) }
                        if (sec.grid) {
                            sec.items.take(8).chunked(2).forEach { pair ->
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pair.forEach { ShortcutTile(it, Modifier.weight(1f), { open(it) }, { onOpenWeb(webPath(it)) }) }
                                        if (pair.size == 1) Box(Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                                    items(sec.items) { CardTile(it, { open(it) }, { onOpenWeb(webPath(it)) }) }
                                }
                            }
                        }
                    }
                    if (error != null) {
                        item { Text("Could not refresh: $error", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        error?.let { "Could not load home: $it" } ?: "Loading…",
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (error != null) {
                        Row {
                            IconButton(onClick = { reload++ }) { Icon(TablerIcons.Refresh, contentDescription = "Retry") }
                            IconButton(onClick = onShowWeb) { Icon(TablerIcons.World, contentDescription = "Web player") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(item: HomeItem, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteImage(item.image, Modifier.size(56.dp), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(item: HomeItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val round = item.uri.startsWith("spotify:artist:")
    Column(
        modifier = Modifier
            .width(150.dp)
            .padding(end = 14.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        RemoteImage(item.image, Modifier.size(150.dp), if (round) CircleShape else MaterialTheme.shapes.large)
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (item.subtitle.isNotEmpty()) {
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
