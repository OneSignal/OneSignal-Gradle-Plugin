package com.onesignal.androidsdk

import spock.lang.Specification

// These tests are slow (about 20 second each) as they are doing a full build as an end-to-end test
// Note: This test file should be run on it's own due to Gradle 7 + JVM 8 memory issues
class FullBuildTest extends Specification {
    static def GRADLE_LATEST_VERSION = GradleTestTemplate.GRADLE_LATEST_VERSION
    static def GRADLE_OLDEST_VERSION = GradleTestTemplate.GRADLE_OLDEST_VERSION

    // Before each test
    def setup() {
        GradleTestTemplate.setup()
    }

    static assertResults(results, Closure closure) {
        AssertHelpers.assertResults(results, closure)
    }

    def runGradleProject(params) {
        GradleTestTemplate.runGradleProject(params)
    }

    //   This is needed as we are making sure compile and runtime versions are not being miss aligned
    //   Asserts just a double check as Gradle or AGP fails to build when this happens
    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 26 with build tasks"() {
        GradleTestTemplate.buildArgumentSets = [
            '6.7.1':  [['build']]
        ]
        GradleTestTemplate.gradleVersions['6.7.1'] = 'com.android.tools.build:gradle:4.1.1'

        when:
        def results = runGradleProject([
            compileSdkVersion: 26,
            targetSdkVersion: 26,
            compileLines: "compile 'com.onesignal:OneSignal:3.5.+'"
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
    }

    def 'Full build on project with sub project prints no errors'() {
        GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [['build']]

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            subProjectCompileLines: """\
                compile 'com.onesignal:OneSignal:3.6.4'
            """
        ])

        then:
        assertResults(results) {
            assert !it.value.toLowerCase().contains('failure')
        }
    }

    // This test is designed to fail with new Google releases
    //   - This is a flag to know we need to make a change in this plugin to resolve version conflicts
    // Run manually search for "Warning:".
    def 'Find min-support for Firebase and GMS - build'() {
        GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [['build']]
        when:
        // Keep as '+' for latest when checking in to this fails when Google changes requirements
        def results = runGradleProject([
            'android.useAndroidX': true,
            compileLines: """
                compile 'com.google.android.gms:play-services-ads:+'
                compile 'com.google.android.gms:play-services-base:+'
                compile 'com.google.android.gms:play-services-location:+'
//              compile 'com.onesignal:OneSignal:[3.11.1, 3.99.99]' // TODO: Need to fix, see note below
            """,
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])
        // NOTE: There is a mix between AndroidX and the Android Support Library.
        //       One to this test may cause duplicated or missing classes with either of these
        //       Might need to follow the AndroidX migration guide to fix and re-add support library

        then:
        assert results // Assert success
    }

    // Run manually search for "Warning:".
    //    If a support library class is listed
    //      then the support library needs to be updated for the firebase / GMS version
    def 'test core and messaging - build'() {
        // Other run options that can be manually run to help debug the issue
        // GradleTestTemplate.buildArgumentSets[GRADLE_OLDEST_VERSION] = [['checkReleaseDuplicateClasses', '--info']]
        // GradleTestTemplate.buildArgumentSets[GRADLE_OLDEST_VERSION] = [['transformClassesAndResourcesWithProguardForDebug', '--info']]
        // GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [['dependencies', '--configuration', 'debugCompileClasspath', '--info']]
        GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [['build']] // , '--info']]
        when:
        def results = runGradleProject([
            compileLines: """
                implementation 'com.google.android.gms:play-services-base:15.0.1'
                implementation 'com.google.android.gms:play-services-basement:17.4.0'
            """,
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            'android.useAndroidX': true,
        ])

        then:
        assert results // Assert success
    }
}
