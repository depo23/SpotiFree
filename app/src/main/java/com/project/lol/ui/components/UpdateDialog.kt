package com.project.lol.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.project.lol.R
import com.project.lol.util.UpdateEvent
import java.io.File

/** Popup for a new version: progress while downloading, then Install / Later. */
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
        is UpdateEvent.Available -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text(stringResource(R.string.update_title_available, event.version)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_text_downloading, 0))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) } }
        )

        is UpdateEvent.Downloading -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text(stringResource(R.string.update_title_available, event.version)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_text_downloading, event.percent))
                    LinearProgressIndicator(
                        progress = { event.percent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) } }
        )

        is UpdateEvent.Ready -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text(stringResource(R.string.update_title_ready)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_text_ready, event.version))
                    if (event.notes.isNotBlank()) {
                        Text(
                            text = event.notes.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (canInstall) {
                    TextButton(onClick = { onInstall(event.apk) }) { Text(stringResource(R.string.update_install)) }
                } else {
                    TextButton(onClick = onAllowInstalls) { Text(stringResource(R.string.update_allow_installs)) }
                }
            },
            dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) } }
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
