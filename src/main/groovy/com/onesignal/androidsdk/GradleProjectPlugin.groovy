package com.onesignal.androidsdk

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolveDetails

// This Gradle plugin automatically fixes or notifies developer of requires changes to make the
//    OneSignal Android SDK compatible with the app's project
// - Automatically aligning versions across groups
//   - com.google.android.gms, com.google.firebase, and 'com.android.support'
//     - Mixing versions of these modules leads to compile or runtime errors.
//   - compileSdkVersion is checked to make sure a compatible com.android.support version is used
//   - TODO: Alignment versions should be determined by the project instead of staticly in this plugin.

class GradleProjectPlugin implements Plugin<Project> {

    def versionGroupAligns = [
        // ### Google Play Services library
        'com.google.android.gms': [
            'version': '11.4.+'
        ],

        // ### Google Firebase library
        // Although not used by OneSignal Firebase has some dependencies on gms
        // If present, ensuring they are aligned
        'com.google.firebase': [
            'version': '11.4.+'
        ],

        // ### Android Support Library
        'com.android.support': [
            'requiredCompileSdkVersion': 26,
            'version': '26.1.+',
            'omitModules': ['multidex', 'multidex-instrumentation'],

            // Can't use 26 of com.android.support when compileSdkVersion 25 is set
            // The following error will be thrown if there is a mismatch here.
            // "No resource found that matches the given name: attr 'android:keyboardNavigationCluster'"
            'versionFallback': '25.+'
        ]
    ]

    static String resolveVersion(Project project, Object versionOverride) {
        def curCompileSdkVersion = project.android.compileSdkVersion.split('-')[1].toInteger()
        def requiredCompileSdk = versionOverride['requiredCompileSdkVersion']
        if (curCompileSdkVersion < requiredCompileSdk)
            return versionOverride['versionFallback']
        return versionOverride['version']
    }

    static void overrideVersion(Project project, DependencyResolveDetails details, String resolvedVersion) {
        details.useVersion(resolvedVersion)

        def modName = "${details.requested.group}:${details.requested.name}"
        def versionsMsg = "'${details.requested.version}' to '${resolvedVersion}'"
        def msg = "${modName} overridden from ${versionsMsg}"
        project.logger.warn("OneSignalProjectPlugin: ${msg}");
    }

    @Override
    void apply(Project project) {
        project.configurations.all { resolutionStrategy {
            resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                // Only override groups we define
                def versionOverride = versionGroupAligns[details.requested.group]
                if (!versionOverride)
                    return

                // Skip modules that should not be align to other modules in the group
                def omitModules = versionOverride['omitModules']
                if (omitModules && omitModules.contains(details.requested.name))
                    return

                // Apply version override if versions are not the same
                def resolvedVersion = resolveVersion(project, versionOverride)
                if (details.requested.version != resolvedVersion)
                    overrideVersion(project, details, resolvedVersion)
            }
        }}
    }
}
