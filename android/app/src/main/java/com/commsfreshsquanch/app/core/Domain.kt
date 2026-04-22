package com.commsfreshsquanch.app.core

enum class AgeBucket { FRESH, OLDER }
enum class RecommendationTab { GENRE, SUB_GENRE, TRENDING_SUB_GENRE }
enum class RecommendationAction { SHOWN, ADDED, DISMISSED, REMOVED }
enum class PlaylistProfileTagKind { GENRE, SUB_GENRE, TRENDING_SUB_GENRE }
enum class PlaylistProfileTagState { ASSOCIATE, IGNORE, AVOID }

data class TrackDto(
    val id: String,
    val uri: String,
    val name: String,
    val albumName: String?,
    val artistNames: List<String>,
    val genres: List<String>,
    val releaseDate: String,
    val releasePrecision: String,
    val popularity: Int?,
    val spotifyUrl: String?
)

data class SyncResult(
    val pairId: String,
    val name: String,
    val movedCount: Int,
    val status: String
)

data class ProfileGroups(
    val genre: List<ProfileTagDto> = emptyList(),
    val subGenres: List<ProfileTagDto> = emptyList(),
    val trendingSubGenres: List<ProfileTagDto> = emptyList()
)

data class ProfileTagDto(
    val id: String,
    val playlistPairId: String,
    val kind: PlaylistProfileTagKind,
    val label: String,
    val state: PlaylistProfileTagState,
    val confidence: Double,
    val source: String?
)

data class PlaybackState(
    val title: String = "No track playing",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val status: String = "Player standby"
)
