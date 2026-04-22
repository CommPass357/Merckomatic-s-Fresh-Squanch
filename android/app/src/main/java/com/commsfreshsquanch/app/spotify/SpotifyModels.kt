package com.commsfreshsquanch.app.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    val scope: String = ""
)

@Serializable
data class SpotifyUser(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val country: String? = null,
    val product: String? = null
)

@Serializable
data class SpotifyPage<T>(
    val items: List<T> = emptyList(),
    val next: String? = null
)

@Serializable
data class SpotifyOwner(val id: String)

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val owner: SpotifyOwner,
    val collaborative: Boolean = false
)

@Serializable
data class SpotifyPlaylistItem(
    @SerialName("added_at") val addedAt: String? = null,
    val track: SpotifyTrack? = null,
    val item: SpotifyTrack? = null
)

@Serializable
data class SpotifyTrack(
    val id: String? = null,
    val uri: String = "",
    val name: String = "",
    val type: String = "track",
    val explicit: Boolean = false,
    val popularity: Int? = null,
    val album: SpotifyAlbum = SpotifyAlbum(),
    val artists: List<SpotifyArtistRef> = emptyList(),
    @SerialName("external_urls") val externalUrls: Map<String, String> = emptyMap()
)

@Serializable
data class SpotifyAlbum(
    val id: String? = null,
    val name: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("release_date_precision") val releaseDatePrecision: String = "day"
)

@Serializable
data class SpotifyArtistRef(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class SpotifyArtist(
    val id: String,
    val name: String = "",
    val genres: List<String> = emptyList()
)

@Serializable
data class ArtistsResponse(val artists: List<SpotifyArtist> = emptyList())

@Serializable
data class SearchTracksResponse(val tracks: SpotifyPage<SpotifyTrack>)

@Serializable
data class PlaybackResponse(
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("progress_ms") val progressMs: Long? = null,
    val item: SpotifyTrack? = null
)
