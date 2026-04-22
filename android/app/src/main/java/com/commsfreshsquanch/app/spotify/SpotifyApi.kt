package com.commsfreshsquanch.app.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class SpotifyApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val tokenProvider: suspend () -> String
) {
    private val jsonType = "application/json".toMediaType()

    suspend fun me(): SpotifyUser = get("/me", SpotifyUser.serializer())
    suspend fun playlists(): List<SpotifyPlaylist> = pages("/me/playlists?limit=50", SpotifyPlaylist.serializer())
    suspend fun playlistItems(playlistId: String): List<SpotifyPlaylistItem> {
        val fields = "items(added_at,item(id,uri,name,type,explicit,popularity,album(id,name,release_date,release_date_precision),artists(id,name),external_urls)),next"
        return pages("/playlists/$playlistId/items?limit=50&fields=${fields.enc()}", SpotifyPlaylistItem.serializer())
            .map { item -> item.copy(track = item.track ?: item.item) }
    }

    suspend fun createPlaylist(userId: String, name: String): SpotifyPlaylist {
        return post(
            "/me/playlists",
            """{"name":${name.q()},"public":false,"description":"Managed by Merckomatic's Fresh Squanch."}""",
            SpotifyPlaylist.serializer()
        )
    }

    suspend fun addTracks(playlistId: String, uris: List<String>) {
        uris.chunked(100).forEach { chunk ->
            postUnit("/playlists/$playlistId/items", """{"uris":[${chunk.joinToString(",") { it.q() }}]}""")
        }
    }

    suspend fun removeTracks(playlistId: String, uris: List<String>) {
        uris.chunked(100).forEach { chunk ->
            requestUnit(
                "DELETE",
                "/playlists/$playlistId/items",
                """{"items":[${chunk.joinToString(",") { """{"uri":${it.q()}}""" }}]}"""
            )
        }
    }

    suspend fun searchTracks(query: String, limit: Int = 20, offset: Int = 0): SearchTracksResponse {
        val safeLimit = limit.coerceIn(1, 10)
        return get("/search?type=track&q=${query.enc()}&limit=$safeLimit&offset=$offset", SearchTracksResponse.serializer())
    }

    suspend fun artists(ids: List<String>): List<SpotifyArtist> {
        val unique = ids.filter { it.isNotBlank() }.distinct()
        return unique.map { id ->
            get("/artists/${id.enc()}", SpotifyArtist.serializer())
        }
    }

    suspend fun track(id: String): SpotifyTrack = get("/tracks/$id", SpotifyTrack.serializer())

    suspend fun playback(): PlaybackResponse? {
        return getNullable("/me/player", PlaybackResponse.serializer())
    }

    suspend fun pause() = requestUnit("PUT", "/me/player/pause", null, allow404 = true)
    suspend fun resume() = requestUnit("PUT", "/me/player/play", "{}", allow404 = true)
    suspend fun play(uri: String) = requestUnit("PUT", "/me/player/play", """{"uris":[${uri.q()}]}""", allow404 = true)
    suspend fun seek(positionMs: Long) = requestUnit("PUT", "/me/player/seek?position_ms=$positionMs", null, allow404 = true)

    private suspend fun <T> pages(firstPath: String, serializer: KSerializer<T>): List<T> {
        val items = mutableListOf<T>()
        var next: String? = firstPath
        while (next != null) {
            val page = getPage(next, serializer)
            items += page.items
            next = page.next
        }
        return items
    }

    private suspend fun <T> getPage(pathOrUrl: String, serializer: KSerializer<T>): SpotifyPage<T> {
        return request("GET", pathOrUrl, null, SpotifyPage.serializer(serializer))
    }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>): T = request("GET", path, null, serializer)

    private suspend fun <T> getNullable(path: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        val response = raw("GET", path, null)
        response.use {
            if (it.code == 204 || it.code == 404) return@withContext null
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) error(safeSpotifyApiError(it.code))
            json.decodeFromString(serializer, text)
        }
    }

    private suspend fun <T> post(path: String, body: String, serializer: KSerializer<T>): T = request("POST", path, body, serializer)
    private suspend fun postUnit(path: String, body: String) = requestUnit("POST", path, body)

    private suspend fun <T> request(method: String, pathOrUrl: String, body: String?, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        raw(method, pathOrUrl, body).use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(safeSpotifyApiError(response.code))
            json.decodeFromString(serializer, text)
        }
    }

    private suspend fun requestUnit(method: String, pathOrUrl: String, body: String?, allow404: Boolean = false) = withContext(Dispatchers.IO) {
        raw(method, pathOrUrl, body).use { response ->
            if (!response.isSuccessful && !(allow404 && response.code == 404)) {
                error(safeSpotifyApiError(response.code))
            }
        }
    }

    private suspend fun raw(method: String, pathOrUrl: String, body: String?): okhttp3.Response {
        val url = if (pathOrUrl.startsWith("https://")) pathOrUrl else "https://api.spotify.com/v1$pathOrUrl"
        val requestBody = body?.toRequestBody(jsonType)
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${tokenProvider()}")
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(jsonType))
            "PUT" -> builder.put(requestBody ?: ByteArray(0).toRequestBody(jsonType))
            "DELETE" -> builder.delete(requestBody ?: ByteArray(0).toRequestBody(jsonType))
        }
        return client.newCall(builder.build()).execute()
    }

    private fun safeSpotifyApiError(code: Int): String = when (code) {
        401 -> "Spotify session expired. Reconnect Spotify and try again."
        403 -> "Spotify denied access. Use playlists you own or collaborate on, and check your Spotify developer allowlist."
        429 -> "Spotify rate limit reached. Wait a minute and try again."
        else -> "Spotify request failed with status $code."
    }

    private fun String.enc(): String = URLEncoder.encode(this, "UTF-8")
    private fun String.q(): String = json.encodeToString(String.serializer(), this)
}
