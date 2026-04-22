package com.commsfreshsquanch.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commsfreshsquanch.app.core.AgeBucket
import com.commsfreshsquanch.app.core.PlaybackState
import com.commsfreshsquanch.app.core.PlaylistProfileTagState
import com.commsfreshsquanch.app.core.ProfileTagDto
import com.commsfreshsquanch.app.core.RecommendationTab
import com.commsfreshsquanch.app.core.TrackDto
import com.commsfreshsquanch.app.data.AvoidSignalEntity
import com.commsfreshsquanch.app.data.DismissedTrackEntity
import com.commsfreshsquanch.app.data.PlaylistPairEntity

private val Bg = Color(0xFF101611)
private val Panel = Color(0xFF1A211B)
private val Line = Color(0xFF344035)
private val TextMain = Color(0xFFF6F0E8)
private val Muted = Color(0xFFB8BCAE)
private val Green = Color(0xFF1ED760)
private val Gold = Color(0xFFF0C96A)
private val Blue = Color(0xFF7BB4FF)
private val Ink = Color(0xFF071009)
private const val SPOTIFY_DASHBOARD_URL = "https://developer.spotify.com/dashboard"

@Composable
fun CfsApp(viewModel: CfsViewModel) {
    val state by viewModel.state.collectAsState()
    MaterialTheme {
        Surface(color = Bg, contentColor = TextMain, modifier = Modifier.fillMaxSize()) {
            if (state.loading) LoadingScreen()
            else if (!state.connected) ConnectScreen(state, viewModel::saveSpotifyClientId, viewModel::startLogin)
            else MainScreen(state, viewModel)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Green)
            Text("Loading Merckomatic's Fresh Squanch", color = Muted)
        }
    }
}

@Composable
private fun ConnectScreen(state: CfsUiState, onSaveClientId: (String) -> Unit, onConnect: () -> Unit) {
    var clientId by remember(state.spotifyClientId) { mutableStateOf(state.spotifyClientId) }
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Merckomatic's Fresh Squanch",
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 28.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("Minimum requirements", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("1. Spotify Premium account\n2. Spotify Developer app Client ID", color = Muted)
        Text("Instructions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedButton(onClick = { uriHandler.openUri(SPOTIFY_DASHBOARD_URL) }) {
            Text("Create API Key")
        }
        Text("Copy and paste the Client ID only. Do not paste a Client Secret into this Android app.", color = Muted)
        Text("Redirect URI", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        InfoCard(state.spotifyRedirectUri, "Add this exact value in the Spotify Developer Dashboard.")
        Text("Detailed Instructions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        SetupStep("1. Log into your Spotify Developer Dashboard using your Spotify account.")
        OutlinedButton(onClick = { uriHandler.openUri(SPOTIFY_DASHBOARD_URL) }) {
            Text("Open Spotify Dashboard")
        }
        SetupStep(
            "2. Click Create app at top right.\n" +
                "App name: Merckomatic's Fresh Squanch\n" +
                "App description: Endless Fresh Squanch\n" +
                "Website: https://github.com/CommPass357/Merckomatic-s-Fresh-Squanch\n" +
                "Redirect URI: ${state.spotifyRedirectUri}\n" +
                "API/SDK checkbox: Android\n" +
                "Android package: com.commsfreshsquanch.app\n" +
                "Release SHA-1: E7:65:FF:D4:F8:00:23:09:79:20:A3:9A:74:5B:CA:42:33:1E:0F:7F"
        )
        Text("Add up to 5 Spotify users", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        SetupStep("1. Go to the User Management tab in your Spotify Developer Dashboard.\n2. Add the name and email of each person Spotify should invite.")
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it.trim() },
            label = { Text("Spotify Client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onSaveClientId(clientId) }, enabled = !state.busy, colors = ButtonDefaults.buttonColors(containerColor = TextMain, contentColor = Ink), shape = RoundedCornerShape(8.dp)) {
            Text("Save Setup", fontWeight = FontWeight.Bold)
        }
        Button(onClick = onConnect, enabled = state.setupConfigured && !state.busy, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Ink), shape = RoundedCornerShape(8.dp)) {
            Text("Connect Spotify", fontWeight = FontWeight.Bold)
        }
        if (!state.message.isNullOrBlank()) {
            Notice(state.message)
        }
    }
}

@Composable
private fun SetupStep(text: String) {
    Text(text, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(state: CfsUiState, vm: CfsViewModel) {
    val pages = listOf("Playlists", "Recommendations", "Playlist Profile", "Avoid History")
    val pager = rememberPagerState(pageCount = { pages.size })
    val uriHandler = LocalUriHandler.current
    Scaffold(containerColor = Bg, topBar = {
        Column(Modifier.background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Merckomatic's Fresh Squanch", fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = vm::checkForUpdates, enabled = !state.busy) { Text("Updates") }
            }
            Text("Signed in as ${state.userName}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Spotify data stays on this device. Update checks contact GitHub.", color = Muted, fontSize = 11.sp, maxLines = 2)
            if (!state.updateUrl.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { uriHandler.openUri(state.updateUrl.orEmpty()) }) { Text("Open GitHub Release") }
            }
            Spacer(Modifier.height(8.dp))
            PlayerCard(state.playback, state.busy, vm::togglePlayback, { vm.seekBy(-15) }, { vm.seekBy(15) })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                pages.forEachIndexed { index, label ->
                    val active = pager.currentPage == index
                    Text(
                        label,
                        color = if (active) Green else Muted,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.message.isNullOrBlank()) Notice(state.message, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> PlaylistsPage(state, vm)
                    1 -> RecommendationsPage(state, vm)
                    2 -> ProfilePage(state, vm)
                    3 -> AvoidPage(state, vm)
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(playback: PlaybackState, busy: Boolean, onPlay: () -> Unit, onBack: () -> Unit, onForward: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(playback.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playback.artist.ifBlank { playback.status }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MiniButton("-15", busy, onBack)
                Spacer(Modifier.width(12.dp))
                Button(onClick = onPlay, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = TextMain, contentColor = Ink), shape = RoundedCornerShape(999.dp), modifier = Modifier.size(46.dp)) {
                    Text(if (playback.isPlaying) "II" else ">")
                }
                Spacer(Modifier.width(12.dp))
                MiniButton("+15", busy, onForward)
            }
        }
    }
}

@Composable
private fun PlaylistsPage(state: CfsUiState, vm: CfsViewModel) {
    Page {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TitleBlock("Playlists", "Enable Spotify playlists this app should manage.")
                OutlinedButton(onClick = vm::refreshPlaylists, enabled = !state.busy) { Text("Refresh") }
            }
        }
        if (state.pairs.isEmpty()) Empty("No playlists found yet. Refresh after connecting Spotify.")
        items(state.pairs) { pair ->
            PairRow(pair, selected = pair.id == state.selectedPairId, onSelect = { vm.selectPair(pair.id) }, onToggle = { vm.togglePair(pair) })
        }
        item {
            Button(
                onClick = vm::sync,
                enabled = !state.busy && state.pairs.any { it.enabled },
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Ink),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Sync Enabled", fontWeight = FontWeight.Bold) }
        }
        items(state.syncResults) { result ->
            InfoCard(result.name, "${result.status}: moved ${result.movedCount}")
        }
    }
}

@Composable
private fun RecommendationsPage(state: CfsUiState, vm: CfsViewModel) {
    Page {
        item { TitleBlock("Recommendations", "Five at a time, filtered by release age and avoid signals.") }
        item {
            SegmentRow(listOf("Fresh" to AgeBucket.FRESH, "Older" to AgeBucket.OLDER), state.mode, vm::setMode)
            Spacer(Modifier.height(8.dp))
            SegmentRow(
                listOf("Genre" to RecommendationTab.GENRE, "Sub-Genre" to RecommendationTab.SUB_GENRE, "Trending" to RecommendationTab.TRENDING_SUB_GENRE),
                state.tab,
                vm::setTab
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.loadRecommendations(state.tracks.isEmpty()) }, enabled = !state.busy && state.selectedPairId.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Ink), shape = RoundedCornerShape(8.dp)) {
                    Text("Load 5", fontWeight = FontWeight.Bold)
                }
                SegmentRow(listOf("Add Fresh" to AgeBucket.FRESH, "Add Older" to AgeBucket.OLDER), state.addTargetMode, vm::setAddTarget, Modifier.weight(1f))
            }
        }
        if (state.tracks.isEmpty()) Empty("Choose a mode and tab, then load a five-song batch.")
        items(state.tracks) { track -> TrackCard(track, state.busy, vm::playTrack, vm::addTrack, vm::dismissTrack, vm::removeTrack) }
    }
}

@Composable
private fun ProfilePage(state: CfsUiState, vm: CfsViewModel) {
    Page {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TitleBlock("Playlist Profile", "Associate, ignore, or avoid tags for the selected playlist.")
                OutlinedButton(onClick = vm::refreshProfile, enabled = !state.busy && state.selectedPairId.isNotBlank()) { Text("Refresh") }
            }
        }
        TagSection("Current playlist Genre", state.profile.genre, state.busy, vm)
        TagSection("Current playlist Sub-Genres", state.profile.subGenres, state.busy, vm)
        TagSection("Trending Sub-Genres", state.profile.trendingSubGenres, state.busy, vm)
    }
}

@Composable
private fun AvoidPage(state: CfsUiState, vm: CfsViewModel) {
    Page {
        item { TitleBlock("Avoid History", "Dismissed and removed music nudges future batches.") }
        item { Text("Sub-Genres", fontWeight = FontWeight.Bold, fontSize = 19.sp) }
        if (state.signals.isEmpty()) Empty("No avoid signals yet.")
        items(state.signals) { signal -> SignalRow(signal, vm::clearAvoid) }
        item { Text("Dismissed / Removed", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 10.dp)) }
        if (state.dismissed.isEmpty()) Empty("Dismissed music will show up here.")
        items(state.dismissed) { track -> DismissedRow(track) }
    }
}

@Composable
private fun Page(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.Empty(text: String) {
    item { EmptyCard(text) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.TagSection(title: String, tags: List<ProfileTagDto>, busy: Boolean, vm: CfsViewModel) {
    item { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
    if (tags.isEmpty()) item { EmptyCard("Select a playlist first or refresh profile.") }
    items(tags) { tag -> TagRow(tag, busy) { vm.setTagState(tag, it) } }
}

@Composable
private fun PairRow(pair: PlaylistPairEntity, selected: Boolean, onSelect: () -> Unit, onToggle: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF203B28) else Panel), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pair.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pair.olderName ?: "Older playlist will be created on sync.", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = onSelect, modifier = Modifier.padding(top = 6.dp)) { Text("Use") }
            }
            Switch(checked = pair.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun TrackCard(track: TrackDto, busy: Boolean, onPlay: (TrackDto) -> Unit, onAdd: (TrackDto) -> Unit, onDismiss: (TrackDto) -> Unit, onRemove: (TrackDto) -> Unit) {
    val uriHandler = LocalUriHandler.current
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(track.name, fontWeight = FontWeight.Bold)
            Text(track.artistNames.joinToString(", "), color = Muted, fontSize = 12.sp)
            Text("${track.albumName.orEmpty()} - ${track.releaseDate}", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniButton("Play", busy) { onPlay(track) }
                MiniButton("Add", busy) { onAdd(track) }
                MiniButton("Skip", busy) { onDismiss(track) }
                MiniButton("Remove", busy) { onRemove(track) }
                track.spotifyUrl?.takeIf { it.isNotBlank() }?.let { spotifyUrl ->
                    MiniButton("Spotify", busy = false) { uriHandler.openUri(spotifyUrl) }
                }
            }
        }
    }
}

@Composable
private fun TagRow(tag: ProfileTagDto, busy: Boolean, onState: (PlaylistProfileTagState) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tag.label, fontWeight = FontWeight.Bold)
            Text("confidence ${(tag.confidence * 100).toInt()}%${tag.source?.let { " - $it" }.orEmpty()}", color = Muted, fontSize = 12.sp)
            SegmentRow(
                listOf("Avoid" to PlaylistProfileTagState.AVOID, "Ignore" to PlaylistProfileTagState.IGNORE, "Associate" to PlaylistProfileTagState.ASSOCIATE),
                tag.state,
                onState,
                enabled = !busy
            )
        }
    }
}

@Composable
private fun SignalRow(signal: AvoidSignalEntity, onClear: (String) -> Unit) {
    InfoCard(signal.key, "weight ${"%.2f".format(signal.weight)}") {
        OutlinedButton(onClick = { onClear(signal.id) }) { Text("Clear") }
    }
}

@Composable
private fun DismissedRow(track: DismissedTrackEntity) {
    InfoCard(track.name, track.source)
}

@Composable
private fun InfoCard(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun TitleBlock(title: String, subtitle: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Notice(text: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF221B18)), shape = RoundedCornerShape(8.dp), modifier = modifier.fillMaxWidth()) {
        Text(text, color = Gold, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141A15)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Muted, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun MiniButton(label: String, busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !busy, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)) {
        Text(label)
    }
}

@Composable
private fun <T> SegmentRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, value) ->
            val active = value == selected
            Button(
                onClick = { onSelect(value) },
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = if (active) Green else Panel, contentColor = if (active) Ink else TextMain),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
