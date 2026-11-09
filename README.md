# WaterBatteryWallpaper

Android live wallpaper that turns battery percentage into a luminous liquid level. The waterline reacts to phone tilt using the gravity sensor (accelerometer fallback).

## Build
Java 17, Android SDK 35 and Gradle 8.9: `gradle :app:assembleDebug`.

GitHub Actions builds an APK on pushes to main. Open Actions > Build Android APK > successful run > Artifacts > WaterBatteryWallpaper-debug.

## Install
Install the debug APK on Android 8.0+ and open Water Battery Wallpaper, then tap Set live wallpaper.

This is an initial version intended for device testing.
