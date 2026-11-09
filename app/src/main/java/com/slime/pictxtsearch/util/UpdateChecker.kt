package com.slime.pictxtsearch.util

import android.content.Context
import com.slime.pictxtsearch.R
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface UpdateResult {
    data class Available(val version: String, val downloadUrl: String) : UpdateResult
    data object Latest : UpdateResult
    data class Error(val message: String) : UpdateResult
}

object UpdateChecker {

    private const val RELEASE_API =
        "https://api.github.com/repos/SlimeABC/PicTxtSearch/releases/latest"
    private const val APK_NAME = "PicTxtSearch.apk"

    suspend fun check(context: Context, currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PicTxtSearch")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateResult.Error(
                    context.getString(R.string.update_error_http, connection.responseCode),
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            val latestVersion = tag.trim().removePrefix("v").removePrefix("V")
            if (latestVersion.isEmpty()) {
                return@withContext UpdateResult.Error(
                    context.getString(R.string.update_error_no_version),
                )
            }
            if (!isNewer(latestVersion, currentVersion)) {
                return@withContext UpdateResult.Latest
            }

            val fallbackUrl = json.optString("html_url")
            var downloadUrl = fallbackUrl
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    if (asset.optString("name").equals(APK_NAME, ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", fallbackUrl)
                        break
                    }
                }
            }
            UpdateResult.Available(latestVersion, downloadUrl)
        } catch (error: Exception) {
            UpdateResult.Error(context.getString(R.string.update_error_network))
        } finally {
            connection?.disconnect()
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val left = versionParts(latest)
        val right = versionParts(current)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val latestPart = left.getOrElse(index) { 0 }
            val currentPart = right.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version
            .removePrefix("v")
            .removePrefix("V")
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
