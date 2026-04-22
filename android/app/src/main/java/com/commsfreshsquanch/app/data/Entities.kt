package com.commsfreshsquanch.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.commsfreshsquanch.app.core.AgeBucket
import com.commsfreshsquanch.app.core.PlaylistProfileTagKind
import com.commsfreshsquanch.app.core.PlaylistProfileTagState
import com.commsfreshsquanch.app.core.RecommendationAction
import com.commsfreshsquanch.app.core.RecommendationTab
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Entity
data class UserProfileEntity(
    @PrimaryKey val id: String = newId(),
    val spotifyId: String,
    val displayName: String?,
    val country: String?,
    val product: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity
data class SpotifySessionEntity(
    @PrimaryKey val id: String = "current",
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val scope: String,
    val userId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(indices = [Index(value = ["spotifyPlaylistId"], unique = true)])
data class PlaylistPairEntity(
    @PrimaryKey val id: String = newId(),
    val spotifyPlaylistId: String,
    val name: String,
    val ownerSpotifyId: String?,
    val enabled: Boolean = false,
    val olderSpotifyPlaylistId: String? = null,
    val olderName: String? = null,
    val lastSyncedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(indices = [Index(value = ["spotifyTrackId"], unique = true)])
data class CachedTrackEntity(
    @PrimaryKey val id: String = newId(),
    val spotifyTrackId: String,
    val uri: String,
    val name: String,
    val albumName: String?,
    val artistNamesJson: String,
    val artistIdsJson: String,
    val genresJson: String,
    val releaseDate: String,
    val releasePrecision: String,
    val popularity: Int?,
    val explicit: Boolean,
    val spotifyUrl: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(primaryKeys = ["spotifyPlaylistId", "spotifyTrackId"])
data class PlaylistMembershipEntity(
    val spotifyPlaylistId: String,
    val spotifyTrackId: String,
    val addedAt: Long?,
    val lastSeenAt: Long = System.currentTimeMillis()
)

@Entity(indices = [Index("spotifyTrackId"), Index("playlistPairId", "mode", "tab")])
data class RecommendationHistoryEntity(
    @PrimaryKey val id: String = newId(),
    val playlistPairId: String?,
    val trackId: String?,
    val spotifyTrackId: String,
    val mode: AgeBucket,
    val tab: RecommendationTab,
    val action: RecommendationAction = RecommendationAction.SHOWN,
    val score: Double? = null,
    val reason: String? = null,
    val shownAt: Long = System.currentTimeMillis()
)

@Entity(indices = [Index(value = ["spotifyTrackId"], unique = true)])
data class DismissedTrackEntity(
    @PrimaryKey val id: String = newId(),
    val spotifyTrackId: String,
    val name: String,
    val artistNamesJson: String,
    val genresJson: String,
    val source: String,
    val reason: String?,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(indices = [Index(value = ["kind", "key"], unique = true)])
data class AvoidSignalEntity(
    @PrimaryKey val id: String = newId(),
    val kind: String,
    val key: String,
    val weight: Double = 1.0,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity
data class SyncLogEntity(
    @PrimaryKey val id: String = newId(),
    val playlistPairId: String?,
    val status: String,
    val message: String,
    val movedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(indices = [Index(value = ["playlistPairId", "kind", "label"], unique = true)])
data class PlaylistProfileTagEntity(
    @PrimaryKey val id: String = newId(),
    val playlistPairId: String,
    val kind: PlaylistProfileTagKind,
    val label: String,
    val state: PlaylistProfileTagState = PlaylistProfileTagState.IGNORE,
    val confidence: Double = 0.0,
    val source: String?,
    val updatedAt: Long = System.currentTimeMillis()
)
