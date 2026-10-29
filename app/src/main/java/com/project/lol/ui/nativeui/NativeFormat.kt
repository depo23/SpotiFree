package com.project.lol.ui.nativeui

import android.icu.text.CompactDecimalFormat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.util.Locale

/** 1363051145 -> "1.4B" (language aware); anything under 1000 stays as it is. */
fun compactCount(raw: String): String {
    val n = raw.filter { it.isDigit() }.toLongOrNull() ?: return raw
    if (n < 1000) return n.toString()
    return try {
        val f = CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT)
        f.maximumFractionDigits = 1
        f.format(n)
    } catch (_: Exception) {
        n.toString()
    }
}

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()

val ShuffleIcon: ImageVector by lazy {
    icon(
        "Shuffle",
        "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5L20,4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20L20,20v-5.5l-2.04,2.04 -3.13,-3.13z"
    )
}

val QueueIcon: ImageVector by lazy {
    icon(
        "Queue",
        "M15,6L3,6v2h12L15,6zM15,10L3,10v2h12v-2zM3,16h8v-2L3,14v2zM17,6v8.18c-0.31,-0.11 -0.65,-0.18 -1,-0.18 -1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3L19,8h3L22,6h-5z"
    )
}

val DownloadIcon: ImageVector by lazy {
    icon("Download", "M19,9h-4L15,3L9,3v6L5,9l7,7 7,-7zM5,18v2h14v-2L5,18z")
}
