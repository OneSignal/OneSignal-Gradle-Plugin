package com.onesignal.androidsdk

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector

import java.util.jar.Manifest

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

    static final String GROUP_GMS = 'com.google.android.gms'
    static final String GROUP_ANDROID_SUPPORT = 'com.android.support'
    static final String GROUP_FIREBASE = 'com.google.firebase'
    static final String GROUP_ONESIGNAL = 'com.onesignal'

    static final String MODULE_ONESIGNAL_SDK = 'com.onesignal:OneSignal'

    // Default version to indicate no current dependency references
    static final String NO_REF_VERSION = '0.0.0'

    // Each of the groups below must have their own module versions aligned
    static final def VERSION_GROUP_ALIGNS = [
        // ### Google Play Services library
        (GROUP_GMS): [
            version: NO_REF_VERSION
        ],

        // ### Google Firebase library
        (GROUP_FIREBASE): [
            version: NO_REF_VERSION
        ],

        // ### Android Support Library
        (GROUP_ANDROID_SUPPORT): [
            version: NO_REF_VERSION,
            omitModules: ['multidex', 'multidex-instrumentation'],

            // Android Support Library can NOT be greater than compileSdkVersion
            // Example: Can't use com.android.support 26.+ when using compileSdkVersion 25
            //    The following error will be thrown in this case:
            //    "No resource found that matches the given name: attr 'android:keyboardNavigationCluster'"
            // This doesn't enforce increasing the support library version
            // Google does warn in Android Studio if this isn't aligned
            //     so we might want to do the same in the future
            compileSdkVersionAlign: true
        ],

        // Exists only for UPDATE_PARENT_ON_DEPENDENCY_UPGRADE
        (GROUP_ONESIGNAL): [
            version: NO_REF_VERSION
        ]
    ]

    // Skip these groups when they are the parent when generating versionGroupAligns
    //   - This for example prevents GMS's dependency on Android Support from locking Android Support
    //     to a lower or higher version
    static final def SKIP_CALC_WHEN_PARENT = [GROUP_GMS, GROUP_FIREBASE, GROUP_ANDROID_SUPPORT]

    // Update the following module versions to a compatible version for their target SDK
    static final def MINIMUM_MODULE_VERSION_FOR_TARGET_SDK = [
        (MODULE_ONESIGNAL_SDK): [
            targetSdkVersion: [
                26: '3.6.3'
            ]
        ]
    ]

    static final def UPDATE_PARENT_ON_DEPENDENCY_UPGRADE = [
        (GROUP_ANDROID_SUPPORT): [
            '27.0.0': [
                (GROUP_ONESIGNAL): '3.7.0'
            ]
        ],
        (GROUP_GMS): [
            '8.4.0': [
                (GROUP_ANDROID_SUPPORT): '22.2.1'
            ],
            '11.2.0': [
                (GROUP_ANDROID_SUPPORT): '25.1.0'
            ],
            '11.8.0': [
                (GROUP_ANDROID_SUPPORT): '26.0.0'
            ]
        ],
        (GROUP_FIREBASE): [
            '9.8.0': [
                (GROUP_ANDROID_SUPPORT): '24.1.0'
            ]
        ]
    ]

    static def versionGroupAligns
    static Project project
    static def moduleVersionOverrides
    static def moduleCopied

    // If true we need to do a static version apply from project.ext
    static boolean gradleV2PostAGPApplyFallback

    static boolean didUpdateOneSignalVersion

    static Object getExtOverride(String prop) {
        if (!gradleV2PostAGPApplyFallback)
            return null
        project.ext.has(prop) ? project.ext.get(prop) : null
    }

    @Override
    void apply(Project inProject) {
        project = inProject
        gradleV2PostAGPApplyFallback = false
        didUpdateOneSignalVersion = false
        moduleVersionOverrides = false
        versionGroupAligns = InternalUtils.deepcopy(VERSION_GROUP_ALIGNS)
        moduleVersionOverrides = [:]
        moduleCopied = [:]

        disableGMSVersionChecks()
        detectProjectState()

        resolutionHooksForAndroidPluginV3()
        resolutionHooksForAndroidPluginV2()
    }

    // At fundamental level this OneSignal plugin and the gms version checks are solving the same problem
    // Disabling the version check part of the gms plugin with their flag
    static void disableGMSVersionChecks() {
        project.afterEvaluate {
            def googleServices = project.extensions.findByName('googleServices')
            if (googleServices)
                googleServices.config.disableVersionCheck = true
        }
    }

    // Get the AGP plugin instance if it has been applied already
    static Plugin appliedAndroidPlugin() {
        Plugin androidPlugin = null
        project.plugins.each {
            if (it.class.name == 'com.android.build.gradle.AppPlugin' ||
                it.class.name == 'com.android.build.gradle.LibraryPlugin') {
                androidPlugin = it
                return
            }
        }
        androidPlugin
    }

    static void detectProjectState() {
        def plugin = appliedAndroidPlugin()
        if (plugin == null)
            return

        if (compareVersions(getAGPVersion(plugin), '3.0.0') == -1) {
            project.logger.warn("WARNING: The onesignal-gradle-plugin MUST be before com.android.application!")
            project.logger.warn("   Please put onesignal-gradle-plugin first OR update to com.android.tools.build:gradle:3.0.0 or newer!")

            // In this fallback state and we can NOT get the dependency tree.
            //   This means we will be setting safe overrides later through the resolve process
            gradleV2PostAGPApplyFallback = true
        }
    }

    static String getAGPVersion(Plugin plugin) {
        try {
            def cl = plugin.class.classLoader as URLClassLoader
            def inputStream = cl.findResource('META-INF/MANIFEST.MF').openStream()
            def manifest = new Manifest(inputStream)
            return manifest.mainAttributes.getValue('Plugin-Version')
        } catch (any) {
            project.logger.warn("OneSignal Warning: Could not get AGP plugin version")
        }
        null
    }

    static void resolutionHooksForAndroidPluginV3() {
        project.afterEvaluate {
            projectVariants().all { variant ->
                // compileConfiguration is new in AGP 3.0.0
                if (!variant.hasProperty('compileConfiguration'))
                    return

                doResolutionStrategyAndroidPluginV3(variant.compileConfiguration)
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

            // AGP 3.0 - Still needed to catch Android specific tasks, <buildType>CompileClasspath
            project.dependencies {
                doResolutionStrategyAndroidPluginV2(configuration)
            }
        }
    }

    // Ends up being used for part of resolution in AGP 3.0 projects
    static void doResolutionStrategyAndroidPluginV2(Configuration configuration) {
        // The Android 3.0.0 Gradle plugin resolves this before we can
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

    static void doResolutionStrategyAndroidPluginV3(Configuration configuration) {
        generateHighestVersionsForGroups(configuration)

        configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            projectVariants().all { variant ->
                doTargetSdkVersionAlign(details)

                project.configurations.all { Configuration config ->
                    generateHighestVersionsForGroups(config)
                }

                doGroupAlignStrategyOnDetail(details)
            }
        }
    }

    // Get either applicationVariants or libraryVariants depending on project type
    static DomainObjectCollection<BaseVariant> projectVariants() {
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
            def mergedFlavor = variant.mergedFlavor
            // Use targetSdkVersion unless null, fallback is minSdkVersion, 1 the static fallback
            if (mergedFlavor.targetSdkVersion != null)
                targetSdkVersion = mergedFlavor.targetSdkVersion.apiLevel
            else if (mergedFlavor.minSdkVersion != null)
                targetSdkVersion = mergedFlavor.minSdkVersion.apiLevel
            else
                targetSdkVersion = 1
        }

        targetSdkVersion
    }

    // Checks the MINIMUM_MODULE_VERSION_FOR_TARGET_SDK hash to see if the module needs to be updated
    //   for the projects current targetSdkVersion
    static void doTargetSdkVersionAlign(DependencyResolveDetails details) {
        String existingOverrider = moduleVersionOverrides["${details.requested.group}:${details.requested.name}"]
        overrideVersion(details, existingOverrider)

        def module = MINIMUM_MODULE_VERSION_FOR_TARGET_SDK["${details.requested.group}:${details.requested.name}" as String]
        if (!module)
            return

        projectVariants().all { variant ->
            def curSdkVersion = getCurrentTargetSdkVersion()
            if (curSdkVersion == 0)
                return

            def curVersion = details.requested.version
            String newVersion = null
            module['targetSdkVersion'].each { key, value ->
                if (curSdkVersion < key)
                    return

                newVersion = acceptedOrIntersectVersion(value as String, newVersion ?: curVersion)
            }

            if (newVersion != null && newVersion != curVersion) {
                project.logger.info("Changing OneSignal for TargetSdkVersion. ${curVersion} -> ${newVersion}")
                moduleVersionOverrides["${details.requested.group}:${details.requested.name}"] = newVersion
                versionGroupAligns[details.requested.group] = [version: newVersion]
                overrideVersion(details, newVersion)
            }
        }
    }

    static void doGroupAlignStrategyOnDetail(DependencyResolveDetails details) {
        if (!inGroupAlignList(details))
            return

        String toVersion = finalAlignmentRules()[details.requested.group]['version']
        overrideVersion(details, toVersion)
    }

    static void compileSdkVersionAlign(versionOverride) {
        if (!versionOverride['compileSdkVersionAlign'])
            return

        def compileSdkVersion = (project.android.compileSdkVersion as String).split('-')[1]

        // gradleV2PostAGPApplyFallback means we can't get a dependency tree
        //   Blindly set Android Support Library to latest supported version of compileSdkVersion as a safe default
        if (gradleV2PostAGPApplyFallback && versionOverride['version'] == NO_REF_VERSION)
            versionGroupAligns[GROUP_ANDROID_SUPPORT]['version'] = "${compileSdkVersion}.+"

        // Will only decrease version, and only when needed
        // TODO:2: Need to rerun alignment to enforce UPDATE_PARENT_ON_DEPENDENCY_UPGRADE
        versionOverride['version'] = lowerMaxVersion(
            versionOverride['version'] as String,
            "${compileSdkVersion}.+"
        )
    }
    
    static void overrideVersion(DependencyResolveDetails details, String resolvedVersion) {
        // Omit NULLs
        if (resolvedVersion == null)
            return

        // Omit override if never set a non-default version
        if (resolvedVersion == NO_REF_VERSION)
            return

        // Omit override if no change
        if (details.requested.version == resolvedVersion)
            return

        // Special Google rule if going from pre-15's non-semantic versions to 15+'s semantic versions
        def newGoogleVersion = new ExactVersionSelector('15.0.0')
        def isGoogleLibrary = details.requested.group == GROUP_GMS || details.requested.group == GROUP_FIREBASE
        def hasAlignOver15 = isVersionInOrHigher(versionGroupAligns[GROUP_GMS]['version'] as String, newGoogleVersion) ||
                             isVersionInOrHigher(versionGroupAligns[GROUP_FIREBASE]['version'] as String, newGoogleVersion)
        if (isGoogleLibrary && hasAlignOver15) {
            // These Google license libraries were dropped in 15.0.0+, omit them
            if (details.requested.name ==~ /firebase-.*-license/ ||
                details.requested.name ==~ /play-services-.*-license/)
                return

            // If the requested version is under 15 increase to version range that will include other libraries
            if (isVersionBelow(details.requested.version, newGoogleVersion))
                resolvedVersion = '[15.0.0, 16.0.0)'
            // The requested version is higher or contains 15.0.0 don't override
            else if (isVersionInOrHigher(details.requested.version, newGoogleVersion))
                return
        }

        logModuleOverride(details, resolvedVersion)
        details.useVersion(resolvedVersion)
    }

    static void logModuleOverride(DependencyResolveDetails details, String resolvedVersion) {
        def modName = "${details.requested.group}:${details.requested.name}"
        def versionsMsg = "'${details.requested.version}' to '${resolvedVersion}'"
        def msg = "${modName} overridden from ${versionsMsg}"
        project.logger.info("OneSignalProjectPlugin: ${msg}")
    }


    static boolean inGroupAlignListFindByStrings(String group, String name) {
        // Only override groups we define
        def versionOverride = versionGroupAligns[group]
        if (!versionOverride)
            return false

        // Skip modules that should not align to other modules in the group
        def omitModules = versionOverride['omitModules']
        if (omitModules && omitModules.contains(name))
            return false

        true
    }

    static boolean inGroupAlignList(DependencyResolveDetails details) {
        inGroupAlignListFindByStrings(details.requested.group, details.requested.name)
    }

    // Compares two exact versions
    // Returns 1 if inComing is newer than existing
    // Returns 0 if both version are the same
    // Returns -1 inComing is older than existing
    static int compareVersions(String inComing, String existing) {
        def versionParser = new VersionParser()
        def inComingVersion = versionParser.transform(inComing)
        def existingVersion = versionParser.transform(existing)

        def versionComparator = new DefaultVersionComparator()
        versionComparator.asVersionComparator().compare(inComingVersion, existingVersion)
    }

    static Object finalAlignmentRules() {
        project.logger.debug("OneSignalProjectPlugin: FINAL ALIGN PART 1: ${versionGroupAligns}")

        def finalVersionGroupAligns = InternalUtils.deepcopy(versionGroupAligns)
        alignAcrossGroups(finalVersionGroupAligns)
        finalVersionGroupAligns.each { group ->
            compileSdkVersionAlign(group.value)
        }

        applyExtFallbackOverrides(finalVersionGroupAligns)

        project.logger.debug("OneSignalProjectPlugin: FINAL ALIGN PART 2: ${finalVersionGroupAligns}")
        finalVersionGroupAligns
    }

    // Normally not used, reads static fallbacks if project has AGP v2 and this plugin was applied last
    // Reads ext.androidSupportLibVersion and ext.androidSupportLibVersion to apply this logic
    static void applyExtFallbackOverrides(finalVersionGroupAligns) {
        if (!gradleV2PostAGPApplyFallback)
            return

        String googlePlayServicesVersion = getExtOverride('googlePlayServicesVersion')
        if (googlePlayServicesVersion && finalVersionGroupAligns[GROUP_GMS]['version'] == NO_REF_VERSION) {
            finalVersionGroupAligns[GROUP_GMS]['version'] = googlePlayServicesVersion
            finalVersionGroupAligns[GROUP_FIREBASE]['version'] = googlePlayServicesVersion
        }

        String androidSupportLibVersion = getExtOverride('androidSupportLibVersion')
        if (androidSupportLibVersion && finalVersionGroupAligns[GROUP_ANDROID_SUPPORT]['version'] == NO_REF_VERSION)
            finalVersionGroupAligns[GROUP_ANDROID_SUPPORT]['version'] = androidSupportLibVersion
    }

    // Parts of Firebase depend parts of GMS that must align to the same version
    // This only applies to versions less than 15.0.0
    static void alignAcrossGroups(versionGroupAligns) {
        def highestVersion = acceptedOrIntersectVersion(
            versionGroupAligns[GROUP_GMS]['version'] as String,
            versionGroupAligns[GROUP_FIREBASE]['version'] as String
        )

        versionGroupAligns[GROUP_GMS]['version'] = highestVersion
        versionGroupAligns[GROUP_FIREBASE]['version'] = highestVersion
    }

    // project.android.@plugin - This looks to be on the AppExtension class however this didn't work
    // Found 'enforceUniquePackageName' by comparing project.android.properties between versions
    static boolean isAndroidPluginV3() {
        !project.android.hasProperty('enforceUniquePackageName')
    }

    static void forceCanBeResolved(Configuration configuration) {
        // canBeResolved not available on Gradle 2.14.1 and older
        if (configuration.hasProperty('canBeResolved'))
            configuration.canBeResolved = true
    }

    static void generateHighestVersionsForGroups(Configuration configuration) {
        // Prevent duplicate runs for the same configuration name
        //   Fixes infinite calls when multiDexEnabled is set
        if (moduleCopied[configuration.name])
            return
        moduleCopied[configuration.name] = true

        didUpdateOneSignalVersion = false

        def configCopy = configuration.copyRecursive()
        forceCanBeResolved(configCopy)

        configCopy.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            doTargetSdkVersionAlign(details)

            if (!inGroupAlignList(details))
                return

            // Needed for "Upgrade to compatible OneSignal SDK when using Android Support library rev 27" test
            String curOverrideVersion = versionGroupAligns[details.requested.group]['version']
            overrideVersion(details, curOverrideVersion)
        }

        triggerResolutionStrategy(configCopy)

        // OneSignal version was changed, need to rerun to get it's dependencies
        if (didUpdateOneSignalVersion) {
            versionGroupAligns.each { group, settings ->
                if (group != GROUP_ONESIGNAL)
                    settings['version'] = NO_REF_VERSION
            }

            project.logger.info("didUpdateOneSignalVersion changed, doing a 2nd pass")
            generateHighestVersionsForGroups(configCopy)
        }
    }

    static void updateVersionGroupAligns(String group, String version) {
        if (group == GROUP_ONESIGNAL)
            project.logger.info("OneSignal: Setting version in versionGroupAligns to: ${version}")
        updateParentOnDependencyUpgrade(group, version)

        versionGroupAligns[group]['version'] = version
    }

    static void triggerResolutionStrategy(Configuration configuration) {
        try {
           processIncomingResolutionResults(configuration)
        } catch (ignored) {
            // Suppressing as some copied configurations can't be resolved yet and will throw
            // Uncomment to debug issues
            // ignored.printStackTrace()
        }
    }

    static void processIncomingResolutionResults(Configuration configuration) {
        configuration.incoming.resolutionResult.allDependencies.each { DependencyResult dependencyResult ->
            // Ignore Google's inner dependencies when deciding on what version align groups with
            if (shouldSkipCalcIfParent(dependencyResult))
                return

            def requestedArtifactParts = dependencyResult.requested.displayName.split(':')

            // String did't contain all parts, most likely a project result so skip
            if (requestedArtifactParts.size() < 3)
                return

            def group = requestedArtifactParts[0]
            def module = requestedArtifactParts[1]
            def version = requestedArtifactParts[2]

            if (!inGroupAlignListFindByStrings(group, module))
                return

            String curOverrideVersion = versionGroupAligns[group]['version']
            def newVersion = acceptedOrIntersectVersion(version, curOverrideVersion)

            updateVersionGroupAligns(group, newVersion)
        }

        // Triggers configuration.incoming.resolutionResult above
        // Rethrow on failures if any issues
        configuration.resolvedConfiguration.rethrowFailure()
    }

    static boolean shouldSkipCalcIfParent(DependencyResult result) {
        def group = result.from.id.displayName.split(':')[0]
        SKIP_CALC_WHEN_PARENT.contains(group)
    }

    static void updateParentOnDependencyUpgrade(String dependencyGroup, String dependencyVersion) {
        UPDATE_PARENT_ON_DEPENDENCY_UPGRADE[dependencyGroup].each { key, value ->
            def exactVersion = new ExactVersionSelector(key)
            if (isVersionBelow(dependencyVersion, exactVersion))
                return // == continue in each closure

            value.each { parentGroupEntry ->
                def parentGroupVersionEntry = versionGroupAligns[parentGroupEntry.key]
                if (parentGroupVersionEntry != null) {
                    def compareVersionResult = acceptedOrIntersectVersion(
                        parentGroupEntry.value,
                        parentGroupVersionEntry['version'] as String
                    )
                    if (compareVersionResult != parentGroupVersionEntry['version']) {
                        if (parentGroupEntry.key == GROUP_ONESIGNAL)
                            didUpdateOneSignalVersion = true
                        versionGroupAligns[parentGroupEntry.key]['version'] = parentGroupEntry.value
                    }
                }
                else {
                    if (parentGroupEntry.key == GROUP_ONESIGNAL)
                        didUpdateOneSignalVersion = true
                    versionGroupAligns[parentGroupEntry.key] = [version: parentGroupEntry.value]
                }
            }
        }
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
        // Turn + into a safe highest possible segment value of 9999
        if (versionSelector instanceof SubVersionSelector)
            return versionSelectorScheme.parseSelector(version.replace('+', '9999'))

        if (versionSelector instanceof LatestVersionSelector)
            return new ExactVersionSelector('9999.9999.9999')

        versionSelector
    }


    // VersionRangeSelector.intersect was introduced in Gradle 4.3, this is a compat wrapper method
    static VersionRangeSelector intersectCompat(VersionRangeSelector inComing, VersionRangeSelector existing) {
        if (inComing.metaClass.respondsTo(inComing, 'intersect', VersionRangeSelector, VersionRangeSelector))
            return inComing.intersect(existing)

        // This means we are on Gradle 4.2 or older so use compat version of intersect
        VersionCompatHelpers.intersect(inComing, existing)
    }

    // Returns the intersection range of two versions
    // If no over lap the higher of the two will be returned
    static VersionRangeSelector mergedIntersectOrHigher(VersionRangeSelector inComing, VersionRangeSelector existing) {
        def intersectResult = intersectCompat(inComing, existing)
        if (intersectResult != null)
            return intersectResult

        if (compareVersions(inComing.upperBound, existing.upperBound) > 0)
            return inComing
        existing
    }

    // Will return the newer if both versions are exact values
    // If one version is a range and another an exact then the exact will be used if
    //   it is in the range or newer
    // If both versions are ranges they will be narrowed, if there is an intersect
    //    - Otherwise the newer of the two ranges will be used
    // Note: If not merging 2 version ranges return exact input Strings as parseSelector
    //       modifying version with +'s into non-usable exact versions for easier comparing logic
    // TODO:FUTURE: Before a 1.0.0 release of this plugin finalize if versions bellow range bounds
    //                should result in the lowest possible value for the range.
    //              If we do this then the following much change;
    //                1. Use lowerBound of inclusive range. (Easy)
    //                2. If exclusive, use the next lowest version candidate. (Hard)
    static String acceptedOrIntersectVersion(String inComingStr, String existingStr) {
        def inComing = parseSelector(inComingStr)
        def existing = parseSelector(existingStr)

        def bothRangeSelectors = inComing instanceof VersionRangeSelector &&
                                 existing instanceof VersionRangeSelector
        if (bothRangeSelectors)
            return mergedIntersectOrHigher(inComing as VersionRangeSelector, existing as VersionRangeSelector).selector

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
                return existingStr
            else if (compareVersions(inComing.lowerBound, existingStr) > 0)
                return inComingStr
            else
                return existingStr
        }
        else {
            if (existing.accept(inComing.selector))
                return inComingStr
            else if (compareVersions(inComingStr, existing.upperBound) > 0)
                return inComingStr
            else
                return existingStr
        }
    }

    // If currentStr.upperBound > maxStr then return maxStr
    // currentStr can be any type of version
    // maxStr must be ExactVersionSelector or SubVersionSelector
    static String lowerMaxVersion(String currentStr, String maxStr) {
        def current = parseSelector(currentStr)
        def max = parseSelector(maxStr)

        if (current instanceof ExactVersionSelector) {
            if (compareVersions(max.selector, current.selector) > 0)
                return currentStr
            return maxStr
        }

        // Assume Version Range
        if (compareVersions(max.selector, current.upperBound) > 0)
            return currentStr
        maxStr
    }

    // Checks if version is lower or if is contained in the range
    // inVersionStr can be a String of an ExactVersionSelector or VersionRangeSelector
    static boolean isVersionInOrLower(String inVersionStr, ExactVersionSelector checkVersion) {
        lowerMaxVersion(inVersionStr, checkVersion.selector) != inVersionStr
    }

    // return true if currentStr is higher than checkVersion
    // currentStr can be any type of version
    // checkVersion must be ExactVersionSelector or SubVersionSelector
    static boolean isVersionInOrHigher(String currentStr, ExactVersionSelector checkVersion) {
        def current = parseSelector(currentStr)
        def check = parseSelector(checkVersion.selector)

        if (current instanceof ExactVersionSelector)
            return compareVersions(current.selector, check.selector) > -1

        // Assume Version Range
        compareVersions(current.upperBound, check.selector) > -1
    }

    // return true if checkVersion is less than currentVersionStr
    // If currentVersionStr is a version range return true if checkVersion is under currentVersionStr.lowerBound
    static boolean isVersionBelow(String currentVersionStr, ExactVersionSelector checkVersion) {
        def current = parseSelector(currentVersionStr)

        if (current instanceof ExactVersionSelector)
            return compareVersions(current.selector, checkVersion.selector) < 0

        compareVersions(current.lowerBound, checkVersion.selector) < 0
    }
}