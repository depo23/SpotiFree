package com.project.lol.ui.materialui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.project.lol.offline.DownloadManager
import com.project.lol.offline.OfflineStore
import compose.icons.TablerIcons
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the download button shows for the current track. */
internal enum class DownloadUi { Idle, Downloading, Saved }

/**
 * Download state of the playing track: downloading (tap cancels), saved (tap opens
 * "Remove downloaded file"), or not saved (tap downloads). [button] draws the trigger
 * for each state; the menu anchors to it.
 */
@Composable
internal fun DownloadControl(
    title: String,
    button: @Composable (state: DownloadUi, onClick: () -> Unit) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var recheck by remember { mutableIntStateOf(0) }

    val downloading by produceState(false) {
        while (true) {
            value = DownloadManager.isDownloading()
            delay(500)
        }
    }
    // Re-checked on track change and when a download finishes or a file is removed.
    LaunchedEffect(downloading) { if (!downloading) recheck++ }

    val trackId by produceState("", title) {
        val answer = MaterialCalls.call("track", JSONObject.quote(""))
        value = try { JSONObject(answer).optString("id", "") } catch (_: Exception) { "" }
    }
    val saved by produceState(false, trackId, recheck) {
        value = trackId.isNotEmpty() && withContext(Dispatchers.IO) { OfflineStore.isTrackSaved(ctx, trackId) }
    }

    val state = when {
        downloading -> DownloadUi.Downloading
        saved -> DownloadUi.Saved
        else -> DownloadUi.Idle
    }

    Box {
        button(state) {
            when (state) {
                DownloadUi.Downloading -> DownloadManager.cancelAll()
                DownloadUi.Saved -> menu = true
                DownloadUi.Idle -> MaterialUi.runJs("splDoDownload()")
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Remove downloaded file") },
                onClick = {
                    menu = false
                    val id = trackId
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            OfflineStore.loadSongs(ctx).firstOrNull { it.id == id }?.let { OfflineStore.deleteSong(ctx, it) }
                        }
                        recheck++
                    }
                }
            )
        }
    }
}

@Composable
internal fun DownloadIconFor(state: DownloadUi, size: Int = 22) {
    when (state) {
        DownloadUi.Downloading -> Icon(TablerIcons.X, contentDescription = "Cancel download", modifier = Modifier.size(size.dp))
        DownloadUi.Saved -> Icon(TablerIcons.CircleCheck, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size.dp))
        DownloadUi.Idle -> Icon(DownloadIcon, contentDescription = "Download", modifier = Modifier.size(size.dp))
    }
}
