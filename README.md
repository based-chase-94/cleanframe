# CleanFrame

A custom home screen and Frame-style art mode for the TCL 65S450F (Fire TV Edition, Fire OS 7.7, Android 9). No root needed.

- **Home**: a scrolling list of up to twelve big app names (five on screen in a block 55% of the screen tall, centred vertically; the edge rows fade out hard when there's more beyond them) in Instrument Serif, each with a white cut-out logo, over full-screen artwork, clock top-left, and Art / All apps / Settings icons top-right. Home only launches apps.
- **All apps**: every installed app. OK opens it; Menu (☰) or holding OK adds it to Home or removes it.
- **Settings → Apps on Home**: a two-column editor. OK picks an app up, Up/Down moves it, OK places it. Right reaches Remove; the app moves to *Recently removed* and focus follows it, so a second OK puts it back where it was. Undo and Done are top-right. Changes save as you go.
- **Weather**: beside the clock, a dot, an icon for the current conditions and the temperature in °F. The TV is located roughly from its internet address (ipwho.is, falling back to ipapi.co and geojs.io; refreshed daily, never shown) and the weather comes from [Open-Meteo](https://open-meteo.com) every 30 minutes. Readings older than 3 hours are hidden. Settings → Weather beside the clock turns it off.
- **Home painting**: a new painting after every restart, every Home press, and whenever the art interval runs out.
- **Art mode**: public-domain paintings from the Art Institute of Chicago, The Met and the Cleveland Museum of Art. It starts after a few idle minutes on Home (or from the picture icon). Left/Right step through works, Up/Down show the label, OK or Back go home. Around 40 works are kept on the TV so it also works offline. Choose full screen or a light or dark mat.
- **Screensaver**: the same art as the system screensaver, so the TV shows paintings even when it goes idle inside another app.
- **Home button**: Fire OS always sends Home to the Amazon launcher. An accessibility service catches the Home key and opens CleanFrame instead; holding Home opens Fire TV settings. If this Fire OS build doesn't pass the Home key on, the service watches for the Amazon home screen, and covers it at once with a working copy of the CleanFrame home screen. Android holds app launches for 5 seconds after Home, so anything chosen on that copy opens when the hold ends.

Default Home apps are YouTube, Prime Video, WOW Presents Plus, Hulu, Paramount+, Tubi and Crunchyroll, picked by name from what's installed. A default app installed later joins Home the first time it's seen, if there's room; once removed in the editor it stays removed.

Logos for YouTube, Prime Video, Hulu, Paramount+, Tubi and Crunchyroll are vector drawables made from [Simple Icons](https://simpleicons.org) (CC0). Any other app's logo is lifted out of its own icon at runtime (`AppLogos`). The font is [Instrument Serif](https://fonts.google.com/specimen/Instrument+Serif) (SIL Open Font License, in `assets/fonts`).

## Install on the TV

The `tv-debloat/05-install-launcher.sh` script in the project files installs the APK over adb, turns on the Home button service, and sets the screensaver. By hand, it does this:

```sh
adb connect <tv-ip>:5555
adb install -r CleanFrame.apk
# Home button service (append to the existing list rather than replacing it)
adb shell settings put secure enabled_accessibility_services com.cleanframe.launcher/com.cleanframe.launcher.HomeWatcherService
adb shell settings put secure accessibility_enabled 1
# Art as the screensaver
adb shell settings put secure screensaver_components com.cleanframe.launcher/com.cleanframe.launcher.ArtDreamService
adb shell settings put secure screensaver_enabled 1
adb shell am start -n com.cleanframe.launcher/.HomeActivity
```

If Home still lands on the Amazon screen, open CleanFrame **Settings → Home button**. *Last Amazon screen seen* shows the screen name the service saw. *Home key last reached CleanFrame* shows whether the key filter works on this TV. `adb logcat -s CleanFrameHome` shows what the service sees when you press Home.

## Build on a Mac

You need JDK 17 and the Android SDK. Installing Android Studio provides both.

```sh
brew install --cask temurin@17 android-studio   # or install them any way you like
export ANDROID_HOME="$HOME/Library/Android/sdk"   # Android Studio's default location
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Every build is signed with the key in `keystore/`, so a Mac build installs over an earlier build with `adb install -r` and keeps the settings. This key is fine for a personal sideloaded app, but don't reuse it for anything published.

`tools/build-without-gradle.sh` builds the same APK with only `aapt`, `javac`, `dx` and `apksigner`, for machines that can't reach Google's Maven repository.

## Layout

| File | What it does |
| --- | --- |
| `HomeActivity`, `HomeScreen` | Home screen; `HomeScreen` is also the Home-button cover |
| `AppLogos` | White logos for the Home list |
| `Weather` | Location lookup, Open-Meteo fetch, condition icons (`wx_*` drawables) |
| `AllAppsActivity` | All apps grid |
| `SettingsActivity` | Settings list |
| `HomeEditorActivity` | Apps on Home editor |
| `ArtModeActivity`, `ArtDreamService` | Art mode and screensaver; both use `ArtController` |
| `ArtRepository`, `ArtSources` | Museum APIs, download, resize, offline cache |
| `ArtCanvasView`, `PlaqueView` | Drawing the artwork and its label |
| `HomeWatcherService` | Home button redirect |
| `IdleActivity` | Starts art mode after the idle timeout |

The code uses plain Android framework APIs with no libraries, written as Java 8 without lambdas so the no-Gradle build's `dx` can compile it.
