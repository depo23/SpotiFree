package com.project.lol.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.project.lol.BuildConfig
import com.project.lol.R
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Progress of an update check, reported on the main thread. */
sealed interface UpdateEvent {
    val version: String? get() = null

    data class Available(override val version: String, val notes: String) : UpdateEvent
    data class Downloading(override val version: String, val percent: Int) : UpdateEvent
    data class Ready(override val version: String, val apk: File, val notes: String) : UpdateEvent
    data object UpToDate : UpdateEvent
    data class Failed(val message: String) : UpdateEvent
}

/**
 * Finds the newest GitHub release of [BuildConfig.UPDATE_REPO], downloads its APK in the
 * background and reports progress. Installing is left to [UpdateInstaller].
 */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "update"
        private const val PREFS_NAME = "spotilol_prefs"
        private const val KEY_LAST_CHECK = "LastUpdateCheck"
        private const val KEY_LATER_VERSION = "UpdateLaterVersion"
        private const val KEY_LATER_AT = "UpdateLaterAt"
        const val KEY_FREQUENCY = "UpdateCheckFrequency"
        const val DEFAULT_FREQUENCY = "day"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Minimum time between automatic checks for a frequency setting; null = never. */
        fun intervalMs(frequency: String): Long? = when (frequency) {
            "week" -> 7 * DAY_MS
            "month" -> 30 * DAY_MS
            "never" -> null
            else -> DAY_MS
        }
        private const val LATER_SNOOZE_MS = 24 * 60 * 60 * 1000L
        private const val APK_SUFFIX = ".apk"

        private val worker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "UpdateChecker").apply { isDaemon = true }
        }
        private val main = Handler(Looper.getMainLooper())
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * [manual] = user asked for a check: always reports the result (including "up to date").
     * Automatic checks run at most once an hour and only surface a new version that is not
     * snoozed; the APK is still downloaded so the install is ready when the user wants it.
     */
    fun check(manual: Boolean, onEvent: (UpdateEvent) -> Unit) {
        val now = System.currentTimeMillis()
        val interval = intervalMs(prefs.getString(KEY_FREQUENCY, DEFAULT_FREQUENCY) ?: DEFAULT_FREQUENCY)
        if (!manual && (interval == null || now - prefs.getLong(KEY_LAST_CHECK, 0L) < interval)) {
            Logger.v(TAG, "auto check skipped (interval=$interval)")
            return
        }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        val repo = BuildConfig.UPDATE_REPO.split('/')
        GitHubApi.fetchLatestRelease(repo[0], repo[1]) { release ->
            if (release == null) {
                Logger.w(TAG, "check failed: no release info")
                if (manual) onEvent(UpdateEvent.Failed(context.getString(R.string.update_check_failed)))
                return@fetchLatestRelease
            }
            val latest = release.tagName.removePrefix("v")
            val installed = installedVersion()
            if (!isNewer(latest, installed)) {
                Logger.i(TAG, "up to date: installed=$installed latest=$latest")
                if (manual) onEvent(UpdateEvent.UpToDate)
                return@fetchLatestRelease
            }
            val apk = release.assets.firstOrNull { it.name.endsWith(APK_SUFFIX) }
            if (apk == null) {
                Logger.w(TAG, "release $latest has no APK asset")
                if (manual) onEvent(UpdateEvent.Failed(context.getString(R.string.update_no_apk)))
                return@fetchLatestRelease
            }

            val snoozed = !manual &&
                prefs.getString(KEY_LATER_VERSION, null) == latest &&
                now - prefs.getLong(KEY_LATER_AT, 0L) < LATER_SNOOZE_MS
            val show: (UpdateEvent) -> Unit = { event -> if (manual || !snoozed) onEvent(event) }

            Logger.s(TAG, "update available: installed=$installed latest=$latest")
            show(UpdateEvent.Available(latest, release.body))
            download(latest, apk, release.body, show)
        }
    }

    /** Hides the automatic popup for [version] for a day. */
    fun snooze(version: String) {
        prefs.edit()
            .putString(KEY_LATER_VERSION, version)
            .putLong(KEY_LATER_AT, System.currentTimeMillis())
            .apply()
    }

    private fun installedVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    private fun download(version: String, asset: ReleaseAsset, notes: String, show: (UpdateEvent) -> Unit) {
        worker.execute {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "Spotilol-$version.apk")
            dir.listFiles()?.forEach { if (it != target) it.delete() }

            if (target.isFile && (asset.size <= 0 || target.length() == asset.size)) {
                Logger.i(TAG, "APK for $version already downloaded")
                main.post { show(UpdateEvent.Ready(version, target, notes)) }
                return@execute
            }

            val part = File(dir, target.name + ".part")
            try {
                val conn = (URL(asset.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                try {
                    val total = if (asset.size > 0) asset.size else conn.contentLengthLong
                    var done = 0L
                    var lastPercent = -1
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                done += read
                                val percent = if (total > 0) (done * 100 / total).toInt() else 0
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    main.post { show(UpdateEvent.Downloading(version, percent)) }
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }

                if (asset.size > 0 && part.length() != asset.size) {
                    throw IOException("size ${part.length()} != ${asset.size}")
                }
                if (!part.renameTo(target)) throw IOException("rename failed")
                Logger.s(TAG, "downloaded $version (${target.length()} bytes)")
                main.post { show(UpdateEvent.Ready(version, target, notes)) }
            } catch (e: Exception) {
                part.delete()
                Logger.w(TAG, "download of $version failed", e)
                main.post { show(UpdateEvent.Failed(context.getString(R.string.update_download_failed))) }
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".")
        val currentParts = current.split(".")
        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
