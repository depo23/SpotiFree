package com.project.lol.ui.nativeui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import org.json.JSONObject

private sealed interface NativeScreen {
    data object None : NativeScreen
    data object Player : NativeScreen
    data class Search(val query: String = "") : NativeScreen
    data class Artist(val uri: String) : NativeScreen
}

/** Native player, search and artist page drawn over the web page (beta). */
@Composable
fun NativeShell(onSleepTimer: () -> Unit) {
    val state by NativeUi.player.collectAsState()
    var screen by remember { mutableStateOf<NativeScreen>(NativeScreen.None) }
    var previous by remember { mutableStateOf<NativeScreen>(NativeScreen.None) }

    fun open(next: NativeScreen) {
        previous = screen
        screen = next
    }

    fun back() {
        screen = if (screen is NativeScreen.Artist && previous !is NativeScreen.Artist) previous else NativeScreen.None
        previous = NativeScreen.None
    }

    fun openWeb(path: String) {
        NativeUi.runJs("window.splNativeNav&&window.splNativeNav(${JSONObject.quote(path)})")
        screen = NativeScreen.None
        previous = NativeScreen.None
    }

    BackHandler(enabled = screen != NativeScreen.None) { back() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (screen == NativeScreen.None) {
            if (state.hasTrack) {
                NativeMiniPlayer(
                    state = state,
                    onOpen = { open(NativeScreen.Player) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
            FloatingActionButton(
                onClick = { open(NativeScreen.Search()) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = if (state.hasTrack) 100.dp else 24.dp)
            ) {
                Icon(TablerIcons.Search, contentDescription = "Search")
            }
        }

        AnimatedVisibility(
            visible = screen == NativeScreen.Player,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NativeFullPlayer(
                state = state,
                onClose = { back() },
                onArtist = { open(NativeScreen.Search(state.artist)) },
                onSleepTimer = onSleepTimer
            )
        }

        when (val s = screen) {
            is NativeScreen.Search -> NativeSearchScreen(
                initialQuery = s.query,
                onClose = { back() },
                onOpenArtist = { open(NativeScreen.Artist(it)) },
                onOpenWeb = { openWeb(it) }
            )
            is NativeScreen.Artist -> NativeArtistScreen(
                uri = s.uri,
                onBack = { back() },
                onOpenWeb = { openWeb(it) }
            )
            else -> {}
        }
    }
}
