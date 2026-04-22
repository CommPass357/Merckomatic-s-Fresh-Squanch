# Merckomatic's Fresh Squanch Android

Standalone Android app for managing personal Spotify playlists. It is not
affiliated with Spotify. The app talks directly to Spotify from the phone,
stores data locally, and asks each user for their own Spotify Developer Client
ID.

## First Launch

Create a Spotify app in the Spotify Developer Dashboard and add this redirect
URI:

```text
commsfreshsquanch://callback
```

Then paste the app's 32-character Client ID into Fresh Squanch. Do not use or
paste a Spotify Client Secret.

## Debug Build

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Signed Release APK

Android releases are Gradle-only. Do not run `npm run build`, `next build`,
Prisma, or any web task to produce an Android APK.

Create a local signing key once:

```powershell
New-Item -ItemType Directory -Force release
keytool -genkeypair -v -keystore release\comms-fresh-squanch-release.jks -alias comms-fresh-squanch -keyalg RSA -keysize 4096 -validity 10000
```

Copy `keystore.properties.example` to `keystore.properties` and fill in the
local passwords. Both the keystore and `keystore.properties` are ignored.

Build the signed APK:

```powershell
.\gradlew.bat --no-daemon :app:assembleRelease
```

Release APK:

```text
app\build\outputs\apk\release\app-release.apk
```

If `assembleRelease` runs longer than 15 minutes, cancel it and inspect
Gradle/Java processes instead of waiting for hours.

## GitHub Releases

Attach `app-release.apk` and a SHA-256 checksum to each GitHub Release. The
release tag should match the Android `versionName`, and each release must
increment `versionCode`.

Users update manually by installing the newer APK over the old one. The app's
Updates button checks GitHub Releases and opens the release page; it does not
download or install APKs automatically.

See `PRIVACY.md` and `RELEASE_CHECKLIST.md` before publishing.
