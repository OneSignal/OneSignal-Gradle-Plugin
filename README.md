OneSignal Gradle Plugin
====================================

This Gradle plugin helps make the [OneSignal Android SDK](https://github.com/OneSignal/OneSignal-Android-SDK) compatible with your Android Studio project. It will automatically fix or notify you of required changes to make the SDK compatible with your app.

## Setup
Add the following to the top of your `app/build.gradle`
```Gradle
plugins {
    id 'com.onesignal.androidsdk.onesignal-gradle-plugin' version '0.5.0'
}
apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'
```

## Features
- Automatic group alignment accross `com.google.android.gms`, `com.google.firebase`, and `com.android.support` dependencies. This will slove compile and runtime errors due to mixing versions bewteen these modules.
- Future: Other warnings and checks OneSignal specific requirements such as app_id and notification icons.