package com.commsfreshsquanch.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.commsfreshsquanch.app.CfsApplication
import com.commsfreshsquanch.app.core.AgeBucket
import com.commsfreshsquanch.app.core.CfsRepository
import com.commsfreshsquanch.app.core.PlaybackState
import com.commsfreshsquanch.app.core.PlaylistProfileTagState
import com.commsfreshsquanch.app.core.ProfileGroups
import com.commsfreshsquanch.app.core.ProfileTagDto
import com.commsfreshsquanch.app.core.RecommendationTab
import com.commsfreshsquanch.app.core.SyncResult
import com.commsfreshsquanch.app.core.TrackDto
import com.commsfreshsquanch.app.data.AvoidSignalEntity
import com.commsfreshsquanch.app.data.DismissedTrackEntity
import com.commsfreshsquanch.app.data.PlaylistPairEntity
import com.commsfreshsquanch.app.spotify.AuthRepository
import com.commsfreshsquanch.app.spotify.SpotifyApi
import com.commsfreshsquanch.app.update.UpdateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

data class CfsUiState(
    val loading: Boolean = true,
    val connected: Boolean = false,
    val setupConfigured: Boolean = false,
    val spotifyClientId: String = "",
    val spotifyRedirectUri: String = AuthRepository.REDIRECT_URI,
    val busy: Boolean = false,
    val userName: String = "",
    val product: String? = null,
    val pairs: List<PlaylistPairEntity> = emptyList(),
    val selectedPairId: String = "",
    val playback: PlaybackState = PlaybackState(),
    val mode: AgeBucket = AgeBucket.FRESH,
    val tab: RecommendationTab = RecommendationTab.GENRE,
    val addTargetMode: AgeBucket = AgeBucket.FRESH,
    val tracks: List<TrackDto> = emptyList(),
    val cursor: Int = 0,
    val profile: ProfileGroups = ProfileGroups(),
    val signals: List<AvoidSignalEntity> = emptyList(),
    val dismissed: List<DismissedTrackEntity> = emptyList(),
    val syncResults: List<SyncResult> = emptyList(),
    val updateUrl: String? = null,
    val message: String? = null
)

class CfsViewModel(app: CfsApplication) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = OkHttpClient()
    private val dao = app.database.dao()
    private val auth = AuthRepository(app, dao, http, json)
    private val api = SpotifyApi(http, json) { auth.accessToken() }
    private val repo = CfsRepository(dao, api, json)
    private val updates = UpdateRepository(http, json)
    private val _state = MutableStateFlow(CfsUiState())
    val state: StateFlow<CfsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observePairs().collect { pairs ->
                _state.update { current ->
                    val selected = current.selectedPairId.ifBlank {
                        pairs.firstOrNull { it.enabled }?.id ?: pairs.firstOrNull()?.id.orEmpty()
                    }
                    current.copy(pairs = pairs, selectedPairId = selected)
                }
                loadProfile()
            }
        }
        viewModelScope.launch { load() }
    }

    fun startLogin() {
        runBusy { auth.startLogin() }
    }

    fun saveSpotifyClientId(value: String) {
        runBusy("Saving Spotify setup...") {
            auth.saveClientId(value)
            _state.update { it.copy(setupConfigured = true, spotifyClientId = auth.clientId(), message = "Spotify setup saved.") }
        }
    }

    fun handleSpotifyCallback(uri: Uri) {
        viewModelScope.launch {
            setBusy("Connecting Spotify...")
            runCatching {
                auth.handleCallback(uri)
                load()
            }.onFailure { showError(it) }
            clearBusy()
        }
    }

    fun refreshPlaylists() = runBusy("Refreshing playlists...") {
        repo.refreshPlaylists()
        loadProfile()
    }

    fun togglePair(pair: PlaylistPairEntity) = runBusy {
        repo.setPairEnabled(pair.id, !pair.enabled)
        if (!pair.enabled) selectPair(pair.id)
    }

    fun selectPair(id: String) {
        _state.update { it.copy(selectedPairId = id, tracks = emptyList(), cursor = 0, message = null) }
        viewModelScope.launch { loadProfile() }
    }

    fun setMode(mode: AgeBucket) {
        _state.update { it.copy(mode = mode, tracks = emptyList(), cursor = 0, message = null) }
    }

    fun setTab(tab: RecommendationTab) {
        _state.update { it.copy(tab = tab, tracks = emptyList(), cursor = 0, message = null) }
    }

    fun setAddTarget(mode: AgeBucket) {
        _state.update { it.copy(addTargetMode = mode) }
    }

    fun sync() = runBusy("Syncing enabled playlists...") {
        val results = repo.syncEnabledPairs()
        _state.update { it.copy(syncResults = results, message = "Sync complete.") }
        loadProfile()
    }

    fun loadRecommendations(reset: Boolean = false) = runBusy("Loading recommendations...") {
        val current = state.value
        val pairId = current.selectedPairId.ifBlank { null }
        val cursor = if (reset) 0 else current.cursor
        val (items, nextCursor) = repo.recommendations(pairId, current.mode, current.tab, cursor)
        _state.update {
            it.copy(
                tracks = if (reset) items else it.tracks + items,
                cursor = nextCursor,
                message = if (items.isEmpty()) "No matching tracks found. Try another tab or sync playlists first." else null
            )
        }
    }

    fun playTrack(track: TrackDto) = runBusy {
        api.play(track.uri)
        pollPlaybackOnce()
    }

    fun addTrack(track: TrackDto) = runBusy("Adding track...") {
        val current = state.value
        repo.addRecommendation(track.id, current.selectedPairId, current.addTargetMode, current.tab)
        _state.update { it.copy(tracks = it.tracks.filterNot { item -> item.id == track.id }, message = "Added \"${track.name}\".") }
    }

    fun dismissTrack(track: TrackDto) = runBusy {
        val current = state.value
        repo.dismissTrack(track.id, "recommendation", current.mode, current.tab, current.selectedPairId)
        _state.update { it.copy(tracks = it.tracks.filterNot { item -> item.id == track.id }) }
        loadAvoidHistory()
    }

    fun removeTrack(track: TrackDto) = runBusy("Removing track...") {
        val current = state.value
        repo.removeTrack(track.id, current.selectedPairId, current.mode, current.tab)
        _state.update { it.copy(tracks = it.tracks.filterNot { item -> item.id == track.id }) }
        loadAvoidHistory()
    }

    fun refreshProfile() = runBusy("Refreshing profile...") {
        state.value.selectedPairId.takeIf { it.isNotBlank() }?.let {
            val profile = repo.refreshPlaylistProfile(it)
            _state.update { current -> current.copy(profile = profile, message = "Profile refreshed.") }
        }
    }

    fun setTagState(tag: ProfileTagDto, state: PlaylistProfileTagState) = runBusy {
        val profile = repo.setProfileTagState(tag, state)
        _state.update { it.copy(profile = profile) }
    }

    fun clearAvoid(id: String) = runBusy {
        repo.clearAvoidSignal(id)
        loadAvoidHistory()
    }

    fun checkForUpdates() = runBusy("Checking GitHub Releases...") {
        val result = updates.checkLatestRelease()
        _state.update { it.copy(message = result.message, updateUrl = result.releaseUrl) }
    }

    fun togglePlayback() = runBusy {
        if (state.value.playback.isPlaying) api.pause() else api.resume()
        pollPlaybackOnce()
    }

    fun seekBy(seconds: Long) = runBusy {
        val playback = state.value.playback
        val next = (playback.positionMs + seconds * 1000).coerceIn(0, playback.durationMs.coerceAtLeast(Long.MAX_VALUE / 2))
        api.seek(next)
        pollPlaybackOnce()
    }

    private suspend fun load() {
        _state.update { it.copy(loading = true, message = null) }
        runCatching {
            if (dao.session() == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        connected = false,
                        setupConfigured = auth.hasClientId(),
                        spotifyClientId = auth.clientId()
                    )
                }
                return
            }
            val user = repo.bootstrapUser()
            repo.refreshPlaylists()
            loadAvoidHistory()
            _state.update {
                it.copy(
                    loading = false,
                    connected = true,
                    setupConfigured = auth.hasClientId(),
                    spotifyClientId = auth.clientId(),
                    userName = user.displayName ?: user.spotifyId,
                    product = user.product,
                    message = null
                )
            }
            startPlaybackPolling()
        }.onFailure {
            _state.update {
                current -> current.copy(
                    loading = false,
                    connected = dao.session() != null,
                    setupConfigured = auth.hasClientId(),
                    spotifyClientId = auth.clientId()
                )
            }
            showError(it)
        }
    }

    private fun startPlaybackPolling() {
        viewModelScope.launch {
            while (state.value.connected) {
                pollPlaybackOnce()
                delay(5000)
            }
        }
    }

    private suspend fun pollPlaybackOnce() {
        val playback = runCatching { api.playback() }.getOrNull()
        _state.update {
            if (playback?.item == null) {
                it.copy(playback = it.playback.copy(status = "Open Spotify and start playback."))
            } else {
                it.copy(
                    playback = PlaybackState(
                        title = playback.item.name,
                        artist = playback.item.artists.joinToString(", ") { artist -> artist.name },
                        isPlaying = playback.isPlaying,
                        positionMs = playback.progressMs ?: 0,
                        durationMs = 0,
                        status = if (playback.isPlaying) "Playing on Spotify" else "Paused on Spotify"
                    )
                )
            }
        }
    }

    private suspend fun loadProfile() {
        val id = state.value.selectedPairId
        if (id.isBlank()) return
        val profile = runCatching { repo.profile(id).takeIf { it.genre.isNotEmpty() || it.subGenres.isNotEmpty() || it.trendingSubGenres.isNotEmpty() } ?: repo.refreshPlaylistProfile(id) }.getOrNull()
        if (profile != null) _state.update { it.copy(profile = profile) }
    }

    private suspend fun loadAvoidHistory() {
        val (signals, dismissed) = repo.avoidHistory()
        _state.update { it.copy(signals = signals, dismissed = dismissed) }
    }

    private fun runBusy(message: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            setBusy(message)
            runCatching { block() }.onFailure { showError(it) }
            clearBusy()
        }
    }

    private fun setBusy(message: String?) {
        _state.update { it.copy(busy = true, message = message) }
    }

    private fun clearBusy() {
        _state.update { it.copy(busy = false) }
    }

    private fun showError(error: Throwable) {
        _state.update { it.copy(message = error.message ?: "Something went wrong.") }
    }
}

class CfsViewModelFactory(private val app: CfsApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CfsViewModel(app) as T
}
