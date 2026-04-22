package com.commsfreshsquanch.app.core

import com.commsfreshsquanch.app.data.AvoidSignalEntity
import com.commsfreshsquanch.app.data.CachedTrackEntity
import com.commsfreshsquanch.app.data.CfsDao
import com.commsfreshsquanch.app.data.DismissedTrackEntity
import com.commsfreshsquanch.app.data.PlaylistMembershipEntity
import com.commsfreshsquanch.app.data.PlaylistPairEntity
import com.commsfreshsquanch.app.data.PlaylistProfileTagEntity
import com.commsfreshsquanch.app.data.RecommendationHistoryEntity
import com.commsfreshsquanch.app.data.SyncLogEntity
import com.commsfreshsquanch.app.data.UserProfileEntity
import com.commsfreshsquanch.app.data.newId
import com.commsfreshsquanch.app.spotify.SpotifyApi
import com.commsfreshsquanch.app.spotify.SpotifyPlaylistItem
import com.commsfreshsquanch.app.spotify.SpotifyTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

private const val OLDER_SUFFIX = " - Older Than 1 Year"

class CfsRepository(
    private val dao: CfsDao,
    private val api: SpotifyApi,
    private val json: Json
) {
    fun observePairs(): Flow<List<PlaylistPairEntity>> = dao.observePairs()

    suspend fun bootstrapUser(): UserProfileEntity {
        val me = api.me()
        val user = UserProfileEntity(
            id = me.id,
            spotifyId = me.id,
            displayName = me.displayName,
            country = me.country,
            product = me.product
        )
        dao.saveUser(user)
        return user
    }

    suspend fun refreshPlaylists(): List<PlaylistPairEntity> {
        val me = api.me()
        val supportedPlaylists = api.playlists()
            .filterNot { it.name.endsWith(OLDER_SUFFIX) }
            .filter { it.owner.id == me.id || it.collaborative }

        if (supportedPlaylists.isEmpty()) {
            dao.deleteAllPairs()
        } else {
            dao.deletePairsExcept(supportedPlaylists.map { it.id })
        }

        supportedPlaylists.forEach { playlist ->
            val existing = dao.pairBySpotifyId(playlist.id)
            dao.savePair(
                existing?.copy(
                    name = playlist.name,
                    ownerSpotifyId = playlist.owner.id,
                    updatedAt = System.currentTimeMillis()
                ) ?: PlaylistPairEntity(
                    spotifyPlaylistId = playlist.id,
                    name = playlist.name,
                    ownerSpotifyId = playlist.owner.id,
                    olderName = playlist.name + OLDER_SUFFIX
                )
            )
        }
        return dao.pairs()
    }

    suspend fun setPairEnabled(id: String, enabled: Boolean) {
        dao.setPairEnabled(id, enabled)
    }

    suspend fun syncEnabledPairs(now: Instant = Instant.now()): List<SyncResult> {
        val results = mutableListOf<SyncResult>()
        for (pair in dao.enabledPairs()) {
            try {
                val olderPlaylistId = ensureOlderPlaylist(pair)
                val items = api.playlistItems(pair.spotifyPlaylistId)
                val olderIds = api.playlistItems(olderPlaylistId).mapNotNull { it.track?.id }.toSet()
                cachePlaylistItems(pair.spotifyPlaylistId, items)
                val validTracks = items.mapNotNull { it.track }.filter { it.id != null && it.uri.isNotBlank() && it.type != "episode" }
                val expired = validTracks.filter { bucketForReleaseDate(it.album.releaseDate, it.album.releaseDatePrecision, now) == AgeBucket.OLDER }
                val toAdd = expired.filter { it.id !in olderIds }.map { it.uri }
                if (toAdd.isNotEmpty()) api.addTracks(olderPlaylistId, toAdd)
                if (expired.isNotEmpty()) api.removeTracks(pair.spotifyPlaylistId, expired.map { it.uri })
                dao.savePair(pair.copy(olderSpotifyPlaylistId = olderPlaylistId, lastSyncedAt = System.currentTimeMillis()))
                refreshPlaylistProfile(pair.id)
                dao.saveSyncLog(
                    SyncLogEntity(
                        playlistPairId = pair.id,
                        status = "OK",
                        message = "Moved ${expired.size} expired track(s).",
                        movedCount = expired.size
                    )
                )
                results += SyncResult(pair.id, pair.name, expired.size, "OK")
            } catch (error: Throwable) {
                dao.saveSyncLog(SyncLogEntity(playlistPairId = pair.id, status = "ERROR", message = error.message ?: "Unknown sync error."))
                results += SyncResult(pair.id, pair.name, 0, "ERROR")
            }
        }
        return results
    }

    suspend fun recommendations(pairId: String?, mode: AgeBucket, tab: RecommendationTab, cursor: Int, now: Instant = Instant.now()): Pair<List<TrackDto>, Int> {
        val seedGenres = seedGenres(pairId)
        val avoidWeights = dao.avoidSignals("genre").associate { it.key to it.weight }
        val excluded = excludedTrackIds(pairId).toMutableSet()
        val ranking = profileRanking(pairId)
        val accepted = mutableListOf<ScoredTrack>()
        var attempt = 0
        while (accepted.size < 5 && attempt < 12) {
            for (query in querySeeds(seedGenres, mode, tab, cursor + attempt, now)) {
                val search = api.searchTracks(query, 20, ((cursor + attempt) * 5) % 200)
                for (track in search.tracks.items) {
                    val id = track.id ?: continue
                    if (id in excluded) continue
                    if (!matchesAgeBucket(track.album.releaseDate, track.album.releaseDatePrecision, mode, now)) continue
                    val score = scoreCandidate(track, seedGenres, avoidWeights, tab) +
                        tagMatchScore(listOf(track.name, track.album.name) + track.artists.map { it.name } + query, ranking.first, ranking.second)
                    accepted += ScoredTrack(track, score, query)
                    excluded += id
                    if (accepted.size >= 5) break
                }
                if (accepted.size >= 5) break
            }
            attempt += 1
        }
        val cached = cacheTracks(accepted.map { it.track })
        val bySpotifyId = cached.associateBy { it.spotifyTrackId }
        accepted.forEach {
            val spotifyId = it.track.id ?: return@forEach
            dao.saveHistory(
                RecommendationHistoryEntity(
                    playlistPairId = pairId,
                    trackId = bySpotifyId[spotifyId]?.id,
                    spotifyTrackId = spotifyId,
                    mode = mode,
                    tab = tab,
                    score = it.score,
                    reason = it.reason
                )
            )
        }
        return accepted.sortedByDescending { it.score }
            .mapNotNull { bySpotifyId[it.track.id] }
            .map { it.toDto() } to cursor + attempt + 1
    }

    suspend fun addRecommendation(spotifyTrackId: String, pairId: String, mode: AgeBucket, tab: RecommendationTab) {
        val pair = dao.pair(pairId) ?: error("Playlist not found.")
        val playlistId = if (mode == AgeBucket.FRESH) pair.spotifyPlaylistId else ensureOlderPlaylist(pair)
        val track = api.track(spotifyTrackId)
        api.addTracks(playlistId, listOf(track.uri))
        val cached = cacheTracks(listOf(track)).first()
        dao.saveMembership(PlaylistMembershipEntity(playlistId, spotifyTrackId, null))
        dao.saveHistory(
            RecommendationHistoryEntity(
                playlistPairId = pairId,
                trackId = cached.id,
                spotifyTrackId = spotifyTrackId,
                mode = mode,
                tab = tab,
                action = RecommendationAction.ADDED
            )
        )
    }

    suspend fun dismissTrack(spotifyTrackId: String, source: String, mode: AgeBucket?, tab: RecommendationTab?, pairId: String?) {
        val cached = dao.cachedTrack(spotifyTrackId) ?: cacheTracks(listOf(api.track(spotifyTrackId))).first()
        val genres = parseList(cached.genresJson)
        dao.saveDismissed(
            DismissedTrackEntity(
                spotifyTrackId = spotifyTrackId,
                name = cached.name,
                artistNamesJson = cached.artistNamesJson,
                genresJson = cached.genresJson,
                source = source,
                reason = if (source == "removed") "Removed from managed playlist" else null
            )
        )
        genres.take(5).forEach { genre ->
            val existing = dao.avoidSignal("genre", genre)
            dao.saveAvoidSignal(
                existing?.copy(weight = existing.weight + 0.35, lastSeenAt = System.currentTimeMillis())
                    ?: AvoidSignalEntity(kind = "genre", key = genre, weight = 0.35)
            )
        }
        if (mode != null && tab != null) {
            dao.saveHistory(
                RecommendationHistoryEntity(
                    playlistPairId = pairId,
                    trackId = cached.id,
                    spotifyTrackId = spotifyTrackId,
                    mode = mode,
                    tab = tab,
                    action = RecommendationAction.DISMISSED
                )
            )
        }
    }

    suspend fun removeTrack(spotifyTrackId: String, pairId: String, mode: AgeBucket, tab: RecommendationTab) {
        val pair = dao.pair(pairId) ?: error("Playlist not found.")
        val playlistId = if (mode == AgeBucket.FRESH) pair.spotifyPlaylistId else pair.olderSpotifyPlaylistId ?: return
        val uri = dao.cachedTrack(spotifyTrackId)?.uri ?: api.track(spotifyTrackId).uri
        api.removeTracks(playlistId, listOf(uri))
        dao.deleteMembership(playlistId, spotifyTrackId)
        dismissTrack(spotifyTrackId, "removed", mode, tab, pairId)
    }

    suspend fun profile(pairId: String): ProfileGroups {
        val tags = dao.profileTags(pairId)
        return ProfileGroups(
            genre = tags.filter { it.kind == PlaylistProfileTagKind.GENRE }.map { it.toDto() },
            subGenres = tags.filter { it.kind == PlaylistProfileTagKind.SUB_GENRE }.map { it.toDto() },
            trendingSubGenres = tags.filter { it.kind == PlaylistProfileTagKind.TRENDING_SUB_GENRE }.map { it.toDto() }
        )
    }

    suspend fun refreshPlaylistProfile(pairId: String): ProfileGroups {
        val pair = dao.pair(pairId) ?: error("Playlist not found.")
        val playlistIds = listOfNotNull(pair.spotifyPlaylistId, pair.olderSpotifyPlaylistId)
        val memberships = dao.membershipsForPlaylists(playlistIds, 250)
        val tracks = dao.cachedTracks(memberships.map { it.spotifyTrackId })
        val history = dao.historyForPair(pairId, 250)
        val genreCounts = mutableMapOf<String, Int>()
        val subGenreCounts = mutableMapOf<String, Int>()
        val trendingCounts = mutableMapOf<String, Int>()
        tracks.forEach { track ->
            parseList(track.genresJson).forEach { genre ->
                subGenreCounts.inc(normalizeLabel(genre), 2)
                genreCounts.inc(coarseGenre(genre), 2)
            }
        }
        history.forEach { item ->
            labelsFromReason(item.reason).forEach { label ->
                trendingCounts.inc(label, 2)
                subGenreCounts.inc(label, 1)
                genreCounts.inc(coarseGenre(label), 1)
            }
        }
        val hasAssociatedGenre = dao.profileTags(pairId).any { it.kind == PlaylistProfileTagKind.GENRE && it.state == PlaylistProfileTagState.ASSOCIATE }
        topCandidates(genreCounts, 6).forEachIndexed { index, candidate ->
            upsertTag(pairId, PlaylistProfileTagKind.GENRE, candidate.label, candidate.confidence, "playlist",
                if (!hasAssociatedGenre && index == 0 && candidate.confidence >= 0.65) PlaylistProfileTagState.ASSOCIATE else PlaylistProfileTagState.IGNORE)
        }
        topCandidates(subGenreCounts, 12).forEach { upsertTag(pairId, PlaylistProfileTagKind.SUB_GENRE, it.label, it.confidence, "playlist", if (it.confidence >= 0.75) PlaylistProfileTagState.ASSOCIATE else PlaylistProfileTagState.IGNORE) }
        topCandidates(trendingCounts, 10).forEach { upsertTag(pairId, PlaylistProfileTagKind.TRENDING_SUB_GENRE, it.label, it.confidence, "spotify-search", if (it.confidence >= 0.85) PlaylistProfileTagState.ASSOCIATE else PlaylistProfileTagState.IGNORE) }
        return profile(pairId)
    }

    suspend fun setProfileTagState(tag: ProfileTagDto, state: PlaylistProfileTagState): ProfileGroups {
        if (tag.kind == PlaylistProfileTagKind.GENRE && state == PlaylistProfileTagState.ASSOCIATE) {
            dao.ignoreOtherGenres(tag.playlistPairId, tag.label)
        }
        upsertTag(tag.playlistPairId, tag.kind, tag.label, 1.0, "user", state)
        dao.updateTagState(tag.playlistPairId, tag.kind, displayLabel(tag.label), state)
        return profile(tag.playlistPairId)
    }

    suspend fun avoidHistory(): Pair<List<AvoidSignalEntity>, List<DismissedTrackEntity>> = dao.allAvoidSignals() to dao.dismissedTracks()
    suspend fun clearAvoidSignal(id: String) = dao.deleteAvoidSignal(id)

    private suspend fun ensureOlderPlaylist(pair: PlaylistPairEntity): String {
        pair.olderSpotifyPlaylistId?.let { return it }
        val targetName = pair.olderName ?: pair.name + OLDER_SUFFIX
        val me = api.me()
        val existing = api.playlists().find { it.name == targetName && it.owner.id == me.id }
        val older = existing ?: api.createPlaylist(me.id, targetName)
        dao.savePair(pair.copy(olderSpotifyPlaylistId = older.id, olderName = targetName))
        return older.id
    }

    private suspend fun cachePlaylistItems(playlistId: String, items: List<SpotifyPlaylistItem>) {
        cacheTracks(items.mapNotNull { it.track }.filter { it.id != null && it.type != "episode" })
        items.forEach { item ->
            val id = item.track?.id ?: return@forEach
            dao.saveMembership(PlaylistMembershipEntity(playlistId, id, item.addedAt?.toInstantMillis()))
        }
    }

    private suspend fun cacheTracks(tracks: List<SpotifyTrack>): List<CachedTrackEntity> {
        if (tracks.isEmpty()) return emptyList()
        val genreMap = api.artists(tracks.flatMap { it.artists.map { artist -> artist.id } }).associate { it.id to it.genres }
        return tracks.mapNotNull { track ->
            val id = track.id ?: return@mapNotNull null
            val genres = track.artists.flatMap { genreMap[it.id].orEmpty() }.map { normalizeLabel(it) }.filter { it.isNotBlank() }.distinct()
            val cached = CachedTrackEntity(
                spotifyTrackId = id,
                uri = track.uri,
                name = track.name,
                albumName = track.album.name,
                artistNamesJson = strings(track.artists.map { it.name }),
                artistIdsJson = strings(track.artists.map { it.id }),
                genresJson = strings(genres),
                releaseDate = track.album.releaseDate,
                releasePrecision = track.album.releaseDatePrecision,
                popularity = track.popularity,
                explicit = track.explicit,
                spotifyUrl = track.externalUrls["spotify"]
            )
            val existing = dao.cachedTrack(id)
            val next = existing?.copy(
                uri = cached.uri,
                name = cached.name,
                albumName = cached.albumName,
                artistNamesJson = cached.artistNamesJson,
                artistIdsJson = cached.artistIdsJson,
                genresJson = cached.genresJson,
                releaseDate = cached.releaseDate,
                releasePrecision = cached.releasePrecision,
                popularity = cached.popularity,
                explicit = cached.explicit,
                spotifyUrl = cached.spotifyUrl,
                updatedAt = System.currentTimeMillis()
            ) ?: cached
            dao.saveTrack(next)
            next
        }
    }

    private suspend fun seedGenres(pairId: String?): List<String> {
        val playlistIds = pairId?.let { id ->
            dao.pair(id)?.let { listOfNotNull(it.spotifyPlaylistId, it.olderSpotifyPlaylistId) }
        }.orEmpty()
        val memberships = if (playlistIds.isNotEmpty()) dao.membershipsForPlaylists(playlistIds, 100) else emptyList()
        val counts = mutableMapOf<String, Int>()
        dao.cachedTracks(memberships.map { it.spotifyTrackId }).forEach { track ->
            parseList(track.genresJson).forEach { counts.inc(it, 1) }
        }
        profileRanking(pairId).first.forEach { counts.inc(it, 4) }
        return counts.entries.sortedByDescending { it.value }.map { it.key }.take(12)
    }

    private suspend fun excludedTrackIds(pairId: String?): Set<String> {
        val playlistIds = pairId?.let { id -> dao.pair(id)?.let { listOfNotNull(it.spotifyPlaylistId, it.olderSpotifyPlaylistId) } }.orEmpty()
        return (if (playlistIds.isNotEmpty()) dao.memberTrackIds(playlistIds) else emptyList()).toSet() +
            dao.dismissedTrackIds() +
            dao.recentHistoryTrackIds()
    }

    private suspend fun profileRanking(pairId: String?): Pair<List<String>, List<String>> {
        if (pairId == null) return emptyList<String>() to emptyList()
        val tags = dao.profileTagsByState(pairId, listOf(PlaylistProfileTagState.ASSOCIATE, PlaylistProfileTagState.AVOID))
        return tags.filter { it.state == PlaylistProfileTagState.ASSOCIATE }.map { normalizeLabel(it.label) } to
            tags.filter { it.state == PlaylistProfileTagState.AVOID }.map { normalizeLabel(it.label) }
    }

    private suspend fun upsertTag(pairId: String, kind: PlaylistProfileTagKind, label: String, confidence: Double, source: String, defaultState: PlaylistProfileTagState) {
        val display = displayLabel(label)
        val existing = dao.profileTags(pairId).find { it.kind == kind && it.label.equals(display, true) }
        dao.saveProfileTag(
            existing?.copy(confidence = max(existing.confidence, confidence), source = source, updatedAt = System.currentTimeMillis())
                ?: PlaylistProfileTagEntity(playlistPairId = pairId, kind = kind, label = display, state = defaultState, confidence = confidence, source = source)
        )
    }

    private fun CachedTrackEntity.toDto() = TrackDto(
        id = spotifyTrackId,
        uri = uri,
        name = name,
        albumName = albumName,
        artistNames = parseList(artistNamesJson),
        genres = parseList(genresJson),
        releaseDate = releaseDate,
        releasePrecision = releasePrecision,
        popularity = popularity,
        spotifyUrl = spotifyUrl
    )

    private fun PlaylistProfileTagEntity.toDto() = ProfileTagDto(id, playlistPairId, kind, label, state, confidence, source)
    private fun strings(value: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), value)
    private fun parseList(value: String): List<String> = runCatching { json.decodeFromString(ListSerializer(String.serializer()), value) }.getOrElse { emptyList() }
}

private data class ScoredTrack(val track: SpotifyTrack, val score: Double, val reason: String)
private data class Candidate(val label: String, val confidence: Double)

private val fallbackGenres = listOf("indie pop", "alternative rock", "hip hop", "electronic", "r&b", "dance", "soul", "house", "folk", "jazz")

private fun scoreCandidate(track: SpotifyTrack, seedGenres: List<String>, avoidGenres: Map<String, Double>, tab: RecommendationTab): Double {
    val artistGenreHits = if (seedGenres.isNotEmpty()) 8.0 else 0.0
    val popularity = min(30.0, max(0.0, (track.popularity ?: 0).toDouble() / 3.0))
    val explicitPenalty = if (track.explicit) 1.5 else 0.0
    val tabBoost = when (tab) {
        RecommendationTab.TRENDING_SUB_GENRE -> 7.0
        RecommendationTab.SUB_GENRE -> 4.0
        RecommendationTab.GENRE -> 2.0
    }
    val avoidPenalty = seedGenres.sumOf { avoidGenres[it] ?: 0.0 }
    return artistGenreHits + popularity + tabBoost - explicitPenalty - avoidPenalty
}

private fun querySeeds(genres: List<String>, mode: AgeBucket, tab: RecommendationTab, cursor: Int, now: Instant): List<String> {
    val pool = if (genres.isNotEmpty()) genres else fallbackGenres
    val years = yearHintsForBucket(mode, now)
    val genre = pool[cursor % pool.size]
    val year = years[(cursor / pool.size) % years.size]
    return when (tab) {
        RecommendationTab.GENRE -> {
            val broad = genre.split(" ").lastOrNull().orEmpty().ifBlank { genre }
            listOf("""genre:"$broad" year:$year""", "$broad year:$year")
        }
        RecommendationTab.SUB_GENRE -> listOf("""genre:"$genre" year:$year""", """"$genre" year:$year""")
        RecommendationTab.TRENDING_SUB_GENRE -> listOf(""""$genre" tag:new""", """genre:"$genre" year:$year""", "$genre $year")
    }
}

private fun normalizeLabel(label: String): String {
    val normalized = label.trim().lowercase().replace(Regex("\\s+"), " ")
    return if (normalized == "hop") "hip hop" else normalized
}

private fun displayLabel(label: String): String {
    val normalized = normalizeLabel(label)
    return when (normalized) {
        "hip hop" -> "Hip Hop"
        "r&b" -> "R&B"
        "edm" -> "EDM"
        else -> normalized.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}

private fun coarseGenre(label: String): String {
    val value = normalizeLabel(label)
    return when {
        "metal" in value -> "metal"
        "rock" in value -> "rock"
        "hip hop" in value || "rap" in value -> "hip hop"
        "pop" in value -> "pop"
        "house" in value || "techno" in value || "edm" in value || "electronic" in value || "dance" in value -> "electronic"
        "r&b" in value || "soul" in value -> "r&b"
        "country" in value -> "country"
        "jazz" in value -> "jazz"
        "folk" in value -> "folk"
        "punk" in value -> "punk"
        "latin" in value -> "latin"
        else -> value.split(" ").lastOrNull().orEmpty()
    }
}

private fun MutableMap<String, Int>.inc(label: String, amount: Int) {
    val normalized = normalizeLabel(label)
    if (normalized.length < 2 || normalized.matches(Regex("^\\d{2,4}s?$")) || normalized in listOf("fpv", "playlist", "fresh", "older", "year")) return
    this[normalized] = (this[normalized] ?: 0) + amount
}

private fun labelsFromReason(reason: String?): List<String> {
    if (reason.isNullOrBlank()) return emptyList()
    val labels = mutableSetOf<String>()
    Regex("genre:\"([^\"]+)\"", RegexOption.IGNORE_CASE).findAll(reason).forEach { labels += it.groupValues[1] }
    Regex("\"([^\"]+)\"\\s+tag:new", RegexOption.IGNORE_CASE).findAll(reason).forEach { labels += it.groupValues[1] }
    val yearless = reason.replace(Regex("year:\\d{4}", RegexOption.IGNORE_CASE), "").replace("tag:new", "").replace("\"", "").trim()
    if (yearless.isNotBlank() && ":" !in yearless) labels += yearless
    return labels.map(::normalizeLabel).filter { it.isNotBlank() }
}

private fun topCandidates(counts: Map<String, Int>, limit: Int): List<Candidate> {
    val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { Candidate(it.key, min(1.0, it.value.toDouble() / maxCount.toDouble())) }
}

private fun tagMatchScore(texts: List<String>, associated: List<String>, avoided: List<String>): Double {
    val haystack = texts.joinToString(" ") { normalizeLabel(it) }
    val assocHits = associated.count { it in haystack }
    val avoidHits = avoided.count { it in haystack }
    return assocHits * 24.0 - avoidHits * 22.0
}

private fun String.toInstantMillis(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
