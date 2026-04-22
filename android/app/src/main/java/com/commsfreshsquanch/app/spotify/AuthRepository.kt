package com.commsfreshsquanch.app.spotify

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.commsfreshsquanch.app.data.CfsDao
import com.commsfreshsquanch.app.data.SpotifySessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

private val scopes = listOf(
    "playlist-read-private",
    "playlist-read-collaborative",
    "playlist-modify-private",
    "playlist-modify-public",
    "user-read-private",
    "streaming",
    "user-read-playback-state",
    "user-modify-playback-state"
)

class AuthRepository(
    private val context: Context,
    private val dao: CfsDao,
    private val client: OkHttpClient,
    private val json: Json
) {
    private val prefs = context.getSharedPreferences("spotify-auth", Context.MODE_PRIVATE)
    private val settings = context.getSharedPreferences("spotify-settings", Context.MODE_PRIVATE)
    private val tokenStore = SecureTokenStore(context)

    fun clientId(): String = settings.getString("spotify_client_id", "").orEmpty()

    fun hasClientId(): Boolean = clientId().matches(Regex("[0-9a-fA-F]{32}"))

    fun saveClientId(value: String) {
        val trimmed = value.trim()
        require(trimmed.matches(Regex("[0-9a-fA-F]{32}"))) {
            "Spotify Client ID should be the 32-character value from your Spotify Developer app."
        }
        settings.edit().putString("spotify_client_id", trimmed).apply()
    }

    fun startLogin() {
        val clientId = clientId()
        require(hasClientId()) { "Paste your Spotify Client ID before connecting." }
        val verifier = randomVerifier()
        val state = UUID.randomUUID().toString()
        prefs.edit().putString("verifier", verifier).putString("state", state).apply()
        val challenge = verifier.challenge()
        val url = Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .path("/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("scope", scopes.joinToString(" "))
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        CustomTabsIntent.Builder().build().launchUrl(context, url)
    }

    suspend fun handleCallback(uri: Uri): SpotifySessionEntity = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code") ?: error("Spotify did not return an auth code.")
        val state = uri.getQueryParameter("state") ?: error("Spotify did not return auth state.")
        check(state == prefs.getString("state", null)) { "Spotify auth state mismatch." }
        val verifier = prefs.getString("verifier", null) ?: error("Missing PKCE verifier.")
        val token = exchangeCode(code, verifier)
        val expiresAt = System.currentTimeMillis() + token.expiresIn * 1000
        tokenStore.save(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken.orEmpty(),
            expiresAt = expiresAt,
            scope = token.scope
        )
        val session = SpotifySessionEntity(
            accessToken = "",
            refreshToken = "",
            expiresAt = expiresAt,
            scope = token.scope
        )
        dao.saveSession(session)
        prefs.edit().clear().apply()
        session
    }

    suspend fun accessToken(): String {
        val session = migrateLegacyTokens() ?: error("Not connected to Spotify.")
        val stored = tokenStore.read() ?: error("Spotify session is missing. Reconnect Spotify.")
        if (stored.expiresAt - System.currentTimeMillis() > 60_000) return stored.accessToken
        if (stored.refreshToken.isBlank()) error("Spotify refresh token is missing. Reconnect Spotify.")
        val refreshed = refresh(stored.refreshToken)
        val expiresAt = System.currentTimeMillis() + refreshed.expiresIn * 1000
        val scope = refreshed.scope.ifBlank { stored.scope }
        tokenStore.save(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken ?: stored.refreshToken,
            expiresAt = expiresAt,
            scope = scope
        )
        val next = session.copy(
            accessToken = "",
            refreshToken = "",
            expiresAt = expiresAt,
            scope = scope,
            updatedAt = System.currentTimeMillis()
        )
        dao.saveSession(next)
        return refreshed.accessToken
    }

    private suspend fun migrateLegacyTokens(): SpotifySessionEntity? {
        val session = dao.session() ?: return null
        if (session.accessToken.isBlank() && session.refreshToken.isBlank()) return session
        tokenStore.save(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAt = session.expiresAt,
            scope = session.scope
        )
        val sanitized = session.copy(accessToken = "", refreshToken = "")
        dao.saveSession(sanitized)
        return sanitized
    }

    private fun exchangeCode(code: String, verifier: String): TokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId())
            .add("code_verifier", verifier)
            .build()
        return tokenRequest(body)
    }

    private fun refresh(refreshToken: String): TokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId())
            .build()
        return tokenRequest(body)
    }

    private fun tokenRequest(body: FormBody): TokenResponse {
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(safeSpotifyAuthError(response.code, text))
            return json.decodeFromString(TokenResponse.serializer(), text)
        }
    }

    private fun safeSpotifyAuthError(code: Int, body: String): String {
        val detail = when {
            "invalid_grant" in body -> "The authorization grant expired or was already used."
            "invalid_client" in body -> "The Spotify Client ID was rejected."
            "invalid_request" in body -> "Spotify rejected the authorization request."
            else -> null
        }
        return when (code) {
            400 -> detail ?: "Spotify could not complete authorization. Check the Client ID and redirect URI."
            401 -> "Spotify authorization failed. Check the Client ID and reconnect."
            403 -> "Spotify denied access. If your Spotify app is in development mode, add this Spotify user to its allowlist."
            429 -> "Spotify rate limit reached. Wait a minute and try again."
            else -> "Spotify authorization failed with status $code."
        }
    }

    private fun randomVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun String.challenge(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    companion object {
        const val REDIRECT_URI = "commsfreshsquanch://callback"
    }
}
