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
                    null
                }
                conn.disconnect()

                Handler(Looper.getMainLooper()).post { onResult(release) }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { onResult(null) }
            }
        }
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
