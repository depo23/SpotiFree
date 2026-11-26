package com.project.lol.ui.materialui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable

/** A soft light band sweeping across placeholder shapes while content loads. */
@Composable
private fun shimmer(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -600f,
        targetValue = 1600f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    return Brush.linearGradient(listOf(base, light, base), start = Offset(x, 0f), end = Offset(x + 600f, 300f))
}

@Composable
private fun Bone(brush: Brush, modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.background(brush, shape))
}

/** Placeholder for home: greeting, shortcut grid and two card rows. */
@Composable
fun HomeSkeleton() {
    val b = shimmer()
    Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
        Bone(b, Modifier.padding(start = 20.dp).width(200.dp).height(30.dp))
        Spacer(Modifier.height(20.dp))
        repeat(4) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bone(b, Modifier.weight(1f).height(56.dp))
                Bone(b, Modifier.weight(1f).height(56.dp))
            }
        }
        repeat(2) {
            Bone(b, Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp).width(160.dp).height(22.dp))
            Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) {
                    Column {
                        Bone(b, Modifier.size(150.dp), MaterialTheme.shapes.large)
                        Bone(b, Modifier.padding(top = 8.dp).width(110.dp).height(14.dp))
                        Bone(b, Modifier.padding(top = 6.dp).width(80.dp).height(12.dp))
                    }
                }
            }
        }
    }
}

/** Placeholder rows for lists (library, tracks). */
@Composable
fun ListSkeleton(rows: Int = 8, header: Dp = 0.dp) {
    val b = shimmer()
    Column(Modifier.fillMaxWidth()) {
        if (header > 0.dp) {
            Bone(b, Modifier.padding(top = 24.dp).size(header).align(Alignment.CenterHorizontally), MaterialTheme.shapes.large)
            Bone(b, Modifier.padding(top = 16.dp).width(200.dp).height(24.dp).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
        }
        repeat(rows) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Bone(b, Modifier.size(52.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Bone(b, Modifier.width(180.dp).height(14.dp))
                    Bone(b, Modifier.padding(top = 6.dp).width(110.dp).height(12.dp))
                }
            }
        }
    }
}

/** One list row: optional image, title, subtitle and a trailing note (duration). Shared by pages and Library. */
@Composable
fun MediaRow(
    image: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: String? = null,
    round: Boolean = false,
    size: Int = 48,
    onSubtitleClick: (() -> Unit)? = null
) {
    Row(
        androidx.compose.ui.Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (image != null) RemoteImage(image, Modifier.size(size.dp), if (round) CircleShape else RoundedCornerShape(6.dp))
        Column(Modifier.weight(1f).padding(start = if (image != null) 12.dp else 0.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
    }
}
