package com.project.lol.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.project.lol.R
import com.project.lol.util.UpdateEvent
import java.io.File
import java.util.Locale

/** Full-screen update panel: wavy progress while downloading, then Install / Later. */
@Composable
fun UpdateDialog(
    event: UpdateEvent,
    canInstall: Boolean,
    onInstall: (File) -> Unit,
    onAllowInstalls: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit
) {
    when (event) {
        is UpdateEvent.Available -> UpdateScreen(
            title = stringResource(R.string.update_downloading_title),
            version = event.version,
            progress = 0f,
            centerPrimary = "0%",
            centerSecondary = "",
            notes = "",
            onClose = onLater
        )

        is UpdateEvent.Downloading -> UpdateScreen(
            title = stringResource(R.string.update_downloading_title),
            version = event.version,
            progress = event.percent / 100f,
            centerPrimary = "${event.percent}%",
            centerSecondary = sizeText(event.done, event.total),
            notes = "",
            onClose = onLater
        )

        is UpdateEvent.Ready -> UpdateScreen(
            title = stringResource(R.string.update_title_ready),
            version = event.version,
            progress = 1f,
            centerPrimary = "100%",
            centerSecondary = "",
            notes = event.notes,
            primaryLabel = stringResource(if (canInstall) R.string.update_install else R.string.update_allow_installs),
            onPrimary = { if (canInstall) onInstall(event.apk) else onAllowInstalls() },
            closeLabel = stringResource(R.string.update_later),
            onClose = onLater
        )

        UpdateEvent.UpToDate -> onDismiss()

        is UpdateEvent.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_title_failed)) },
            text = { Text(event.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_ok)) } }
        )
    }
}

@Composable
private fun sizeText(done: Long, total: Long): String {
    if (total <= 0L) return ""
    val mb = 1024.0 * 1024.0
    return stringResource(
        R.string.update_size_progress,
        String.format(Locale.getDefault(), "%.1f", done / mb),
        String.format(Locale.getDefault(), "%.1f", total / mb)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateScreen(
    title: String,
    version: String,
    progress: Float,
    centerPrimary: String,
    centerSecondary: String,
    notes: String,
    onClose: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    closeLabel: String = stringResource(R.string.update_close)
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_playstore),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.update_subtitle, version),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(260.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = centerPrimary,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (centerSecondary.isNotEmpty()) {
                            Text(
                                text = centerSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val points = remember(notes) {
                    notes.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .map { it.removePrefix("- ").removePrefix("* ").replace("**", "") }
                        .take(3)
                }
                if (points.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        points.forEach { line ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (primaryLabel != null) {
                        Button(
                            onClick = onPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) { Text(primaryLabel) }
                    }
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) { Text(closeLabel) }
                }
            }
        }
    }
}
