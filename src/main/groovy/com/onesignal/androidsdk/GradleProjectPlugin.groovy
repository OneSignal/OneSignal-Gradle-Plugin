package com.onesignal.androidsdk

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.ResolvedConfiguration
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

class InternalUtils {
    // standard deep copy implementation
    static Object deepcopy(orig) {
        def bos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(bos)
        oos.writeObject(orig); oos.flush()
        def bin = new ByteArrayInputStream(bos.toByteArray())
        def ois = new ObjectInputStream(bin)
        return ois.readObject()
    }
}

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

    static def versionGroupAligns
    static Project project

    @Override
    void apply(Project inProject) {
        project = inProject
        versionGroupAligns = InternalUtils.deepcopy(VERSION_GROUP_ALIGNS)

        project.configurations.all { configuration ->

            project.afterEvaluate {
                generateHighestVersionsForGroups(configuration)
                doResolutionStrategy(configuration)
            }

            // Android Specific task
            // This allows aligning of <buildType>CompileClasspath tasks
            project.dependencies {
                generateHighestVersionsForGroups(configuration)

                // ConfigurationInternal.InternalState.ARTIFACTS_RESOLVED
                if (configuration.getResolvedState() == 2)
                    return

                doResolutionStrategy(configuration)
            }
        }
    }

    static void doResolutionStrategy(Object configuration) {
        configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            if (!isInGroupAlignList(details))
                return

            def toVersion = finalAlignmentRules()[details.requested.group]['version']
            overrideVersion(project, details, toVersion)
        }
    }

    static void compileSdkVersionAlign(Project project, def versionOverride) {
        if (!versionOverride['compileSdkVersionAlign'])
            return;

        def curCompileSdkVersion = project.android.compileSdkVersion.split('-')[1].toInteger()
        def firstPartVersion = versionOverride['version'].split('\\.').first().toInteger();
        if (curCompileSdkVersion < firstPartVersion)
            versionOverride['version'] = "${curCompileSdkVersion}.+"
    }

    static void overrideVersion(Project project, DependencyResolveDetails details, String resolvedVersion) {
        if (resolvedVersion == '0.0.0')
            return

        details.useVersion(resolvedVersion)

        def modName = "${details.requested.group}:${details.requested.name}"
        def versionsMsg = "'${details.requested.version}' to '${resolvedVersion}'"
        def msg = "${modName} overridden from ${versionsMsg}"
        project.logger.warn("OneSignalProjectPlugin: ${msg}");
    }

    static boolean isInGroupAlignList(def details) {
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
        project.logger.debug("OneSignalProjectPlugin: FINAL PART 1: ${versionGroupAligns}")

        def finalVersionGroupAligns = InternalUtils.deepcopy(versionGroupAligns)
        alignAcrossGroups(finalVersionGroupAligns);
        updateMockVersionsIntoGradleVersions(finalVersionGroupAligns);

        project.logger.debug("OneSignalProjectPlugin: FINAL PART 2: ${finalVersionGroupAligns}")
        return finalVersionGroupAligns
    }

    static void alignAcrossGroups(def finalVersionGroupAligns) {
        def highestVersion = getHighestVersion(
            finalVersionGroupAligns['com.google.android.gms']['version'],
            finalVersionGroupAligns['com.google.firebase']['version']
        )

        finalVersionGroupAligns['com.google.android.gms']['version'] = highestVersion
        finalVersionGroupAligns['com.google.firebase']['version'] = highestVersion
    }

    static void updateMockVersionsIntoGradleVersions(def finalVersionGroupAligns) {
        finalVersionGroupAligns.each { group ->
            compileSdkVersionAlign(project, group.value)

            // Mock latest into Gradle latest
            if (group.value['version'] == '9999.9999.9999')
                group.value['version'] = '+'

            group.value['version'] = group.value['version'].replace('9999', '+')
            // TODO: This shouldn't be needed.
            //   This is an indication of extra unneeded calls to generateHighestVersionsForGroups
            group.value['version'] = group.value['version'].replace(".+.+", ".+")
        }
    }

    static void generateHighestVersionsForGroups(def configuration) {
        if (configuration.name.endsWith('Copy'))
            return

        def configCopy = configuration.copy()
        // Gradle 2.14.1 check
        if (configCopy.hasProperty('canBeResolved'))
            configCopy.canBeResolved = true

        configCopy.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            if (!isInGroupAlignList(details))
                return

            def defaultVersionComparator = new DefaultVersionComparator()
            def defaultVersionSelectorScheme  = new DefaultVersionSelectorScheme(defaultVersionComparator)
            def parsedVersion = defaultVersionSelectorScheme.parseSelector(details.requested.version)

            def highVersion = null
            if (parsedVersion instanceof ExactVersionSelector)
                highVersion = details.requested.version
            else if (parsedVersion instanceof VersionRangeSelector) {
                if (parsedVersion.upperInclusive)
                    highVersion = parsedVersion.upperBound
                else {
                    def parts = parsedVersion.upperBound.split('\\.')
                    if (parts.last() != '0') {
                        def lastDigit = parts.last().toInteger() - 1
                        parts[parts.size() - 1] = lastDigit.toString()
                    }
                    else {
                        def secondToLastDigit = parts[parts.size() - 2].toInteger() - 1
                        if (secondToLastDigit == -1) {
                            def firstDigit = parts.first().toInteger() - 1
                            parts[0] = firstDigit.toString()
                            parts[1] = '9999'
                        }
                        else
                            parts[parts.size() - 2] = secondToLastDigit.toString()
                        parts[parts.size() - 1] = '9999'
                    }
                    highVersion = parts.join('.')
                }
            }
            else if (parsedVersion instanceof SubVersionSelector)
                highVersion = details.requested.version.replace('+', '9999')
            else if (parsedVersion instanceof LatestVersionSelector)
                highVersion = '9999.9999.9999'
            else
                project.logger.error("OneSignalProjectPlugin: Unkown VersionSelector: ${VersionSelector}")

            def curOverrideVersion = versionGroupAligns[details.requested.group]['version']
            def compareVersionResult = compareVersions(highVersion, curOverrideVersion)
            if (compareVersionResult > 0)
                versionGroupAligns[details.requested.group]['version'] = highVersion
        }

        // Triggers configCopy.resolutionStrategy.eachDependency
        ResolvedConfiguration resolvedConfiguration = configCopy.getResolvedConfiguration()
        resolvedConfiguration.getResolvedArtifacts();
    }
}