# Android Release Checklist

## Public Info Sweep

- Confirm `.env`, databases, logs, tunnel files, local scripts, local SDKs,
  signing configs, and keystores are not included in the public repo.
- Confirm no personal email, phone number, address, private path, token, API
  key, or Spotify Client Secret is committed.
- Confirm `android/local.properties`, `android/keystore.properties`, and
  `*.jks` are ignored.

## Versioning

- Increment `versionCode` in `android/app/build.gradle.kts`.
- Set `versionName` to match the GitHub Release tag, for example `1.0.1`.
- Build the release APK:

```powershell
.\gradlew.bat --no-daemon :app:assembleRelease
```

## Build

Use Android Gradle only:

```powershell
cd android
.\gradlew.bat --no-daemon :app:assembleRelease
```

Do not run `npm run build`, `next build`, Prisma, or any web task.

If the release build exceeds 15 minutes, cancel and inspect Gradle/Java
processes.

## Verify

- Install `app\build\outputs\apk\release\app-release.apk` on a device.
- Connect with a fresh Spotify Client ID using PKCE.
- Sync an owned playlist.
- Confirm followed/non-owned playlists are not offered for sync.
- Tap Updates and confirm GitHub behavior.
- Install a newer signed APK over the old APK and confirm app data survives.

## Publish

- Attach `app-release.apk` to the GitHub Release.
- Attach a SHA-256 checksum:

```powershell
Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

- Include short release notes and manual install/update instructions.
