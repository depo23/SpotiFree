package com.project.lol.ui.nativeui

import androidx.compose.animation.core.animateDpAsState
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import com.project.lol.offline.DownloadManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import com.project.lol.timer.SleepTimerManager
import com.project.lol.timer.SleepTimerMode
import compose.icons.tablericons.Clock
import compose.icons.tablericons.X
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.PlayerPause
import compose.icons.tablericons.PlayerPlay
import compose.icons.tablericons.PlayerSkipBack
import compose.icons.tablericons.PlayerSkipForward
import compose.icons.tablericons.Repeat
import compose.icons.tablericons.RepeatOnce

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun NativeMiniPlayer(state: NativePlayerState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteImage(state.cover, Modifier.size(48.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { NativeUi.like() }) {
                    Icon(
                        if (state.fav) HeartFilledIcon else HeartOutlineIcon,
                        contentDescription = "Like",
                        tint = if (state.fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PlayButton(state.playing, 44.dp) { NativeUi.togglePlay() }
            }
            val progress = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 8.dp)
                    .height(3.dp)
            )
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val corner by animateDpAsState(if (playing) size / 3 else size / 2, label = "playCorner")
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(corner)
    ) {
        Icon(
            if (playing) TablerIcons.PlayerPause else TablerIcons.PlayerPlay,
            contentDescription = if (playing) "Pause" else "Play",
            modifier = Modifier.size(size / 2)
        )
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, active: Boolean, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun NativeFullPlayer(state: NativePlayerState, onClose: () -> Unit, onArtist: () -> Unit, onSleepTimer: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var canvasUrl by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(state.title) {
        canvasUrl = ""
        controlsVisible = true
        repeat(25) {
            val answer = NativeCalls.call("canvas", JSONObject.quote(""))
            val url = try { JSONObject(answer).optString("url", "") } catch (_: Exception) { "" }
            if (url.isNotEmpty()) {
                canvasUrl = url
                return@LaunchedEffect
            }
            delay(2000)
        }
    }

    val hasCanvas = canvasUrl.isNotEmpty()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (hasCanvas) Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { controlsVisible = !controlsVisible } else Modifier
                )
        ) {
            if (hasCanvas) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(36.dp))
                ) {
                    CanvasVideo(canvasUrl, Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                    )
                }
            }
            AnimatedVisibility(
                visible = !hasCanvas || controlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PlayerBody(state, showCover = !hasCanvas, onClose = onClose, onArtist = onArtist, onQueue = { showQueue = true }, onSleepTimer = onSleepTimer)
            }
        }
    }

    if (showQueue) QueueSheet(state, onDismiss = { showQueue = false })
}

private class CanvasPlayer(val view: TextureView) {
    var player: MediaPlayer? = null
    var url: String = ""

    fun fit() {
        val mp = player ?: return
        val vw = mp.videoWidth.toFloat()
        val vh = mp.videoHeight.toFloat()
        if (vw <= 0f || vh <= 0f || view.width == 0 || view.height == 0) return
        val scale = maxOf(view.width / vw, view.height / vh)
        val m = Matrix()
        m.setScale(vw * scale / view.width, vh * scale / view.height, view.width / 2f, view.height / 2f)
        view.setTransform(m)
    }

    fun load(surface: SurfaceTexture?) {
        release()
        if (url.isEmpty() || surface == null) return
        try {
            player = MediaPlayer().apply {
                setSurface(Surface(surface))
                setDataSource(url)
                isLooping = true
                setVolume(0f, 0f)
                setOnPreparedListener { fit(); start() }
                setOnVideoSizeChangedListener { _, _, _ -> fit() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}

@Composable
private fun CanvasVideo(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            val tv = TextureView(ctx)
            val holder = CanvasPlayer(tv)
            tv.tag = holder
            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = holder.load(st)
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = holder.fit()
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    holder.release()
                    return true
                }
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
            tv
        },
        update = { view ->
            val holder = view.tag as CanvasPlayer
            if (holder.url != url) {
                holder.url = url
                holder.load(if (view.isAvailable) view.surfaceTexture else null)
            }
        },
        onRelease = { (it.tag as CanvasPlayer).release() }
    )
}

@Composable
private fun PlayerBody(
    state: NativePlayerState,
    showCover: Boolean,
    onClose: () -> Unit,
    onArtist: () -> Unit,
    onQueue: () -> Unit,
    onSleepTimer: () -> Unit
) {
    val timer by produceState(SleepTimerManager.state()) {
        while (true) {
            value = SleepTimerManager.state()
            delay(1000)
        }
    }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val duration = state.duration.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else state.position.toFloat().coerceIn(0f, duration.toFloat())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(TablerIcons.ChevronDown, contentDescription = "Close") }
            Spacer(Modifier.weight(1f))
            Text("Now playing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        if (showCover) {
            Spacer(Modifier.height(12.dp))
            RemoteImage(
                state.cover,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                MaterialTheme.shapes.extraLarge
            )
            Spacer(Modifier.height(28.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onArtist)
                )
            }
            ToggleIcon(if (state.fav) HeartFilledIcon else HeartOutlineIcon, state.fav, "Like") { NativeUi.like() }
        }

        Spacer(Modifier.height(12.dp))
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                NativeUi.seek(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown.toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(state.duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToggleIcon(ShuffleIcon, state.shuffle, "Shuffle") { NativeUi.shuffle() }
            IconButton(onClick = { NativeUi.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(TablerIcons.PlayerSkipBack, contentDescription = "Previous", modifier = Modifier.size(32.dp))
            }
            PlayButton(state.playing, 84.dp) { NativeUi.togglePlay() }
            IconButton(onClick = { NativeUi.next() }, modifier = Modifier.size(56.dp)) {
                Icon(TablerIcons.PlayerSkipForward, contentDescription = "Next", modifier = Modifier.size(32.dp))
            }
            ToggleIcon(
                if (state.repeat == "mixed") TablerIcons.RepeatOnce else TablerIcons.Repeat,
                state.repeat != "false",
                "Repeat"
            ) { NativeUi.repeat() }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(onClick = onQueue, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(QueueIcon, contentDescription = "Queue", modifier = Modifier.size(22.dp))
            }
            val downloading by produceState(false) {
                while (true) {
                    value = DownloadManager.isDownloading()
                    delay(500)
                }
            }
            FilledTonalButton(
                onClick = { if (downloading) DownloadManager.cancelAll() else NativeUi.runJs("splDoDownload()") },
                modifier = Modifier.weight(1f).height(52.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                if (downloading) Icon(TablerIcons.X, contentDescription = "Cancel download", modifier = Modifier.size(22.dp))
                else Icon(DownloadIcon, contentDescription = "Download", modifier = Modifier.size(22.dp))
            }
            val timerLabel = when {
                !timer.active -> ""
                timer.mode == SleepTimerMode.COUNTDOWN -> formatTime(timer.remainingMs)
                else -> "End of song"
            }
            val timerContent: @Composable () -> Unit = {
                Icon(TablerIcons.Clock, contentDescription = "Sleep timer", modifier = Modifier.size(22.dp))
                if (timerLabel.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(timerLabel, maxLines = 1)
                }
            }
            if (timer.active) {
                Button(onClick = onSleepTimer, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { timerContent() }
            } else {
                FilledTonalButton(onClick = onSleepTimer, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { timerContent() }
            }
        }
    }
}

private data class QueueItem(val uri: String, val name: String, val artist: String, val image: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: NativePlayerState, onDismiss: () -> Unit) {
    var items by remember { mutableStateOf<List<QueueItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val answer = NativeCalls.call("queue", JSONObject.quote(""))
        try {
            val o = JSONObject(answer)
            if (o.has("error")) {
                error = o.optString("error")
            } else {
                val arr: JSONArray = o.optJSONArray("items") ?: JSONArray()
                val upcoming = (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { QueueItem(it.optString("uri"), it.optString("name"), it.optString("artist"), it.optString("image")) }
                }
                items = listOf(QueueItem("", state.title, state.artist, state.cover)) + upcoming
            }
        } catch (e: Exception) {
            error = e.message ?: "Parse error"
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            "Queue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        when {
            loading -> Text("Loading…", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            error != null -> Text("Could not read the queue: $error", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
            items.size <= 1 -> Text("Nothing is queued after this track", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(modifier = Modifier.fillMaxWidth().height(480.dp)) {
                items(items.size) { idx ->
                    val q = items[idx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = q.uri.isNotEmpty()) {
                                NativeUi.runJs("playFromUri(${JSONObject.quote(q.uri)}, null)")
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RemoteImage(q.image, Modifier.size(48.dp), RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                q.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (idx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(q.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
