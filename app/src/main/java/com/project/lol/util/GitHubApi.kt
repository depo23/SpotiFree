package com.project.lol.util

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long
)

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset> = emptyList()
)

object GitHubApi {

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "GitHubApi-Worker").apply { isDaemon = true }
        }

    fun fetchLatestRelease(
        owner: String,
        repo: String,
        onResult: (GitHubRelease?) -> Unit
    ) {
        executor.execute {
            try {
                // /releases/latest skips pre-releases, and fork test builds are all pre-releases,
                // so read the list and take the newest published one.
                val url = URL("https://api.github.com/repos/$owner/$repo/releases?per_page=10")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val release = if (conn.responseCode == 200) {
                    val list = JSONArray(conn.inputStream.bufferedReader().readText())
                    (0 until list.length())
                        .mapNotNull { list.optJSONObject(it) }
                        .firstOrNull { !it.optBoolean("draft", false) }
                        ?.let { json ->
                            GitHubRelease(
                                tagName = json.optString("tag_name", ""),
                                name = json.optString("name", ""),
                                body = json.optString("body", ""),
                                publishedAt = json.optString("published_at", ""),
                                htmlUrl = json.optString("html_url", ""),
                                assets = parseAssets(json.optJSONArray("assets"))
                            )
                        }
                } else {
                    Logger.w("update", "releases api http ${conn.responseCode} (remaining=${conn.getHeaderField("X-RateLimit-Remaining")}), using releases page")
                    null
                }
                conn.disconnect()

                val result = release ?: latestFromPage(owner, repo)
                Handler(Looper.getMainLooper()).post { onResult(result) }
            } catch (e: Exception) {
                Logger.w("update", "releases api failed: ${e.javaClass.simpleName} ${e.message}, using releases page")
                val result = try { latestFromPage(owner, repo) } catch (_: Exception) { null }
                Handler(Looper.getMainLooper()).post { onResult(result) }
            }
        }
    }

    /**
     * Fallback when the API is rate-limited (60 calls/hour per IP without a token): the
     * public /releases/latest page redirects to the newest tag, and the APK has a fixed name.
     */
    private fun latestFromPage(owner: String, repo: String): GitHubRelease? {
        val conn = URL("https://github.com/$owner/$repo/releases/latest").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val location = conn.getHeaderField("Location")
        conn.disconnect()
        val tag = location?.substringAfter("/releases/tag/", "")?.takeIf { it.isNotEmpty() } ?: run {
            Logger.w("update", "releases page gave no tag (http ${conn.responseCode})")
            return null
        }
        val apk = "https://github.com/$owner/$repo/releases/download/$tag/spotifree.apk"
        return GitHubRelease(tag, tag, "", "", location, listOf(ReleaseAsset("spotifree.apk", apk, 0L)))
    }

    private fun parseAssets(arr: JSONArray?): List<ReleaseAsset> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            ReleaseAsset(
                name = a.optString("name", ""),
                url = a.optString("browser_download_url", ""),
                size = a.optLong("size", 0L)
            )
        }
    }
}
