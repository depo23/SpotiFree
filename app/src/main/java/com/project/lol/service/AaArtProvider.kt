package com.project.lol.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Serves browse-tree artwork to Android Auto as content:// URIs. The car UI
 * can't load remote https images, so the host app proxies them through here
 * and keeps a disk copy. Only Spotify CDN hosts are fetched.
 */
class AaArtProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        val remote = uri.getQueryParameter("u") ?: throw FileNotFoundException()
        if (!isAllowed(remote)) throw FileNotFoundException("host not allowed")
        val dir = File(ctx.cacheDir, "aa_art").apply { mkdirs() }
        val file = File(dir, sha1(remote))
        if (!file.exists() || file.length() == 0L) download(remote, file)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun download(remote: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            val conn = URL(remote).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() == 0L || !tmp.renameTo(target)) throw FileNotFoundException()
        } catch (e: Exception) {
            tmp.delete()
            throw FileNotFoundException(e.message)
        }
        prune(target.parentFile)
    }

    private fun prune(dir: File?) {
        val files = dir?.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    override fun getType(uri: Uri): String? = "image/*"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        private const val MAX_FILES = 400

        private fun authority(ctx: Context) = "${ctx.packageName}.aaart"

        private fun isAllowed(url: String): Boolean {
            val u = try { Uri.parse(url) } catch (_: Exception) { return false }
            val host = u.host ?: return false
            return u.scheme == "https" &&
                (host == "scdn.co" || host.endsWith(".scdn.co") ||
                    host == "spotifycdn.com" || host.endsWith(".spotifycdn.com"))
        }

        private fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
                .joinToString("") { "%02x".format(it) }

        /** content:// URI for an allowed remote image, else the original URI. */
        fun uriFor(ctx: Context?, image: String): Uri {
            if (ctx == null || !isAllowed(image)) return Uri.parse(image)
            return Uri.Builder()
                .scheme("content")
                .authority(authority(ctx))
                .appendPath("art")
                .appendQueryParameter("u", image)
                .build()
        }
    }
}
