package com.commsfreshsquanch.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.commsfreshsquanch.app.core.AgeBucket
import com.commsfreshsquanch.app.core.PlaylistProfileTagKind
import com.commsfreshsquanch.app.core.PlaylistProfileTagState
import com.commsfreshsquanch.app.core.RecommendationAction
import com.commsfreshsquanch.app.core.RecommendationTab
import kotlinx.coroutines.flow.Flow

@Dao
interface CfsDao {
    @Query("SELECT * FROM SpotifySessionEntity WHERE id = 'current'")
    suspend fun session(): SpotifySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: SpotifySessionEntity)

    @Query("DELETE FROM SpotifySessionEntity")
    suspend fun clearSession()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserProfileEntity)

    @Query("SELECT * FROM UserProfileEntity ORDER BY updatedAt DESC LIMIT 1")
    suspend fun user(): UserProfileEntity?

    @Query("SELECT * FROM PlaylistPairEntity ORDER BY name ASC")
    fun observePairs(): Flow<List<PlaylistPairEntity>>

    @Query("SELECT * FROM PlaylistPairEntity ORDER BY name ASC")
    suspend fun pairs(): List<PlaylistPairEntity>

    @Query("SELECT * FROM PlaylistPairEntity WHERE id = :id")
    suspend fun pair(id: String): PlaylistPairEntity?

    @Query("SELECT * FROM PlaylistPairEntity WHERE spotifyPlaylistId = :spotifyPlaylistId LIMIT 1")
    suspend fun pairBySpotifyId(spotifyPlaylistId: String): PlaylistPairEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePair(pair: PlaylistPairEntity)

    @Query("DELETE FROM PlaylistPairEntity WHERE spotifyPlaylistId NOT IN (:spotifyPlaylistIds)")
    suspend fun deletePairsExcept(spotifyPlaylistIds: List<String>)

    @Query("DELETE FROM PlaylistPairEntity")
    suspend fun deleteAllPairs()

    @Query("UPDATE PlaylistPairEntity SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setPairEnabled(id: String, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM PlaylistPairEntity WHERE enabled = 1 ORDER BY name ASC")
    suspend fun enabledPairs(): List<PlaylistPairEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrack(track: CachedTrackEntity)

    @Query("SELECT * FROM CachedTrackEntity WHERE spotifyTrackId = :spotifyTrackId LIMIT 1")
    suspend fun cachedTrack(spotifyTrackId: String): CachedTrackEntity?

    @Query("SELECT * FROM CachedTrackEntity WHERE spotifyTrackId IN (:ids)")
    suspend fun cachedTracks(ids: List<String>): List<CachedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMembership(membership: PlaylistMembershipEntity)

    @Query("SELECT * FROM PlaylistMembershipEntity WHERE spotifyPlaylistId IN (:playlistIds) ORDER BY lastSeenAt DESC LIMIT :limit")
    suspend fun membershipsForPlaylists(playlistIds: List<String>, limit: Int = 250): List<PlaylistMembershipEntity>

    @Query("SELECT spotifyTrackId FROM PlaylistMembershipEntity WHERE spotifyPlaylistId IN (:playlistIds)")
    suspend fun memberTrackIds(playlistIds: List<String>): List<String>

    @Query("DELETE FROM PlaylistMembershipEntity WHERE spotifyPlaylistId = :playlistId AND spotifyTrackId = :spotifyTrackId")
    suspend fun deleteMembership(playlistId: String, spotifyTrackId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(history: RecommendationHistoryEntity)

    @Query("SELECT spotifyTrackId FROM RecommendationHistoryEntity WHERE action = :action ORDER BY shownAt DESC LIMIT 500")
    suspend fun recentHistoryTrackIds(action: RecommendationAction = RecommendationAction.SHOWN): List<String>

    @Query("SELECT * FROM RecommendationHistoryEntity WHERE playlistPairId = :pairId ORDER BY shownAt DESC LIMIT :limit")
    suspend fun historyForPair(pairId: String, limit: Int = 250): List<RecommendationHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDismissed(track: DismissedTrackEntity)

    @Query("SELECT spotifyTrackId FROM DismissedTrackEntity")
    suspend fun dismissedTrackIds(): List<String>

    @Query("SELECT * FROM DismissedTrackEntity ORDER BY createdAt DESC")
    suspend fun dismissedTracks(): List<DismissedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAvoidSignal(signal: AvoidSignalEntity)

    @Query("SELECT * FROM AvoidSignalEntity WHERE kind = :kind AND `key` = :key LIMIT 1")
    suspend fun avoidSignal(kind: String, key: String): AvoidSignalEntity?

    @Query("SELECT * FROM AvoidSignalEntity WHERE kind = :kind")
    suspend fun avoidSignals(kind: String = "genre"): List<AvoidSignalEntity>

    @Query("SELECT * FROM AvoidSignalEntity ORDER BY weight DESC, `key` ASC")
    suspend fun allAvoidSignals(): List<AvoidSignalEntity>

    @Query("DELETE FROM AvoidSignalEntity WHERE id = :id")
    suspend fun deleteAvoidSignal(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncLog(log: SyncLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfileTag(tag: PlaylistProfileTagEntity)

    @Query("SELECT * FROM PlaylistProfileTagEntity WHERE playlistPairId = :pairId ORDER BY kind ASC, confidence DESC, label ASC")
    suspend fun profileTags(pairId: String): List<PlaylistProfileTagEntity>

    @Query("SELECT * FROM PlaylistProfileTagEntity WHERE playlistPairId = :pairId AND state IN (:states)")
    suspend fun profileTagsByState(pairId: String, states: List<PlaylistProfileTagState>): List<PlaylistProfileTagEntity>

    @Query("UPDATE PlaylistProfileTagEntity SET state = :state, updatedAt = :now WHERE playlistPairId = :pairId AND kind = :kind AND lower(label) = lower(:label)")
    suspend fun updateTagState(pairId: String, kind: PlaylistProfileTagKind, label: String, state: PlaylistProfileTagState, now: Long = System.currentTimeMillis())

    @Query("UPDATE PlaylistProfileTagEntity SET state = 'IGNORE', updatedAt = :now WHERE playlistPairId = :pairId AND kind = 'GENRE' AND lower(label) <> lower(:label)")
    suspend fun ignoreOtherGenres(pairId: String, label: String, now: Long = System.currentTimeMillis())
}
