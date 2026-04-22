# Privacy

Merckomatic's Fresh Squanch is a standalone personal Spotify client. It is not
affiliated with Spotify.

## Data Stored On Device

The app stores the Spotify Client ID, Spotify session tokens, Spotify profile
ID/display name, playlist IDs/names, track metadata, recommendation history,
sync history, and avoid/preferences data on the Android device.

Spotify access and refresh tokens are stored in encrypted app storage backed by
Android Keystore. App backups are disabled for this app's local data.

## Network Access

The app contacts:

- Spotify Accounts and Spotify Web API for login, playlists, playback, and
  track metadata.
- GitHub Releases when the user taps the in-app Updates button.

The app does not use ads, analytics SDKs, or a developer-operated server.

## User Control

Users can revoke Spotify access from their Spotify account settings. Uninstalling
the app removes its local app data from the device.
