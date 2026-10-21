# Tamil TV — Android TV / sideload project

A native Android TV app project for the Tamil IPTV-org playlist. It has a 10-foot AMOLED-dark interface, large channel logo cards, remote-control focus, a left slide-out menu, channel search, channel groups, refresh, and a full-screen Media3/ExoPlayer player with playback controls.

## Build APK in Android Studio
1. Install Android Studio (current stable) and Android SDK Platform 35.
2. Open this `TamilTV-AndroidTV` folder in Android Studio.
3. Allow Gradle sync and dependency downloads.
4. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Sideload to Android TV
Copy `app-debug.apk` to a USB drive or transfer it to the TV, enable installation from unknown apps for the file manager, and install it. It appears as **Tamil TV** in the TV apps list. On a computer with Android platform-tools, use `adb install -r app-debug.apk`.

## Controls
- TV remote D-pad: navigate cards and menu buttons; OK/Enter opens a channel.
- Back: exits full-screen player or closes the drawer.
- Menu: opens the left sidebar. Search filters channel names and group names. Choose a group to filter.
- Refresh playlist: fetches the latest M3U list.
- Player: standard ExoPlayer playback controls; OK/center can show controls and Back returns to channels.

## Notes
- Playlist: https://iptv-org.github.io/iptv/languages/tam.m3u
- Channel availability, logos, and playback depend on the third-party streams. Some providers may block TV playback, geo-restrict streams, or take streams offline.
- This source ZIP is an Android Studio project, not a precompiled APK. Build it using Android Studio to produce the installable APK.
