OneSignal Gradle Plugin
====================================

This Gradle plugin helps make the [OneSignal Android SDK](https://github.com/OneSignal/OneSignal-Android-SDK) compatible with your Android Studio project. It automatically fixes or notifies you of required changes to make the SDK compatible with your app.

## Setup
1. Add the following to the top of your `app/build.gradle`
```Gradle
plugins {
    id 'com.onesignal.androidsdk.onesignal-gradle-plugin' version '0.8.0'
}
apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'
```
2. Android Studio - Sync gradle
3. Clean and rebuild

## Features
- Automatically aligns dependency versions of modules under the same group. This solves compile and runtime errors due to interdependencies. Applies to the following libraries;
  - `com.google.android.gms`
  - `com.google.firebase`
  - `com.android.support`
- Ensures `com.android.support` is never higher than `compileSdkVersion`.
- Ensures you are using a compatible OneSignal SDK version for the `targetSdkVersion` you're using.
- Future: Other warnings and checks specific to OneSignal such as app_id and notification icons.

## Compatibility
Compatible with Gradle 2.14.1+ and AGP (Android Gradle Plugin) 2.2.3+. Latest tested versions Gradle 4.4 and AGP 3.0.1.

## Change Log
See this repository's [release tags](https://github.com/OneSignal/OneSignal-Gradle-Plugin/releases) for a complete change log.

## Issues
Please create a new issue on this repository's [Github issue tracker](https://github.com/OneSignal/OneSignal-Gradle-Plugin/issues) for feature requests and bug reports related specifically to this plugin.
For other OneSignal issues not related to this plugin please contact OneSignal support from the [OneSignal.com](https://onesignal.com) dashboard.

## Pull Requests
Pull requests are welcome! Please fork, create a new branch, and open a pull request. Also please add a test to `MainTest.groovy` verify your changes.