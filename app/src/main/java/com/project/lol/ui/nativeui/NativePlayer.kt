package com.project.lol.ui.nativeui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FilledIconButton
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
import compose.icons.tablericons.ArrowsShuffle
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Heart
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
                        TablerIcons.Heart,
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
fun NativeFullPlayer(state: NativePlayerState, onClose: () -> Unit, onArtist: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val duration = state.duration.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else state.position.toFloat().coerceIn(0f, duration.toFloat())

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
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

            Spacer(Modifier.height(12.dp))
            RemoteImage(
                state.cover,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                MaterialTheme.shapes.extraLarge
            )
            Spacer(Modifier.height(28.dp))

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
                ToggleIcon(TablerIcons.Heart, state.fav, "Like") { NativeUi.like() }
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
                ToggleIcon(TablerIcons.ArrowsShuffle, state.shuffle, "Shuffle") { NativeUi.shuffle() }
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
        }
    }
}
