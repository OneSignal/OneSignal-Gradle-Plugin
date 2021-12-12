package com.onesignal.gradleplugin

import org.gradle.api.Project

class AndroidExtensionHelpers {
    static String compileSdkVersion(Project project) {
        project.android.compileSdkVersion
    }
}
