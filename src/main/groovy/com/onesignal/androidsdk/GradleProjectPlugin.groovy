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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector

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

    // Each of the groups below must have their own module versions aligned
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

            // Android Support Library can NOT be greater than compileSdkVersion
            // Example: Can't use com.android.support 26.+ when using compileSdkVersion 25
            //    The following error will be thrown in this case:
            //    "No resource found that matches the given name: attr 'android:keyboardNavigationCluster'"
            // This doesn't enforce increasing the support library version
            // Google does warn in Android Studio if this isn't aligned
            //     so we might want to do the same in the future
            'compileSdkVersionAlign': true
        ],

        // Exists only for UPDATE_PARENT_ON_DEPENDENCY_UPGRADE
        'com.onesignal': [
            'version': '0.0.0'
        ]
    ]

    // Update the following module versions to a compatible version for their target SDK
    static final def MINIMUM_MODULE_VERSION_FOR_TARGET_SDK = [
        'com.onesignal:OneSignal': [
            targetSdkVersion: [
                26: '3.6.3'
            ]
        ]
    ]

    static final def UPDATE_PARENT_ON_DEPENDENCY_UPGRADE = [
        'com.android.support': [
            27: [
                'com.onesignal': '3.7.1'
            ]
        ]
    ]

    static def versionGroupAligns
    static Project project
    static def moduleVersionOverrides
    static def moduleCopied

    static def didUpdateOneSignalVersion

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

        def module = MINIMUM_MODULE_VERSION_FOR_TARGET_SDK["${details.requested.group}:${details.requested.name}"]
        if (!module)
            return

        projectVariants().all { variant ->
            def curSdkVersion = getCurrentTargetSdkVersion()
            if (curSdkVersion == 0)
                return

            def curVersion = details.requested.version
            def newVersion = null
            module['targetSdkVersion'].each { key, value ->
                if (curSdkVersion < key)
                    return

                newVersion = acceptedOrIntersectVersion(value, newVersion ?: curVersion)
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

    static void compileSdkVersionAlign(versionOverride) {
        if (!versionOverride['compileSdkVersionAlign'])
            return

        def compileSdkVersion = project.android.compileSdkVersion.split('-')[1]

        // Will only decrease version, and only when needed
        // TODO:2: Need to rerun alignment to enforce UPDATE_PARENT_ON_DEPENDENCY_UPGRADE
        versionOverride['version'] = lowerMaxVersion(
            versionOverride['version'],
            "${compileSdkVersion}.+"
        )
    }

    static void overrideVersion(DependencyResolveDetails details, String resolvedVersion) {
        if (resolvedVersion == '0.0.0')
            return

        if (details.requested.version == resolvedVersion)
            return

        logModuleOverride(details, resolvedVersion)
        details.useVersion(resolvedVersion)
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

    // Returns 1 if inComing is newer than existing
    // Returns 0 if both version are the same
    // Returns -1 inComing is older than existing
    static int compareVersions(String inComing, String existing) {
        def versionComparator = new DefaultVersionComparator()
        versionComparator.compare(new VersionInfo(inComing), new VersionInfo(existing))
    }

    static Object finalAlignmentRules() {
        project.logger.debug("OneSignalProjectPlugin: FINAL ALIGN PART 1: ${versionGroupAligns}")

        def finalVersionGroupAligns = InternalUtils.deepcopy(versionGroupAligns)
        alignAcrossGroups(finalVersionGroupAligns)
        finalVersionGroupAligns.each { group ->
            compileSdkVersionAlign(group.value)
        }

        project.logger.debug("OneSignalProjectPlugin: FINAL ALIGN PART 2: ${finalVersionGroupAligns}")
        finalVersionGroupAligns
    }

    static void alignAcrossGroups(def versionGroupAligns) {
        def highestVersion = acceptedOrIntersectVersion(
            versionGroupAligns['com.google.android.gms']['version'],
            versionGroupAligns['com.google.firebase']['version']
        )

        versionGroupAligns['com.google.android.gms']['version'] = highestVersion
        versionGroupAligns['com.google.firebase']['version'] = highestVersion
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

        didUpdateOneSignalVersion = false

        def configCopy = configuration.copy()
        forceCanBeResolved(configCopy)

        configCopy.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            doTargetSdkVersionAlign(details)

            if (!inGroupAlignList(details))
                return

            def curOverrideVersion = versionGroupAligns[details.requested.group]['version']
            def newVersion = acceptedOrIntersectVersion(details.requested.version, curOverrideVersion)

            if (details.requested.group == 'com.onesignal')
                project.logger.info("OneSignal: curOverrideVersion: ${curOverrideVersion} -> ${newVersion}")

            if (newVersion != curOverrideVersion)
                updateVersionGroupAligns(details.requested.group, newVersion)
        }

        triggerResolutionStrategy(configCopy)

        // OneSignal version was changed, need to rerun to get it's dependencies
        if (didUpdateOneSignalVersion) {
            versionGroupAligns.each { group, settings ->
                if (group != 'com.onesignal')
                    settings['version'] = '0.0.0'
            }

            project.logger.info("didUpdateOneSignalVersion changed, doing a 2nd pass")
            generateHighestVersionsForGroups(configCopy)
        }

    }

    static void triggerResolutionStrategy(Object configuration) {
        // Will throw on 'compile' and 'implementation' tasks.
        // Checking for configuration.name == 'compile' || 'implementation' skips to much however
        try {
           configuration.resolvedConfiguration.resolvedArtifacts
        } catch (any) {}
    }

    static void updateParentOnDependencyUpgrade(String dependencyGroup, String dependencyVersion) {
        def dependencyGroupEntry = UPDATE_PARENT_ON_DEPENDENCY_UPGRADE[dependencyGroup]
        if (dependencyGroupEntry == null)
            return

        dependencyGroupEntry.each { key, value ->
            if (compareVersions(dependencyVersion, "${key}.0.0}") < 0)
                return // == continue in each closure

            value.each { parentGroupEntry ->
                def parentGroupVersionEntry = versionGroupAligns[parentGroupEntry.key]
                if (parentGroupVersionEntry != null) {
                    def compareVersionResult = compareVersions(parentGroupEntry.value, parentGroupVersionEntry['version'])
                    if (compareVersionResult > 0) {
                        didUpdateOneSignalVersion = true
                        versionGroupAligns[parentGroupEntry.key]['version'] = parentGroupEntry.value
                    }
                }
                else {
                    didUpdateOneSignalVersion = true
                    versionGroupAligns[parentGroupEntry.key] = [version: parentGroupEntry.value]
                }
            }
        }
    }

    static void updateVersionGroupAligns(String group, String version) {
        if (group == 'com.onesignal')
            project.logger.info("OneSignal: Setting version in versionGroupAligns to: ${version}")
        updateParentOnDependencyUpgrade(group, version)
        versionGroupAligns[group]['version'] = version
    }

    // Parses String version and turns it into a VersionSelector
    // Only returns ExactVersionSelector or VersionRangeSelector for easier handling:
    //   * Turns SubVersionSelector and LatestVersionSelector into ExactVersionSelector
    //   * Turns single range entries of [1.0.0] into ExactVersionSelector
    static VersionSelector parseSelector(String version) {
        def versionComparator = new DefaultVersionComparator()
        def versionSelectorScheme  = new DefaultVersionSelectorScheme(versionComparator)
        def versionSelector = versionSelectorScheme.parseSelector(version)

        // Turns VersionRangeSelector with a single value into ExactVersionSelector Example: [1.0.0]
        if (versionSelector instanceof VersionRangeSelector) {
            if (versionSelector.lowerBound == versionSelector.upperBound &&
                versionSelector.lowerInclusive && versionSelector.upperInclusive)
                return versionSelectorScheme.parseSelector(versionSelector.upperBound)
        }
        if (versionSelector instanceof SubVersionSelector)
            return versionSelectorScheme.parseSelector(version.replace('+', '9999'))

        if (versionSelector instanceof LatestVersionSelector)
            return new ExactVersionSelector('9999.9999.9999')

        versionSelector
    }

    static VersionSelector changeLowerBoundOfSelector(VersionRangeSelector selector, String newLower) {
        def parts = selector.getSelector().split(',')
        parts[0] = parts[0].replace(selector.lowerBound, newLower)
        parts[0] = parts[0].replace('(', '[').replace(')', '[').replace(']', '[')
        parseSelector(parts.join(','))
    }

    static VersionSelector changeUpperBoundOfSelector(VersionRangeSelector selector, String newUpper) {
        def parts = selector.getSelector().split(',')
        parts[1] = parts[1].replace(selector.upperBound, newUpper)
        parts[1] = parts[1].replace(')', ']').replace('(', ']').replace('[', ']')
        parseSelector(parts.join(','))
    }

    // Returns the intersection range of two versions
    // If no over lap the higher of the two will be returned
    static VersionRangeSelector mergedIntersectOrHigher(VersionRangeSelector inComing, VersionRangeSelector existing) {
        def intersectResult = inComing.intersect(existing)
        if (intersectResult != null)
            return intersectResult

        if (compareVersions(inComing.upperBound, existing.upperBound) > 0)
            return inComing
        existing
    }

    // Will return the newer of the two versions
    // If either or both versions include ranges, the range will be adjusted
    //   This includes narrowing or expanding ranges when newer
    static String acceptedOrIntersectVersion(String inComingStr, String existingStr) {
        def inComing = parseSelector(inComingStr)
        def existing = parseSelector(existingStr)

        def bothRangeSelectors = inComing instanceof VersionRangeSelector &&
                                 existing instanceof VersionRangeSelector
        if (bothRangeSelectors)
            return mergedIntersectOrHigher(inComing, existing).selector

        def bothExactSelectors = inComing instanceof ExactVersionSelector &&
                                 existing instanceof ExactVersionSelector
        if (bothExactSelectors) {
            if (compareVersions(inComing.selector, existing.selector) > 0)
                return inComingStr
            else
                return existingStr
        }

        // At this point we know either inComing or existing is a VersionRangeSelector type
        if (inComing instanceof VersionRangeSelector) {
            if (inComing.accept(existing.selector))
                return changeLowerBoundOfSelector(inComing, existingStr).selector
            else if (compareVersions(inComing.lowerBound, existingStr) > 0)
                return inComingStr
            else
                return changeUpperBoundOfSelector(inComing, existingStr).selector
        }
        else {
            if (existing.accept(inComing.selector))
                return changeLowerBoundOfSelector(existing, inComingStr).selector
            else if (compareVersions(inComingStr, existing.upperBound) > 0)
                return changeUpperBoundOfSelector(existing, inComingStr).selector
            else
                return existingStr
        }
    }

    // inComingStr can be any type of version
    // maxStr must be ExactVersionSelector or SubVersionSelector
    static String lowerMaxVersion(String currentStr, String maxStr) {
        def current = parseSelector(currentStr)
        def max = parseSelector(maxStr)

        if (current instanceof ExactVersionSelector) {
            if (compareVersions(max.selector, current.selector) > 0)
                return currentStr
            else
                return maxStr
        }

        if (compareVersions(max.selector, current.upperBound) > 0)
            return currentStr
        return maxStr
    }

}