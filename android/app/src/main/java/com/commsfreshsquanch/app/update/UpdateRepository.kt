package com.commsfreshsquanch.app.update

import com.commsfreshsquanch.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateCheckResult(
    val message: String,
    val releaseUrl: String? = null
)

class UpdateRepository(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun checkLatestRelease(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val owner = BuildConfig.GITHUB_OWNER.trim()
        val repo = BuildConfig.GITHUB_REPO.trim()
        if (owner.isBlank() || repo.isBlank()) {
            return@withContext UpdateCheckResult("GitHub release checking is not configured for this build.")
        }

        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CommsFreshSquanchAndroid/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext UpdateCheckResult("Could not check GitHub Releases right now.")
            }

            val release = runCatching { json.decodeFromString(GitHubRelease.serializer(), text) }
                .getOrElse { return@withContext UpdateCheckResult("GitHub release information was not readable.") }
            val hasApk = release.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
            val current = BuildConfig.VERSION_NAME

            if (isNewer(release.tagName, current)) {
                val apkNote = if (hasApk) "" else " No APK asset was found on the latest release."
                UpdateCheckResult("Update ${release.tagName} is available.$apkNote", release.htmlUrl)
            } else {
                UpdateCheckResult("Fresh Squanch is up to date ($current).", release.htmlUrl)
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val max = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until max) {
            val next = latestParts.getOrElse(index) { 0 }
            val now = currentParts.getOrElse(index) { 0 }
            if (next > now) return true
            if (next < now) return false
        }
        return false
    }

    private fun versionParts(value: String): List<Int> =
        value.trim()
            .removePrefix("v")
            .split(".", "-", "_")
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String
)
