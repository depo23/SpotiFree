package com.project.lol.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.project.lol.R
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsMaximize
import compose.icons.tablericons.Gift
import compose.icons.tablericons.Headphones
import compose.icons.tablericons.Movie
import compose.icons.tablericons.Video

/** One-time "What's new" card, shown on first run and once after updating to a release that adds features. */
object WhatsNew {
    // Bump when the list below changes so everyone sees it once more.
    private const val ID = "fullscreen-player-1"
    private const val KEY = "WhatsNewSeen"

    fun pending(context: Context): Boolean =
        context.getSharedPreferences("spotilol_prefs", Context.MODE_PRIVATE).getString(KEY, null) != ID

    fun markSeen(context: Context) {
        context.getSharedPreferences("spotilol_prefs", Context.MODE_PRIVATE).edit().putString(KEY, ID).apply()
    }
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(TablerIcons.Gift, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.whats_new_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WhatsNewItem(TablerIcons.ArrowsMaximize, R.string.whats_new_player_title, R.string.whats_new_player_body)
                WhatsNewItem(TablerIcons.Video, R.string.whats_new_video_title, R.string.whats_new_video_body)
                WhatsNewItem(TablerIcons.Movie, R.string.whats_new_canvas_title, R.string.whats_new_canvas_body)
                WhatsNewItem(TablerIcons.Headphones, R.string.whats_new_podcast_title, R.string.whats_new_podcast_body)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.whats_new_ok), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun WhatsNewItem(icon: ImageVector, title: Int, body: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
