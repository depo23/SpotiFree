package com.project.lol.ui.materialui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Home
import compose.icons.tablericons.Search
import kotlinx.coroutines.delay
import org.json.JSONObject

private sealed interface MaterialScreen {
    data object None : MaterialScreen
    data object Player : MaterialScreen
    data class Search(val query: String = "") : MaterialScreen
    data class Artist(val uri: String) : MaterialScreen
}

/** Native player, search and artist page drawn over the web page (beta). */
@Composable
fun MaterialShell(onSleepTimer: () -> Unit) {
    val state by MaterialUi.player.collectAsState()
    var screen by remember { mutableStateOf<MaterialScreen>(MaterialScreen.None) }
    var previous by remember { mutableStateOf<MaterialScreen>(MaterialScreen.None) }

    fun open(next: MaterialScreen) {
        previous = screen
        screen = next
    }

    fun back() {
        screen = if (screen is MaterialScreen.Artist && previous !is MaterialScreen.Artist) previous else MaterialScreen.None
        previous = MaterialScreen.None
    }

    // Home is the default screen; the web player shows only when asked for.
    var showHome by remember { mutableStateOf(true) }

    fun openWeb(path: String) {
        showHome = false
        MaterialUi.runJs("window.splNativeNav&&window.splNativeNav(${JSONObject.quote(path)})")
        screen = MaterialScreen.None
        previous = MaterialScreen.None
    }

    BackHandler(enabled = screen != MaterialScreen.None) { back() }

    var videoDismissed by remember { mutableStateOf(false) }
    val showVideo = state.video && !videoDismissed && (screen == MaterialScreen.None || screen == MaterialScreen.Player)

    fun enterVideo() {
        videoDismissed = false
        MaterialUi.runJs("window.__splVfsEnter&&window.__splVfsEnter()")
    }

    LaunchedEffect(state.video) {
        if (!state.video) {
            videoDismissed = false
        } else if (!videoDismissed) {
            delay(800)
            enterVideo()
        }
    }

    LaunchedEffect(state.title, state.artist) {
        if (state.hasTrack) {
            delay(2500)
            QueueCache.refresh("${state.title}|${state.artist}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showHome && !showVideo && screen != MaterialScreen.Player) {
            MaterialHomeScreen(
                onOpenArtist = { open(MaterialScreen.Artist(it)) },
                onOpenWeb = { openWeb(it) },
                onShowWeb = { showHome = false }
            )
        }
        if (showVideo) {
            MaterialVideoPlayer(state = state, onDismiss = { videoDismissed = true })
        } else if (screen == MaterialScreen.None) {
            if (state.hasTrack) {
                MaterialMiniPlayer(
                    state = state,
                    onOpen = { if (state.video) enterVideo() else open(MaterialScreen.Player) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
            FloatingActionButton(
                onClick = { open(MaterialScreen.Search()) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = if (state.hasTrack) 100.dp else 24.dp)
            ) {
                Icon(TablerIcons.Search, contentDescription = "Search")
            }
            if (!showHome) {
                FloatingActionButton(
                    onClick = { showHome = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = if (state.hasTrack) 172.dp else 96.dp)
                ) {
                    Icon(TablerIcons.Home, contentDescription = "Home")
                }
            }
        }

        AnimatedVisibility(
            visible = screen == MaterialScreen.Player && !showVideo,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            MaterialFullPlayer(
                state = state,
                onClose = { back() },
                onArtist = { open(MaterialScreen.Search(state.artist)) },
                onSleepTimer = onSleepTimer
            )
        }

        when (val s = screen) {
            is MaterialScreen.Search -> MaterialSearchScreen(
                initialQuery = s.query,
                onClose = { back() },
                onOpenArtist = { open(MaterialScreen.Artist(it)) },
                onOpenWeb = { openWeb(it) }
            )
            is MaterialScreen.Artist -> MaterialArtistScreen(
                uri = s.uri,
                onBack = { back() },
                onOpenWeb = { openWeb(it) }
            )
            else -> {}
        }
    }
}
