package com.project.lol.ui.nativeui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import compose.icons.tablericons.PlayerPause
import compose.icons.tablericons.PlayerPlay
import compose.icons.tablericons.X
import kotlinx.coroutines.delay
import org.json.JSONObject

private data class VideoInfo(val cur: Long = 0L, val dur: Long = 0L, val paused: Boolean = false, val fullscreen: Boolean = false)

private suspend fun readVideo(): VideoInfo? {
    val answer = NativeCalls.call("video", JSONObject.quote(""))
    return try {
        val o = JSONObject(answer)
        if (!o.optBoolean("ok", false)) null
        else VideoInfo(o.optLong("cur"), o.optLong("dur"), o.optBoolean("paused"), o.optBoolean("fs"))
    } catch (_: Exception) {
        null
    }
}

/**
 * Web video drawn fullscreen by the page; this layer is transparent and only adds the native
 * title, close, seek and play controls, which a tap on the video hides or shows.
 */
@Composable
fun NativeVideoPlayer(state: NativePlayerState, onDismiss: () -> Unit) {
    var controls by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(VideoInfo()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var seenFullscreen = false
        var misses = 0
        while (true) {
            val v = readVideo()
            if (v != null) {
                misses = 0
                info = v
                if (v.fullscreen) seenFullscreen = true
                else if (seenFullscreen) { onDismiss(); return@LaunchedEffect }
            } else if (++misses >= 4) {
                onDismiss()
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    LaunchedEffect(controls, info.paused) {
        if (controls && !info.paused) {
            delay(3500)
            controls = false
        }
    }

    fun close() {
        NativeUi.runJs("window.__splVfsExit&&window.__splVfsExit()")
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { controls = !controls }
    ) {
        AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                )
                Row(
                    modifier = Modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { close() }) {
                        Icon(TablerIcons.X, contentDescription = "Close", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(state.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.artist, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp).align(Alignment.BottomCenter)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        TextButtonLabel("-10") { NativeUi.runJs("window.__splVfsBy&&window.__splVfsBy(-10)") }
                        FilledIconButton(onClick = { NativeUi.runJs("window.__splVfsToggle&&window.__splVfsToggle()") }, modifier = Modifier.padding(horizontal = 20.dp).size(64.dp)) {
                            Icon(if (info.paused) TablerIcons.PlayerPlay else TablerIcons.PlayerPause, contentDescription = "Play or pause", modifier = Modifier.size(32.dp))
                        }
                        TextButtonLabel("+10") { NativeUi.runJs("window.__splVfsBy&&window.__splVfsBy(10)") }
                    }
                    val dur = info.dur.coerceAtLeast(1L)
                    val shown = if (dragging) dragValue else info.cur.toFloat()
                    Slider(
                        value = shown.coerceIn(0f, dur.toFloat()),
                        onValueChange = { dragging = true; dragValue = it },
                        onValueChangeFinished = {
                            NativeUi.runJs("window.__splVfsSeek&&window.__splVfsSeek(${dragValue.toLong()})")
                            dragging = false
                        },
                        valueRange = 0f..dur.toFloat()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(shown.toLong()), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(info.dur), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonLabel(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}
