package com.commsfreshsquanch.app.spotify

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class StoredSpotifyTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val scope: String
)

class SecureTokenStore(context: Context) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "spotify-secure-auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun read(): StoredSpotifyTokens? {
        val accessToken = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() } ?: return null
        val refreshToken = prefs.getString("refresh_token", null).orEmpty()
        val expiresAt = prefs.getLong("expires_at", 0L)
        val scope = prefs.getString("scope", "").orEmpty()
        return StoredSpotifyTokens(accessToken, refreshToken, expiresAt, scope)
    }

    fun save(accessToken: String, refreshToken: String, expiresAt: Long, scope: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", expiresAt)
            .putString("scope", scope)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
