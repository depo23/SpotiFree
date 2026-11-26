package com.project.lol.ui.materialui

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
import java.io.File
import java.net.HttpURLConnection
import java.security.MessageDigest
import androidx.compose.ui.platform.LocalContext
import java.net.URL

private val imageCache = object : LruCache<String, Bitmap>(24 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

private var diskDir: File? = null
private const val MAX_DISK_FILES = 600
private const val MAX_EDGE = 640

private fun diskFile(url: String): File? {
    val dir = diskDir ?: return null
    val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    return File(dir, hash)
}

/** Decodes at no more than [MAX_EDGE] px on the long side, so big covers don't fill memory. */
private fun decode(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Memory, then disk, then network (saved to disk). */
private fun loadBitmap(url: String): Bitmap? {
    imageCache.get(url)?.let { return it }
    return try {
        val f = diskFile(url)
        val bytes = if (f != null && f.isFile) {
            f.setLastModified(System.currentTimeMillis())
            f.readBytes()
        } else {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val data = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (f != null) {
                f.writeBytes(data)
                f.parentFile?.listFiles()?.takeIf { it.size > MAX_DISK_FILES }
                    ?.sortedBy { it.lastModified() }?.take(100)?.forEach { it.delete() }
            }
            data
        }
        val bmp = decode(bytes)
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
    if (diskDir == null) diskDir = File(LocalContext.current.cacheDir, "material_img").apply { mkdirs() }
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
