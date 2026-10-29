package com.project.lol.ui.nativeui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val imageCache = object : LruCache<String, Bitmap>(24 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

private fun loadBitmap(url: String): Bitmap? {
    imageCache.get(url)?.let { return it }
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
        conn.disconnect()
        if (bmp != null) imageCache.put(url, bmp)
        bmp
    } catch (_: Exception) {
        null
    }
}

@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap by produceState<Bitmap?>(initialValue = url?.let { imageCache.get(it) }, key1 = url) {
        value = if (url.isNullOrEmpty()) null else withContext(Dispatchers.IO) { loadBitmap(url) }
    }
    Box(modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
