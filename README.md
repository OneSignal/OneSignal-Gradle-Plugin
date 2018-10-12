# OneSignal Gradle Plugin

This Gradle plugin helps make the [OneSignal Android SDK](https://github.com/OneSignal/OneSignal-Android-SDK) compatible with your Android Studio / Gradle project. It automatically fixes and notifies you of required changes to make the OneSignal SDK compatible with your app.

## Setup
1. In your root `build.gradle`, under `buildscript`, add the following 2 new lines to your existing `repositories` and `dependencies` sections
```gradle
buildscript {
    repositories {
        // ...
        maven { url 'https://plugins.gradle.org/m2/' } // Gradle Plugin Portal 
    }
    dependencies {
        // ...
        // OneSignal-Gradle-Plugin
        classpath 'gradle.plugin.com.onesignal:onesignal-gradle-plugin:[0.11.2, 0.99.99]'
    }
}
```
2. Add the following to the top of your `app/build.gradle`
```gradle
apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'
```
3. Android Studio - Sync gradle
4. Clean and rebuild

## Features
- Automatically aligns versions of module dependencies under the same group. This fixes compile and runtime errors due to mismatched interdependencies.
Applies to the following libraries:
  - `com.google.android.gms`
  - `com.google.firebase`
  - `com.android.support`
- Ensures `com.android.support` is never higher than `compileSdkVersion`
- Ensures a compatible OneSignal SDK version for the `targetSdkVersion` you're using
- Ensures new enough OneSignal SDK is included when `com.android.support` is upgraded
- Calculates intersecting range of 2 version ranges
   - Including backwards capability with Gradle 2.14.1
- Future: Other warnings and checks specific to OneSignal such as app_id and notification icons

## Compatibility
* Recommend using AGP 3.0.0 or newer ([Android Gradle Plugin](https://developer.android.com/studio/releases/gradle-plugin)) and Gradle 4.1 or newer.
* Compatible with Gradle 2.14.1+ and AGP 2.2.3+
* Tested up to Gradle 4.10.2 and AGP 3.2.1

## Change Log
See this repository's [release tags](https://github.com/OneSignal/OneSignal-Gradle-Plugin/releases) for a complete change log.

## Issues
Please create a new issue on this repository's [Github issue tracker](https://github.com/OneSignal/OneSignal-Gradle-Plugin/issues) for feature requests and bug reports related specifically to this plugin.
For other OneSignal issues not related to this plugin please contact OneSignal support from the [OneSignal.com](https://onesignal.com) dashboard.

## Troubleshooting
You can add `--info` to `./gradlew` commands such as `./gradlew app:dependencies --info` to see log entries of version overrides being applied.

## Pull Requests
Pull requests are welcome! Please fork, create a new branch, and open a pull request. Also please add a test to `MainTest.groovy` verify your changes.
