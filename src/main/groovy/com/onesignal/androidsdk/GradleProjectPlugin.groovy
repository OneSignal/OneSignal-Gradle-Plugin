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

    static final VERSION_SELECTOR_SCHEME = new DefaultVersionSelectorScheme(new DefaultVersionComparator())

    static final String GROUP_GMS = 'com.google.android.gms'
    static final String GROUP_ANDROID_SUPPORT = 'com.android.support'
    static final String GROUP_FIREBASE = 'com.google.firebase'
    static final String GROUP_ONESIGNAL = 'com.onesignal'

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
            version: NO_REF_VERSION,
            // Do not attempt to align Firebase's Unity libraries
            // These wrapper libraries have their own version numbers that don't line up
            omitModulesPostFix: '-unity',
        ],

        // ### Android Support Library
        (GROUP_ANDROID_SUPPORT): [
            version: NO_REF_VERSION,
            omitModules: ['multidex', 'multidex-instrumentation'],

            // Given a compileSdkVersion what is the maximum Android Support Library that can be used
            // Going over the maximum version results in compile time errors
            // Example: Can't use com.android.support 26.+ when using compileSdkVersion 25
            //    The following error will be thrown in this case:
            //    "No resource found that matches the given name: attr 'android:keyboardNavigationCluster'"
            // This table will only lower Android Support Versions when needed, it will never increase them
            // Google linting says they must match but though testing we found these to be the max versions
            compileSdkVersionMax: [
                22: '22.+',
                23: '25.0.+',
                24: '25.+',
                25: '25.+',
                26: '27.+',
                27: '27.+',
                28: '28.+'
                // There is no 29, AndroidX has taken it's place
            ]
        ],

        // Exists only for UPDATE_PARENT_ON_DEPENDENCY_UPGRADE
        (GROUP_ONESIGNAL): [
            version: NO_REF_VERSION
        ]
    ]


    // Is com.google.android.gms:play-services included in the project?
    // If so we will limit gms and firebase to 12.0.1 as this it's last version
    static boolean hasFullPlayServices

    static final def GOOGLE_SEMANTIC_EXACT_VERSION = new ExactVersionSelector('15.0.0')
    static final def LAST_MAJOR_ANDROID_SUPPORT_VERSION = 28

    // Skip these groups when they are the parent when generating versionGroupAligns
    //   - This for example prevents GMS's dependency on Android Support from locking Android Support
    //     to a lower or higher version
    static final def SKIP_CALC_WHEN_PARENT = [GROUP_GMS, GROUP_FIREBASE, GROUP_ANDROID_SUPPORT]

    // Update the following module versions to a compatible version for their target SDK
    static final def MINIMUM_MODULE_VERSION_FOR_TARGET_SDK = [
        (GROUP_ONESIGNAL): [
            26: '3.6.3'
        ]
    ]

    // Similar to MODULE_DEPENDENCY_MINIMUMS but applied at the group level.
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

    // Summary: Map of modules with a map of versions if are at or higher then update the list of modules.
    // Solves: Issue where a sub dependency of a module can be updated to a version that is incompatible
    //         to a parent modules.
    // Example: firebase-iid:16.2.0 and firebase-messaging:17.0.0 causes a runtime crash of
    //    class not found. While scanning dependencies when we find firebase-iid:16.2.0
    //    we will update firebase-messaging to 17.1.0 to fix this issue.
    // Inherits: Any module min version is carried over to higher numbers if it is defined in a lower version number.
    //           Example: If firebase-iid:20.1.6 is used in a project then OneSignal will still be upgraded to 3.13.0
    //                    even though OneSignal:3.13.0 is only defined in the hash under firebase-iid:20.1.1.
    // NOTE: This list is manually maintained but should be generated in the future
    //       One way to do this would be to run a build with proguard and check for any class missing warnings
    //       A report can crawl up in versions that are available until a failure, then bump the dependent version
    static final Map<String, Map<String, Map<String, String>>> MODULE_DEPENDENCY_MINIMUMS = [
        'com.google.firebase:firebase-core': [
            '16.0.0': [
                'com.google.firebase:firebase-messaging': '17.0.0'
            ]
        ],
        'com.google.firebase:firebase-common': [
            // Tested firebase-common:17.1.0 back to firebase-iid:10.2.1
            '18.0.0': [
                'com.google.firebase:firebase-iid': '19.0.0'
            ]
            // Tested up to firebase-common:19.3.0
        ],
        'com.google.firebase:firebase-iid': [
            '16.2.0': [
                'com.google.firebase:firebase-messaging': '17.1.0'
            ],
            '17.0.0': [
                'com.google.firebase:firebase-messaging': '17.3.0'
            ],
            '17.0.1': [
                'com.google.firebase:firebase-messaging': '17.3.1'
            ],
            '17.1.0': [
                'com.google.firebase:firebase-messaging': '17.4.0'
            ],
            '17.1.1': [
                'com.google.firebase:firebase-messaging': '17.5.0'
            ],
            '17.1.2': [
                'com.google.firebase:firebase-messaging': '17.6.0'
            ],
            '18.0.0': [
                'com.google.firebase:firebase-messaging': '18.0.0'
            ],
            '19.0.1': [
                'com.google.firebase:firebase-messaging': '19.0.1'
            ],
            '20.0.0': [
                'com.google.firebase:firebase-messaging': '20.0.0'
            ],
            '20.1.0': [
                'com.google.firebase:firebase-messaging': '20.1.1'
            ],
            '20.1.1': [
                'com.google.firebase:firebase-messaging': '20.1.1',
                'com.onesignal:OneSignal': '3.13.0'
            ],
            '20.1.2': [
                'com.google.firebase:firebase-messaging': '20.1.1'
            ],
            '20.1.4': [
                'com.google.firebase:firebase-messaging': '20.1.4'
            ],
            '20.1.5': [
                'com.google.firebase:firebase-messaging': '20.1.4'
            ]
        ],
        'com.google.android.gms:play-services-measurement-base': [
            '15.0.4': [
                'com.google.firebase:firebase-analytics': '16.0.0'
            ]
        ],
        'com.google.android.gms:play-services-basement': [
            '16.0.1': [
                'com.google.firebase:firebase-messaging': '17.3.3'
            ],
            '17.0.0': [
                'com.google.android.gms:play-services-base': '17.0.0'
            ]
        ]
    ]

    static Map<String, Object> versionModuleAligns

    static def versionGroupAligns
    static Project project
    static def copiedModules

    // If true we need to do a static version apply from project.ext
    static boolean gradleV2PostAGPApplyFallback

    static boolean didUpdateOneSignalVersion

    enum WarningType {
        SUPPORT_DOWNGRADE
    }
    static Map<WarningType, Boolean> shownWarnings

    static Object getExtOverride(String prop) {
        if (!gradleV2PostAGPApplyFallback)
            return null
        project.ext.has(prop) ? project.ext.get(prop) : null
    }

    @Override
    void apply(Project inProject) {
        project = inProject
        project.logger.info('Initializing OneSignal-Gradle-Plugin 0.12.7')

        hasFullPlayServices = false
        gradleV2PostAGPApplyFallback = false
        didUpdateOneSignalVersion = false
        versionGroupAligns = InternalUtils.deepcopy(VERSION_GROUP_ALIGNS)
        copiedModules = [:]
        shownWarnings = [:]
        versionModuleAligns = [:]

        disableGMSVersionChecks()
        detectProjectState()

        resolutionHooksForAndroidPluginV3()

        // Breaks AGP 3.3.0+ so skipping this AGP V2 method call.
        // Checking Gradle version instead of AGP versions as it won't be loaded yet.
        // This high of a Gradle version would not normally be used
        if (compareVersions(project.gradle.gradleVersion, '4.10.0') == -1)
            resolutionHooksForAndroidPluginV2()
    }

    // At fundamental level this OneSignal plugin and the gms version checks are solving the same problem
    // Disabling the version check part of the gms plugin with their flag
    static void disableGMSVersionChecks() {
        project.afterEvaluate {
            def googleServices = project.extensions.findByName('googleServices')
            if (googleServices)
                googleServices.disableVersionCheck = true
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

        if (isAGPVersionOlderThan(plugin, '3.0.0')) {
            project.logger.warn("WARNING: The onesignal-gradle-plugin MUST be before com.android.application!")
            project.logger.warn("   Please put onesignal-gradle-plugin first OR update to com.android.tools.build:gradle:3.0.0 or newer!")

            // In this fallback state and we can NOT get the dependency tree.
            //   This means we will be setting safe overrides later through the resolve process
            gradleV2PostAGPApplyFallback = true
        }
    }

    static boolean isAGPVersionOlderThan(Plugin plugin, String version) {
        def agpVersion = getAGPVersion(plugin)
        if (!agpVersion) {
            if (project)
                project.logger.warn('OneSignal Warning: Could not get AGP plugin version')
            return false
        }

        compareVersions(agpVersion, version) == -1
    }

    static String getAGPVersion(Plugin plugin) {
        try {
            def cl = plugin.class.classLoader as URLClassLoader
            def inputStream = cl.findResource('META-INF/MANIFEST.MF').openStream()
            def manifest = new Manifest(inputStream)
            return manifest.mainAttributes.getValue('Plugin-Version')
        } catch (ignore) {}
        null
    }

    static void warnOnce(WarningType type, String msg) {
        if (shownWarnings[type])
            return
        shownWarnings[type] = true
        project.logger.warn("OneSignalPlugin: WARNING: $msg")
    }

    static void resolutionHooksForAndroidPluginV3() {
        // Can use this instead of 'project.afterEvaluate'
        // project.gradle.projectsEvaluated { gradle ->  // gradle.allprojects {
        // However this does not work for library projects
        project.afterEvaluate {
            // variant only includes 'release' and 'debug'
            projectVariants().all { variant ->
                // compileConfiguration is new in AGP 3.0.0
                if (!variant.hasProperty('compileConfiguration'))
                    return

                // TODO: Remove this after testing
                // project.ext['android.injected.build.model.only'] = true

                doResolutionStrategyAndroidPluginV3(variant.runtimeConfiguration)
                doResolutionStrategyAndroidPluginV3(variant.compileConfiguration)
                doResolutionStrategyAndroidPluginV3(variant.annotationProcessorConfiguration)
            }
            doResolutionStrategyAndroidPluginV3_3()
        }
    }

    static void resolutionHooksForAndroidPluginV2() {
        project.configurations.all { configuration ->
            project.dependencies {
                doResolutionStrategyAndroidPluginV2(configuration)
            }
        }

        project.configurations.all { configuration ->
            project.afterEvaluate {
                if (isAndroidPluginV3())
                    return
                generateHighestVersionsForGroups(configuration)
                doResolutionStrategyAndroidPluginV2(configuration)
            }
        }
    }

    static void doResolutionStrategyAndroidPluginV2(Configuration configuration) {
        // The Android 3.0.0 Gradle plugin resolves this before we can
        //   Skip it in this case to prevent a build error
        if (configuration.name.with { endsWith('WearApp') || endsWith('wearApp')} )
            return

        configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            // Soonest we can do this check, as project.android won't be available before
            if (isAndroidPluginV3())
                return

            // The Android 2.14.1 plugin doesn't work with doTargetSdkVersionAlign
            //   We are not able to get the targetSDK version in this case.

            // Once doTargetSdkVersionAlign is fixed this will loop with compileCopy
            //  At this point we can skip this by checking for ending 'Copy' in the config name
            // generateHighestVersionsForGroups(configuration)

            doGroupAlignStrategyOnDetail(details)
        }
    }

    // Calculates versions alignment and applies it to all configurations
    static void doResolutionStrategyAndroidPluginV3(Configuration lazyConfiguration) {
        // Step 1. Generate a versionGroupAligns Map for all configurations
        // TODO: Look into only running once instead of per variant
        project.configurations.all { configuration ->
            generateHighestVersionsForGroups(configuration)
        }

        // Step 2. Apply version overrides to configurations
        lazyConfiguration.resolutionStrategy.eachDependency { details ->
            doGroupAlignStrategyOnDetail(details)
        }
    }

    static void doResolutionStrategyAndroidPluginV3_3() {
        project.configurations.all { configuration ->
            // Config may start with debug or release
            if (configuration.name.with {
                endsWith('AndroidTestRuntimeClasspath') ||
                endsWith('AndroidTestCompileClasspath')
            }) {
                configuration.resolutionStrategy.eachDependency { details ->
                    doGroupAlignStrategyOnDetail(details)
                }
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
            if (mergedFlavor.targetSdkVersion)
                targetSdkVersion = mergedFlavor.targetSdkVersion.apiLevel
            else if (mergedFlavor.minSdkVersion)
                targetSdkVersion = mergedFlavor.minSdkVersion.apiLevel
            else
                targetSdkVersion = 1
        }

        targetSdkVersion
    }

    // Based on android.targetSdkVersion, upgrade any groups to meet compatibility
    static void doTargetSdkVersionAlign() {
        def curSdkVersion = getCurrentTargetSdkVersion()
        if (curSdkVersion == 0)
            return

        MINIMUM_MODULE_VERSION_FOR_TARGET_SDK.each { groupKey, targetSdkMap ->
            def curVersion = versionGroupAligns[groupKey]['version'] as String
            if (!curVersion || curVersion == NO_REF_VERSION)
                return

            targetSdkMap.each { limitTargetSdkVersion, groupVersion ->
                if (curSdkVersion < limitTargetSdkVersion)
                    return
                def newVersion = acceptedOrIntersectVersion(groupVersion, curVersion)
                if (newVersion != curVersion) {
                    project.logger.info("Changing OneSignal for TargetSdkVersion. ${curVersion} -> ${newVersion}")
                    versionGroupAligns[groupKey] = [version: newVersion]
                }
            }
        }
    }

    static void doGroupAlignStrategyOnDetail(DependencyResolveDetails details) {
        if (!inGroupAlignList(details))
            return

        String toVersion = finalAlignmentRules()[details.target.group]['version']
        overrideVersion(details, toVersion)
    }

    static void compileSdkVersionDependencyLimits(versionOverride) {
        def maxSupportVersionObj = versionOverride['compileSdkVersionMax'] as Map<Integer, String>
        if (!maxSupportVersionObj)
            return

        def maxSupportVersion = maxAndroidSupportVersion(maxSupportVersionObj)
        def currentOverride = versionOverride['version'] as String

        // gradleV2PostAGPApplyFallback means we can't get a dependency tree
        //   Blindly set Android Support Library to latest supported version of compileSdkVersion as a safe default
        if (gradleV2PostAGPApplyFallback && currentOverride == NO_REF_VERSION)
            versionGroupAligns[GROUP_ANDROID_SUPPORT]['version'] = maxSupportVersion

        // Will only decrease version, and only when needed
        // TODO:2: Need to rerun alignment to enforce UPDATE_PARENT_ON_DEPENDENCY_UPGRADE
        def newMaxVersion = lowerMaxVersion(currentOverride, maxSupportVersion)

        if (newMaxVersion != currentOverride) {
            warnOnce(
                WarningType.SUPPORT_DOWNGRADE,
                "OneSignalPlugin: Downgraded 'com.android.support:$currentOverride' -> $newMaxVersion" +
                    " to prevent compile errors! Recommend updating your project's compileSdkVersion!"
            )
        }
        versionOverride['version'] = newMaxVersion
    }

    static String maxAndroidSupportVersion(Map<Integer, String> maxSupportVersionObj) {
        def compileSdkVersion = (project.android.compileSdkVersion as String).split('-')[1].toInteger()
        if (compileSdkVersion <= LAST_MAJOR_ANDROID_SUPPORT_VERSION) {
            String maxSupportVersion = maxSupportVersionObj[compileSdkVersion]
            if (maxSupportVersion)
                maxSupportVersion
            else
                LAST_MAJOR_ANDROID_SUPPORT_VERSION
        }
        else
            LAST_MAJOR_ANDROID_SUPPORT_VERSION
    }

    static void overrideVersion(DependencyResolveDetails details, String groupVersionOverride) {
        def group = details.target.group
        def name = details.target.name
        def version = details.target.version

        String resolvedVersion = null
        def moduleOverride = versionModuleAligns["$group:$name"]

        // 1. Special Google rule if going from pre-15's non-semantic versions to 15+'s semantic versions
        def isGoogleLibrary = group == GROUP_GMS || group == GROUP_FIREBASE
        def hasAlignOver15 = isVersionInOrHigher(versionGroupAligns[GROUP_GMS]['version'] as String, GOOGLE_SEMANTIC_EXACT_VERSION) ||
                             isVersionInOrHigher(versionGroupAligns[GROUP_FIREBASE]['version'] as String, GOOGLE_SEMANTIC_EXACT_VERSION)
        if (isGoogleLibrary && hasAlignOver15) {
            // These Google license libraries were dropped in 15.0.0+, omit them
            if (name ==~ /firebase-.*-license/ ||
                name ==~ /play-services-.*-license/)
                return

            // Means this project has com.google.android.gms:play-services
            // As another option it might be possible to include all part of `com.google.android.gms` instead
            if (hasFullPlayServices)
                resolvedVersion = '12.0.1'
            // If the requested version is under 15 increase to version range that will include other libraries
            else if (isVersionBelow(version, GOOGLE_SEMANTIC_EXACT_VERSION))
                resolvedVersion = '[15.0.0, 16.0.0)'
            // The requested version is higher or contains 15.0.0 don't override
            else if (!moduleOverride && isVersionInOrHigher(version, GOOGLE_SEMANTIC_EXACT_VERSION))
                return
        }

        // 2. Handle overriding specific modules
        if (moduleOverride) {
            if (resolvedVersion == null || resolvedVersion == NO_REF_VERSION)
                resolvedVersion = version
            resolvedVersion = acceptedOrIntersectVersion(
                moduleOverride['version'] as String,
                resolvedVersion
            )
        }
        else if (resolvedVersion == null)
            resolvedVersion = groupVersionOverride

        // 3. Omit if no value
        if (resolvedVersion == null || resolvedVersion == NO_REF_VERSION)
            return

        // 4. Omit override if no change
        if (version == resolvedVersion)
            return

        logModuleOverride(details, resolvedVersion)

        // This '+' version check works for now but...
        // TODO: Do not let group override (resolvedVersion) override moduleOverride
        // TODO: Create a test reproducing the problem in 3.2.1
        if (resolvedVersion == '+')
            return

        details.useVersion(resolvedVersion)
        if (details.respondsTo('because'))
            details.because('OneSignal override')
    }

    static void logModuleOverride(DependencyResolveDetails details, String resolvedVersion) {
        def modName = "${details.target.group}:${details.target.name}"
        def versionsMsg = "'${details.target.version}' to '${resolvedVersion}'"
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

        def omitModulesPostFix = versionOverride['omitModulesPostFix']
        if (omitModulesPostFix && name.endsWith(omitModulesPostFix))
            return false

        true
    }

    static boolean inGroupAlignList(DependencyResolveDetails details) {
        inGroupAlignListFindByStrings(details.target.group, details.target.name)
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

    static Map<String, Object> finalAlignmentRules() {
        project.logger.info("OneSignalProjectPlugin: FINAL ALIGN PART 1: Groups : ${versionGroupAligns}")
        project.logger.info("OneSignalProjectPlugin: FINAL ALIGN PART 1: Modules: ${versionModuleAligns}")

        def finalVersionGroupAligns = InternalUtils.deepcopy(versionGroupAligns) as Map<String, Object>
        alignAcrossGroups(finalVersionGroupAligns)
        finalVersionGroupAligns.each { group ->
            compileSdkVersionDependencyLimits(group.value)
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
        // Skipping configurations to fix the following error
        // > Cannot create variant 'android-manifest-metadata' after configuration ':debugApiElements' has been resolved
        if (configuration.name.with {
              endsWith('AndroidTestRuntimeClasspath') ||
              endsWith('AndroidTestAnnotationProcessorClasspath') ||
              endsWith('AnnotationProcessor') ||
              endsWith('AndroidTestCompileClasspath') ||
              endsWith('CompileClasspath') ||
              endsWith('UnitTestRuntimeClasspath') ||
              endsWith('UnitTestCompileClasspath') ||
              endsWith('wearApp')
        })
            return

        // Prevent duplicate runs for the same configuration name
        //   Fixes infinite calls when multiDexEnabled is set
        if (copiedModules[configuration.name])
            return
        copiedModules[configuration.name] = true

        didUpdateOneSignalVersion = false

        def configCopy = configuration.copyRecursive()
        forceCanBeResolved(configCopy)

        configCopy.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            if (!inGroupAlignList(details))
                return

            // Needed for "Upgrade to compatible OneSignal SDK when using Android Support library rev 27" test
            String curOverrideVersion = versionGroupAligns[details.target.group]['version']
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
            def requestedArtifactParts = dependencyResult.requested.displayName.split(':')

            // String didn't contain all parts, most likely a project result so skip
            if (requestedArtifactParts.size() < 3)
                return

            def group = requestedArtifactParts[0]
            def module = requestedArtifactParts[1]
            def version = requestedArtifactParts[2]

            updateVersionModuleAligns(group, module, version)

            // Ignore Google's inner dependencies when deciding on what version align groups with
            if (shouldSkipCalcIfParent(dependencyResult))
                return

            if (!inGroupAlignListFindByStrings(group, module))
                return

            String curOverrideVersion = versionGroupAligns[group]['version']
            def newVersion = acceptedOrIntersectVersion(version, curOverrideVersion)

            updateVersionGroupAligns(group, newVersion)
        }.every({
            // This every block runs after the closure above
            doTargetSdkVersionAlign()
        })

        // Triggers configuration.incoming.resolutionResult above
        // Rethrow on failures if any issues
        configuration.resolvedConfiguration.rethrowFailure()
    }

    /**
     * Given a child dependency this adds a minimum version dependency rule for any of it's parents.
     * Gradle does NOT check if upgrading a dependency will break any of it's parent's when upgrading
     *    so we do this here.
     * This works off a whitelist defined in MODULE_DEPENDENCY_MINIMUMS today, however it
     *    might be possible to generate on the fly in the future
     * A request to build such a functionality into Gradle and an in depth explanation:
     * https://github.com/gradle/gradle/issues/10170
     * @param group - String - Gradle group such as "com.company"
     * @param module - String - Gradle module such as "OneSignal"
     * @param version - String - Exact version or a valid Gradle version range such as "[1.0.0, 2.0.0)"
     */
    static void updateVersionModuleAligns(String group, String module, String version) {
        def inputModule = "$group:$module"

        // When we get a dependent version value parent's to a min version if needed
        def rule = MODULE_DEPENDENCY_MINIMUMS[inputModule]
        rule.each { parentVersion, dependentChildMinVersion ->
            def exactVersion = new ExactVersionSelector(parentVersion)
            if (isVersionBelow(version, exactVersion))
                return // == continue in each closure

            dependentChildMinVersion.each { parentModuleEntry ->
                def parentModuleVersionEntry = versionModuleAligns[parentModuleEntry.key]

                if (parentModuleVersionEntry != null) {
                    def compareVersionResult = acceptedOrIntersectVersion(
                        parentModuleEntry.value,
                        parentModuleVersionEntry['version'] as String
                    )
                    if (compareVersionResult != parentModuleVersionEntry['version'])
                        updateVersionModuleAlignsKey(parentModuleEntry.key, parentModuleEntry.value)
                }
                else
                    updateVersionModuleAlignsKey(parentModuleEntry.key, parentModuleEntry.value)
            }
        }

        if (group == GROUP_GMS && module == 'play-services')
            hasFullPlayServices = true
    }

    static void updateVersionModuleAlignsKey(String groupAndModule, String version) {
        versionModuleAligns[groupAndModule] = [version: version]

        // Recursive call to check the parent of this parent rule we just added
        // Updating a child dependency can have an upwards cascading affect through dependencies
        def group = groupAndModule.split(':')[0]
        def module = groupAndModule.split(':')[1]
        updateVersionModuleAligns(group, module, version)
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
        if (version == '+')
            version = 'latest.release'

        final versionSelector = VERSION_SELECTOR_SCHEME.parseSelector(version)

        // Turns VersionRangeSelector with a single value into ExactVersionSelector Example: [1.0.0]
        if (versionSelector instanceof VersionRangeSelector) {
            if (versionSelector.lowerBound == versionSelector.upperBound &&
                versionSelector.lowerInclusive && versionSelector.upperInclusive)
                return VERSION_SELECTOR_SCHEME.parseSelector(versionSelector.upperBound)
        }
        // Turn + into a safe highest possible segment value of 9999
        if (versionSelector instanceof SubVersionSelector)
            return VERSION_SELECTOR_SCHEME.parseSelector(version.replace('+', '9999'))

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
            else if (compareVersions(inComing.lowerBound, existing.selector) > 0)
                return inComingStr
            else
                return existingStr
        }
        else {
            if (existing.accept(inComing.selector))
                return inComingStr
            else if (compareVersions(inComing.selector, existing.upperBound) > 0)
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