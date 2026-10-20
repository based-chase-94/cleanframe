# CleanFrame

A custom home screen and Frame-style art mode for the TCL 65S450F (Fire TV Edition, Fire OS 7.7, Android 9). No root needed.

- **Home**: big app names over full-screen artwork, clock top-left, and Art / All apps / Settings icons top-right. Home only launches apps.
- **All apps**: every installed app. OK opens it; Menu (☰) or holding OK adds it to Home or removes it.
- **Settings → Apps on Home**: a two-column editor. OK picks an app up, Up/Down moves it, OK places it. Right reaches Remove; the app moves to *Recently removed* and focus follows it, so a second OK puts it back where it was. Undo and Done are top-right. Changes save as you go.
- **Art mode**: public-domain paintings from the Art Institute of Chicago, The Met and the Cleveland Museum of Art. It starts after a few idle minutes on Home (or from the picture icon). Left/Right step through works, Up/Down show the label, OK or Back go home. Around 40 works are kept on the TV so it also works offline. Choose full screen or a light or dark mat.
- **Screensaver**: the same art as the system screensaver, so the TV shows paintings even when it goes idle inside another app.
- **Home button**: Fire OS always sends Home to the Amazon launcher. An accessibility service sees the Amazon home screen appear and opens CleanFrame over it right away. There's an optional setting that catches the Home key before the Amazon screen shows; it turns off the long-press Home menu.

Default Home apps are YouTube, Prime Video, WOW Presents Plus, Hulu and Paramount+, picked by name from what's installed.

## Install on the TV

The `tv-debloat/05-install-launcher.sh` script in the project files installs the APK over adb, turns on the Home button service, and sets the screensaver. By hand, it does this:

```sh
adb connect <tv-ip>:5555
adb install -r CleanFrame-1.0.0.apk
# Home button service (append to the existing list rather than replacing it)
adb shell settings put secure enabled_accessibility_services com.cleanframe.launcher/com.cleanframe.launcher.HomeWatcherService
adb shell settings put secure accessibility_enabled 1
# Art as the screensaver
adb shell settings put secure screensaver_components com.cleanframe.launcher/com.cleanframe.launcher.ArtDreamService
adb shell settings put secure screensaver_enabled 1
adb shell am start -n com.cleanframe.launcher/.HomeActivity
```

If Home still lands on the Amazon screen, open CleanFrame **Settings → Home button**. *Last Amazon screen seen* shows the screen name the service saw. Turn on *Leave every Amazon launcher screen* or *Catch Home before Amazon shows* if the home screen isn't caught.

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
| `HomeActivity` | Home screen |
| `AllAppsActivity` | All apps grid |
| `SettingsActivity` | Settings list |
| `HomeEditorActivity` | Apps on Home editor |
| `ArtModeActivity`, `ArtDreamService` | Art mode and screensaver; both use `ArtController` |
| `ArtRepository`, `ArtSources` | Museum APIs, download, resize, offline cache |
| `ArtCanvasView`, `PlaqueView` | Drawing the artwork and its label |
| `HomeWatcherService` | Home button redirect |
| `IdleActivity` | Starts art mode after the idle timeout |

The code uses plain Android framework APIs with no libraries, written as Java 8 without lambdas so the no-Gradle build's `dx` can compile it.
