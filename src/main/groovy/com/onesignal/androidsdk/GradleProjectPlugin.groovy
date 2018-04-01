package com.onesignal.androidsdk

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.VersionInfo
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector

// This Gradle plugin automatically fixes or notifies developer of requires changes to make the
//    OneSignal Android SDK compatible with the app's project
// - Automatically aligning versions across groups
//   - com.google.android.gms, com.google.firebase, and 'com.android.support'
//     - Mixing versions of these modules leads to compile or runtime errors.
//   - compileSdkVersion is checked to make sure a compatible com.android.support version is used

// References
// - Source of Android Gradle Plugin (com.android.application)
//   https://android.googlesource.com/platform/tools/build/+/oreo-release/gradle/src/main/groovy/com/android/build/gradle/BasePlugin.groovy


// Notes on new 3.0.0 android gradle plugin way to do resolutionStrategy
// https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#new_configurations
// Instead, because the new build model delays dependency resolution, you
// should query and modify the resolution strategy using the Variant API:
//    android {
//        applicationVariants.all { variant ->
//            variant.getCompileConfiguration().resolutionStrategy {
//                ...
//            }
//            variant.runtimeConfiguration.resolutionStrategy {
//                ...
//            }
//            variant.getAnnotationProcessorConfiguration().resolutionStrategy {
//                ...
//            }
//        }
//    }


class GradleProjectPlugin implements Plugin<Project> {

    static final def VERSION_GROUP_ALIGNS = [
        // ### Google Play Services library
        'com.google.android.gms': [
            'version': '0.0.0'
        ],

        // ### Google Firebase library
        // Although not used by OneSignal Firebase has some dependencies on gms
        // If present, ensuring they are aligned
        'com.google.firebase': [
            'version': '0.0.0'
        ],

        // ### Android Support Library
        'com.android.support': [
            'version': '0.0.0',
            'omitModules': ['multidex', 'multidex-instrumentation'],

            // Can't use 26 of com.android.support when compileSdkVersion 25 is set
            // The following error will be thrown if there is a mismatch here.
            // "No resource found that matches the given name: attr 'android:keyboardNavigationCluster'"
            'compileSdkVersionAlign': true
        ]
    ]

    static final def MINIMUM_MODULE_VERSIONS = [
        'com.onesignal:OneSignal': [
            targetSdkVersion: [
                26: '3.6.3'
            ]
        ]
    ]

    static def versionGroupAligns
    static Project project
    static def moduleVersionOverrides
    static def moduleCopied

    @Override
    void apply(Project inProject) {
        project = inProject
        versionGroupAligns = InternalUtils.deepcopy(VERSION_GROUP_ALIGNS)
        moduleVersionOverrides = [:]
        moduleCopied = [:]

        resolutionHooksForAndroidPluginV3()
        resolutionHooksForAndroidPluginV2()
    }

    static void resolutionHooksForAndroidPluginV3() {
        project.afterEvaluate {
            projectVariants().all { variant ->
                // compileConfiguration is new in 3.0.0
                if (!variant.hasProperty('compileConfiguration'))
                    return

                def configuration = variant.compileConfiguration
                doResolutionStrategyAndroidPluginV3(configuration)
            }
        }
    }

    static void resolutionHooksForAndroidPluginV2() {
        project.configurations.all { configuration ->
            project.afterEvaluate {
                if (isAndroidPluginV3())
                    return
                generateHighestVersionsForGroups(configuration)
                doResolutionStrategyAndroidPluginV2(configuration)
            }

            // Catches Android specific tasks, <buildType>CompileClasspath
            project.dependencies {
                doResolutionStrategyAndroidPluginV2(configuration)
            }
        }
    }

    static void doResolutionStrategyAndroidPluginV2(Object configuration) {
        // The Android 3.3 plugin resolves this before we can
        // Skip it in this case to prevent a build error
        def configName = configuration.name
        if (configName.endsWith('WearApp') || configName.endsWith('wearApp'))
            return

        configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            // The Android 2.14.1 plugin doesn't work with doTargetSdkVersionAlign
            //   We are not able to get the targetSDK version in this case.

            // Once doTargetSdkVersionAlign is fixed this will loop with compileCopy
            //  At this point we can skip this by checking for ending 'Copy' in the config name
            // generateHighestVersionsForGroups(configuration)

            doGroupAlignStrategyOnDetail(details)
        }
    }

    static void doResolutionStrategyAndroidPluginV3(Object configuration) {
        configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            projectVariants().all { variant ->
                doTargetSdkVersionAlign(details)

                project.configurations.all { config ->
                    generateHighestVersionsForGroups(config)
                }

                doGroupAlignStrategyOnDetail(details)
            }
        }
    }

    // Get either applicationVariants or libraryVariants depending on project type
    // returns BaseVariant
    static Object projectVariants() {
        if (project.android.hasProperty('applicationVariants'))
            project.android.applicationVariants
        else
            project.android.libraryVariants
    }

    // Each variant is created from this internal Android Gradle plugin method
    // private createTasksForFlavoredBuild(ProductFlavorData... flavorDataList) {
    // https://stackoverflow.com/questions/31461267/using-a-different-manifestplaceholder-for-each-build-variant
    static int getCurrentTargetSdkVersion() {
        def targetSdkVersion = 0
        projectVariants().all { variant ->
            def mergedFlavor = variant.getMergedFlavor()
            // Use targetSdkVersion unless null, fallback is minSdkVersion, 1 the static fallback
            if (mergedFlavor.targetSdkVersion != null)
                targetSdkVersion = mergedFlavor.targetSdkVersion.apiLevel
            else if ( mergedFlavor.minSdkVersion != null)
                targetSdkVersion = mergedFlavor.minSdkVersion.apiLevel
            else
                targetSdkVersion = 1
        }

        targetSdkVersion
    }

    static void doTargetSdkVersionAlign(DependencyResolveDetails details) {
        def existingOverrider = moduleVersionOverrides["${details.requested.group}:${details.requested.name}"]
        if (existingOverrider)
            details.useVersion(existingOverrider)

        def module = MINIMUM_MODULE_VERSIONS["${details.requested.group}:${details.requested.name}"]
        if (!module)
            return

        projectVariants().all { variant ->
            def curSdkVersion = getCurrentTargetSdkVersion()

            if (curSdkVersion == 0)
                return


            def curVersion = getVersionFromDependencyResolveDetails(details)
            def newVersion = null
            module['targetSdkVersion'].each { key, value ->
                if (curSdkVersion < key)
                    return

                def compareVersionResult = compareVersions(value, newVersion ?: curVersion)
                if (compareVersionResult > 0)
                    newVersion = value
            }

            if (newVersion != curVersion && newVersion != null) {
                moduleVersionOverrides["${details.requested.group}:${details.requested.name}"] = newVersion
                versionGroupAligns[details.requested.group] = [version: newVersion]
                details.useVersion(newVersion)
            }
        }
    }

    static void doGroupAlignStrategyOnDetail(DependencyResolveDetails details) {
        if (!inGroupAlignList(details))
            return

        def toVersion = finalAlignmentRules()[details.requested.group]['version']
        overrideVersion(details, toVersion)
    }

    static void compileSdkVersionAlign(Project project, def versionOverride) {
        if (!versionOverride['compileSdkVersionAlign'])
            return

        def compileSdkVersion = project.android.compileSdkVersion.split('-')[1].toInteger()
        def libraryGroupVersion = versionOverride['version'].split('\\.').first().toInteger()
        if (compileSdkVersion < libraryGroupVersion)
            versionOverride['version'] = "${compileSdkVersion}.+"
    }

    static void overrideVersion(DependencyResolveDetails details, String resolvedVersion) {
        if (resolvedVersion == '0.0.0')
            return

        if (details.requested.version == resolvedVersion)
            return

        details.useVersion(resolvedVersion)
        logModuleOverride(details, resolvedVersion)
    }

    static void logModuleOverride(DependencyResolveDetails details, String resolvedVersion) {
        def modName = "${details.requested.group}:${details.requested.name}"
        def versionsMsg = "'${details.requested.version}' to '${resolvedVersion}'"
        def msg = "${modName} overridden from ${versionsMsg}"
        project.logger.info("OneSignalProjectPlugin: ${msg}")
    }

    static boolean inGroupAlignList(details) {
        // Only override groups we define
        def versionOverride = versionGroupAligns[details.requested.group]
        if (!versionOverride)
            return false

        // Skip modules that should not align to other modules in the group
        def omitModules = versionOverride['omitModules']
        if (omitModules && omitModules.contains(details.requested.name))
            return false
        true
    }

    static String getHighestVersion(String version1, String version2) {
        compareVersions(version1, version2) > 0 ? version1 : version2
    }

    static String moduleGroupAndName(details) {
        "${details.requested.group}:${details.requested.name}"
    }

    static int compareVersions(String version1, String version2) {
        def versionComparator = new DefaultVersionComparator()
        versionComparator.compare(new VersionInfo(version1), new VersionInfo(version2))
    }

    static Object finalAlignmentRules() {
        project.logger.debug("OneSignalProjectPlugin: FINAL ALIGN PART 1: ${versionGroupAligns}")

        def finalVersionGroupAligns = InternalUtils.deepcopy(versionGroupAligns)
        alignAcrossGroups(finalVersionGroupAligns)
        updateMockVersionsIntoGradleVersions(finalVersionGroupAligns)

        project.logger.debug("OneSignalProjectPlugin: FINAL ALIGN PART 2: ${finalVersionGroupAligns}")
        finalVersionGroupAligns
    }

    static void alignAcrossGroups(def versionGroupAligns) {
        def highestVersion = getHighestVersion(
            versionGroupAligns['com.google.android.gms']['version'],
            versionGroupAligns['com.google.firebase']['version']
        )

        versionGroupAligns['com.google.android.gms']['version'] = highestVersion
        versionGroupAligns['com.google.firebase']['version'] = highestVersion
    }

    static void updateMockVersionsIntoGradleVersions(def finalVersionGroupAligns) {
        finalVersionGroupAligns.each { group ->
            compileSdkVersionAlign(project, group.value)

            String version = group.value['version']

            // Mock latest into Gradle latest
            if (version == '9999.9999.9999')
                version = '+'

            group.value['version'] = version.replace('9999', '+').replace(".+.+", ".+")
        }
    }

    // project.android.@plugin - This looks to be on the AppExtension class however this didn't work
    // Found 'enforceUniquePackageName' by comparing project.android.properties between versions
    static boolean isAndroidPluginV3() {
        !project.android.hasProperty('enforceUniquePackageName')
    }

    static void forceCanBeResolved(def configuration) {
        // canBeResolved not available on Gradle 2.14.1 and older
        if (configuration.hasProperty('canBeResolved'))
            configuration.canBeResolved = true
    }

    static void generateHighestVersionsForGroups(def configuration) {
        // Prevent duplicate runs for the same configuration name
        //   Fixes infinite calls when multiDexEnabled is set
        if (moduleCopied[configuration.name])
            return
        moduleCopied[configuration.name] = true

        def configCopy = configuration.copy()
        forceCanBeResolved(configCopy)

        configCopy.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            doTargetSdkVersionAlign(details)

            if (!inGroupAlignList(details))
                return

            def curOverrideVersion = versionGroupAligns[details.requested.group]['version']
            def inComingVersion = getVersionFromDependencyResolveDetails(details)

            def compareVersionResult = compareVersions(inComingVersion, curOverrideVersion)
            if (compareVersionResult > 0)
                versionGroupAligns[details.requested.group]['version'] = inComingVersion
        }

        triggerResolutionStrategy(configCopy)
    }

    static void triggerResolutionStrategy(Object configuration) {
        // Will throw on 'compile' and 'implementation' tasks.
        // Checking for configuration.name == 'compile' || 'implementation' skips to much however
        try {
           configuration.resolvedConfiguration.resolvedArtifacts
        } catch (any) {}
    }

    static String getVersionFromDependencyResolveDetails(DependencyResolveDetails details) {
        def defaultVersionComparator = new DefaultVersionComparator()
        def defaultVersionSelectorScheme  = new DefaultVersionSelectorScheme(defaultVersionComparator)
        def parsedVersion = defaultVersionSelectorScheme.parseSelector(details.requested.version)

        if (parsedVersion instanceof ExactVersionSelector)
            return details.requested.version
        else if (parsedVersion instanceof VersionRangeSelector)
            return getHighestVersionFromVersionRangeSelector(parsedVersion)
        else if (parsedVersion instanceof SubVersionSelector)
            return details.requested.version.replace('+', '9999')
        else if (parsedVersion instanceof LatestVersionSelector)
            return '9999.9999.9999'
        else
            project.logger.error("OneSignalProjectPlugin: Unkown VersionSelector: ${parsedVersion}")
    }

    static final int MAJOR_INDEX = 0
    static final int MINOR_INDEX = 1
    static final int PATCH_INDEX = 2

    static String getHighestVersionFromVersionRangeSelector(VersionRangeSelector parsedVersion) {
        if (parsedVersion.upperInclusive)
            return parsedVersion.upperBound

        // If upper limit of range is exclusive subtract 1 from the version

        def parts = parsedVersion.upperBound.split('\\.')

        if (parts[PATCH_INDEX] != '0') {
            def patchVersion = parts[PATCH_INDEX].toInteger() - 1
            parts[PATCH_INDEX] = patchVersion.toString()
        }
        else {
            parts[PATCH_INDEX] = '9999'

            def minorVersion = parts[MINOR_INDEX].toInteger() - 1
            if (minorVersion == -1) {
                parts[MINOR_INDEX] = '9999'

                def majorVersion = parts[MAJOR_INDEX].toInteger() - 1
                parts[MAJOR_INDEX] = majorVersion.toString()
            }
            else
                parts[MINOR_INDEX] = minorVersion.toString()
        }

        parts.join('.')
    }
}