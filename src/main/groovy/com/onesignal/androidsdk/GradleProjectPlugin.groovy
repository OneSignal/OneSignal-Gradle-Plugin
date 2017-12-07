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
import org.gradle.api.invocation.Gradle

import java.util.regex.Matcher
import java.util.regex.Pattern

// This Gradle plugin automatically fixes or notifies developer of requires changes to make the
//    OneSignal Android SDK compatible with the app's project
// - Automatically aligning versions across groups
//   - com.google.android.gms, com.google.firebase, and 'com.android.support'
//     - Mixing versions of these modules leads to compile or runtime errors.
//   - compileSdkVersion is checked to make sure a compatible com.android.support version is used

// References
// - Source of Android Gradle Plugin (com.android.application)
//   https://android.googlesource.com/platform/tools/build/+/oreo-release/gradle/src/main/groovy/com/android/build/gradle/BasePlugin.groovy

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

    @Override
    void apply(Project inProject) {
        project = inProject
        versionGroupAligns = InternalUtils.deepcopy(VERSION_GROUP_ALIGNS)
        moduleVersionOverrides = [:]

        // This sections works with com.android.tools.build:gradle:3.0.0 on the debugCompileClasspath task.
        //   However not the compile task as it don't contain a variant.
        project.afterEvaluate {
            project.android.applicationVariants.all { variant ->
//                if (!variant.hasProperty('compileConfiguration'))
//                    return

                def configuration = variant.compileConfiguration
                println("variant.compileConfiguration: ${variant.class}")
                println variant.properties.toString()

                // This uses configuration.copy, however the resolves does not trigger on the copy of compileConfiguration
                generateHighestVersionsForGroups(configuration)

                doResolutionStrategy(configuration)
            }
        }

        // Adding this section triggers resolution to soon when using com.android.tools.build:gradle:3.0.0
        project.configurations.all { configuration ->
            // This Breaks "Upgrade to compatible OneSignal SDK..." but fixes a lot of other tests
//            project.afterEvaluate {
//                generateHighestVersionsForGroups(configuration)
//                doResolutionStrategy(configuration)
//            }

            // Seem to have no effect on the 3.0.0 plugin
            // Catches Android specific tasks, <buildType>CompileClasspath
//            project.dependencies {
//                generateHighestVersionsForGroups(configuration)
//                doResolutionStrategy(configuration)
//            }
        }
    }

    static void doResolutionStrategy(Object configuration) {
        configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            project.android.applicationVariants.all { variant ->
                doMinimumVersionUpgradeOnDetail(configuration, details)

                project.configurations.all { localConfiguration ->
                    generateHighestVersionsForGroups(localConfiguration)
                }

                doGroupAlignStrategyOnDetail(details)
            }
        }
    }

    static String getCurrentVariant() {
        Gradle gradle = project.getGradle()
        Pattern pattern = Pattern.compile(":assemble(.*?)(Release|Debug)")
        Matcher matcher = pattern.matcher(gradle.getStartParameter().getTaskRequests().toString())
        if (matcher.matches()) {
            // find()
            println("Current Android Varient: ${matcher.group(1)}")
            return matcher.group(1)
        }

        return null
    }


    // Notes on new 3.0.0 android gradle plugin way to do resolutionStragegy
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


    // Each variant is created from this Android Gradle method
    // private createTasksForFlavoredBuild(ProductFlavorData... flavorDataList) {

    // https://stackoverflow.com/questions/31461267/using-a-different-manifestplaceholder-for-each-build-variant
    static int getCurrentTargetSdkVersion() {
        def targetSdkVersion = 0
        project.android.applicationVariants.all { variant ->
//            println("project.android.applicationVariants.all:variant:${variant.name}")
            def mergedFlavor = variant.getMergedFlavor()
//            println("mergedFlavor:${mergedFlavor}")
//            println("mergedFlavor.targetSdkVersion: ${mergedFlavor.targetSdkVersion}")
//            println("mergedFlavor.targetSdkVersion:apiLevel: ${mergedFlavor.targetSdkVersion.apiLevel}")

//          if (variant.name.equals('debugCompileClasspath')) {
              targetSdkVersion = mergedFlavor.targetSdkVersion.apiLevel
            //  return mergedFlavor.targetSdkVersion.apiLevel
//          }
        }

        println("getCurrentTargetSdkVersion:returning:${targetSdkVersion}")
        return targetSdkVersion
    }



    static void doMinimumVersionUpgradeOnDetail(Object configuration, DependencyResolveDetails details) {
        def existingOverrider = moduleVersionOverrides["${details.requested.group}:${details.requested.name}"]
        if (existingOverrider) {
            println("${configuration}:**********************doMinimumVersionUpgradeOnDetail:existingOverrider: ${existingOverrider}")
            details.useVersion(existingOverrider)
        }

        def module = MINIMUM_MODULE_VERSIONS["${details.requested.group}:${details.requested.name}"]
        if (!module)
            return


//        // TODO: Just a temp force override
//        details.useVersion("3.6.4")
//        return

        project.android.applicationVariants.all { variant ->

            println("configuration.name: ${configuration.name}")
            println("project.android: ${project.android}")

            def curSdkVersion = getCurrentTargetSdkVersion()
            println("getCurrentTargetSdkVersion: ${curSdkVersion}")

            if (curSdkVersion == 0)
                return


            def curVersion = getVersionFromDependencyResolveDetails(details)
            def newVersion = null
            module['targetSdkVersion'].each { key, value ->

//            ApplicationVariantData appVariantData = new ApplicationVariantData(variantConfig)
//            appVariantData.variantConfiguration.targetSdkVersion

                // variantData.variantConfiguration.targetSdkVersion

                if (curSdkVersion < key)
                    return

                def compareVersionResult = compareVersions(value, newVersion ?: curVersion)
                if (compareVersionResult > 0)
                    newVersion = value
            }

            if (newVersion != curVersion && newVersion != null) {
                moduleVersionOverrides["${details.requested.group}:${details.requested.name}"] = newVersion
                println("${configuration}:@@@@@@@@@@@@@@@@@@@@@@@@@@@@@doMinimumVersionUpgradeOnDetail:newVersion: ${newVersion}")
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

        details.useVersion(resolvedVersion)
        logModuleOverride(details, resolvedVersion)
    }

    static void logModuleOverride(DependencyResolveDetails details, String resolvedVersion) {
        def modName = "${details.requested.group}:${details.requested.name}"
        def versionsMsg = "'${details.requested.version}' to '${resolvedVersion}'"
        def msg = "${modName} overridden from ${versionsMsg}"
        project.logger.warn("OneSignalProjectPlugin: ${msg}")
    }

    static boolean inGroupAlignList(def details) {
        // Only override groups we define
        def versionOverride = versionGroupAligns[details.requested.group]
        if (!versionOverride)
            return false

        // Skip modules that should not align to other modules in the group
        def omitModules = versionOverride['omitModules']
        if (omitModules && omitModules.contains(details.requested.name))
            return false
        return true
    }

    static String getHighestVersion(String version1, String version2) {
        return compareVersions(version1, version2) > 0 ? version1 : version2
    }

    static int compareVersions(String version1, String version2) {
        def versionComparator = new DefaultVersionComparator()
        return versionComparator.compare(new VersionInfo(version1), new VersionInfo(version2))
    }

    static Object finalAlignmentRules() {
        project.logger.debug("OneSignalProjectPlugin: FINAL ALIGN PART 1: ${versionGroupAligns}")

        def finalVersionGroupAligns = InternalUtils.deepcopy(versionGroupAligns)
        alignAcrossGroups(finalVersionGroupAligns)
        updateMockVersionsIntoGradleVersions(finalVersionGroupAligns)

        project.logger.debug("OneSignalProjectPlugin: FINAL ALIGN PART 2: ${finalVersionGroupAligns}")
        return finalVersionGroupAligns
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

    static void generateHighestVersionsForGroups(def configuration) {
//        println("generateHighestVersionsForGroups:configuration:${configuration}")

        def configCopy = configuration.copy()
        // canBeResolved not available on Gradle 2.14.1 and older
        if (configCopy.hasProperty('canBeResolved'))
            configCopy.canBeResolved = true

        configCopy.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            println "############configCopy.resolutionStrategy.eachDependency"
            doMinimumVersionUpgradeOnDetail(configCopy, details)

            if (!inGroupAlignList(details))
                return

            def curOverrideVersion = versionGroupAligns[details.requested.group]['version']
            def inComingVersion = getVersionFromDependencyResolveDetails(details)

            def compareVersionResult = compareVersions(inComingVersion, curOverrideVersion)
            if (compareVersionResult > 0) {
                println("${configCopy}:Updating versionGroupAligns[${details.requested.group}] to '${inComingVersion}'")
                versionGroupAligns[details.requested.group]['version'] = inComingVersion
            }
        }

        triggerResolutionStrategy(configCopy)
    }

    static void triggerResolutionStrategy(Object configuration) {
        // New
        //org.gradle.api.internal.artifacts.ivyservice.ErrorHandlingConfigurationResolver
        def resolutionResult = configuration.incoming.resolutionResult
        println "11111111111111111 resolutionResult: ${resolutionResult}"

       // org.gradle.api.internal.artifacts.configurations.DefaultConfiguration // $ConfigurationResolvableDependencies
        def artifacts = configuration.incoming.artifacts
        println " 444444444 getArtifacts: ${artifacts}"

        println "555555: ${artifacts.getArtifacts()}"
        //org.gradle.api.internal.artifacts.configurations.DefaultConfiguration // $ConfigurationArtifactCollection

        resolutionResult.allDependencies {
            println "3333333: ${it}"
        }

        // Existing trigger
        def resolvedArtifacts = configuration.resolvedConfiguration.resolvedArtifacts
        println "2222222222 ${resolvedArtifacts}"
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

        return null
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

        return parts.join('.')
    }
}