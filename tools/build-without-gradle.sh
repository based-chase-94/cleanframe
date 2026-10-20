#!/usr/bin/env bash
# Builds the APK with the bare Android tools (aapt, javac, dx, apksigner) instead of Gradle.
# Used where Google's Maven repo is unreachable. On a Mac, prefer ./gradlew assembleRelease.
#
#   ANDROID_JAR=/path/to/android-28/android.jar tools/build-without-gradle.sh
set -euo pipefail
cd "$(dirname "$0")/.."

ANDROID_JAR=${ANDROID_JAR:?set ANDROID_JAR to an API 28 android.jar}
DX=${DX:-$(command -v dalvik-exchange || command -v dx)}
OUT=out
VERSION=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)
VERSION_CODE=$(sed -n "s/.*versionCode \([0-9]*\).*/\1/p" app/build.gradle)
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

aapt package -f -m \
  -M app/src/main/AndroidManifest.xml -S app/src/main/res -I "$ANDROID_JAR" \
  -J "$OUT/gen" -F "$OUT/unsigned.apk" \
  --min-sdk-version 25 --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION"

javac -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find app/src/main/java "$OUT/gen" -name '*.java')

"$DX" --dex --min-sdk-version=25 --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && zip -q unsigned.apk classes.dex)

zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
apksigner sign --ks keystore/cleanframe-debug.jks --ks-pass pass:cleanframe --key-pass pass:cleanframe \
  --ks-key-alias cleanframe --out "$OUT/CleanFrame-$VERSION.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/CleanFrame-$VERSION.apk"
echo "Built $OUT/CleanFrame-$VERSION.apk"
