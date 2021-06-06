package com.onesignal.androidsdk

import spock.lang.Specification

class MainTest extends Specification {

    static def GRADLE_LATEST_VERSION = GradleTestTemplate.GRADLE_LATEST_VERSION
    static def GRADLE_OLDEST_VERSION = GradleTestTemplate.GRADLE_OLDEST_VERSION

    static def NEW_LINE = System.getProperty('line.separator')

    // Before each test
    def setup() {
        GradleTestTemplate.setup()
    }

    def runGradleProject(params) {
        GradleTestTemplate.runGradleProject(params)
    }

    static assertResults(results, Closure closure) {
        // 1.Ensure one or more results exist
        assert results

        // 2. Ensure we don't have any failures
        results.each {
            assert !it.value.contains('FAILED')
        }

        // 3. Run test specific asserts
        results.each {
            closure(it)
        }
    }

    // This version range is in the OneSignal instructions
    def "OneSignal version range test"() {
        def compileLines = "compile 'com.onesignal:OneSignal:[3.8.3, 3.99.99]'"

        when:
        def results = runGradleProject([compileLines : compileLines])

        then:
        assertResults(results) {
            assert it.value.contains('com.onesignal:OneSignal:[3.8.3, 3.99.99] -> 3.16.0')
        }
    }

    def "OneSignal version 3_6_4 With compileSdkVersion 27"() {
        def compileLines = "compile 'com.onesignal:OneSignal:3.6.4'"

        when:
        def results = runGradleProject([compileSdkVersion: 27, compileLines : compileLines])

        then:
        assertResults(results) {
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.google.android.gms:play-services-location:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.android.support:support-v4:[26.0.0,26.2.0) -> 26.1.0 (*)')
            assert it.value.contains('\\--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.1.0')
        }
    }

    def "OneSignal version 3_6_4 with compileSdkVersion 26"() {
        def compileLines = "compile 'com.onesignal:OneSignal:3.6.4'"

        when:
        def results = runGradleProject([compileSdkVersion: 26, compileLines : compileLines])

        then:
        assertResults(results) {
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.google.android.gms:play-services-location:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.android.support:support-v4:[26.0.0,26.2.0) -> 26.1.0 (*)')
            assert it.value.contains('\\--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.1.0')
        }
    }


    // OneSignal 3.7.0 had it's dependencies defined as inclusive on both ends
    //   which caused it to use the highest of the range 11.6.99 which isn't a real version
    // Testing Issue #14
    // https://github.com/OneSignal/OneSignal-Gradle-Plugin/issues/14
    def "OneSignal version 3_7_0"() {
        def compileLines = "compile 'com.onesignal:OneSignal:3.7.0'"

        when:
        def results = runGradleProject([compileLines : compileLines])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.android.gms:play-services-gcm:[10.2.1, 11.6.99] -> 11.6.2')
            assert it.value.contains('com.google.android.gms:play-services-location:[10.2.1, 11.6.99] -> 11.6.2')
            assert it.value.contains('com.android.support:support-v4:[26.0.0, 27.0.99] -> 27.0.2')
            assert it.value.contains('com.android.support:customtabs:[26.0.0, 27.0.99] -> 27.0.2')
        }
    }

    def "GMS pining when in version range - flat project"() {
        def compileLines = """\
            compile 'com.onesignal:OneSignal:3.8.3'
            compile 'com.google.android.gms:play-services-base:11.4.0'
        """

        when:
        def results = runGradleProject([compileLines : compileLines])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.android.gms:play-services-gcm:[10.2.1, 12.1.0) -> 11.4.0')
        }
    }


    def "GMS pining when in version range - OneSignal in sub project"() {
        def compileLines = """\
            compile 'com.google.android.gms:play-services-base:11.4.0'
        """

        when:
        def results = runGradleProject([
            compileLines : compileLines,
            subProjectCompileLines: "compile 'com.onesignal:OneSignal:3.8.3'"
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.android.gms:play-services-gcm:[10.2.1, 12.1.0) -> 11.4.0')
        }
    }

    def "GMS pining when in version range - GMS in sub project - Gradle 2_14_1"() {
        def compileLines = """\
            compile 'com.onesignal:OneSignal:3.8.3'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_LATEST_VERSION,
            compileLines : compileLines,
            subProjectCompileLines: "compile 'com.google.android.gms:play-services-gcm:11.4.0'"
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.android.gms:play-services-gcm:[10.2.1, 12.1.0) -> 11.4.0')
        }
    }

    def "GMS pining when in version range - GMS in sub project - Gradle 4_7"() {
        def compileLines = """\
            compile 'com.onesignal:OneSignal:3.8.3'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines,
            subProjectCompileLines: "compile 'com.google.android.gms:play-services-games:11.4.0'"
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.android.gms:play-services-gcm:[10.2.1, 12.1.0) -> 11.4.0')
        }
    }

    // Helper to test both directions of inComing and existing versions
    static assertAcceptedOrIntersectVersion(String inComingStr, String existingStr, String expected) {
        assert GradleProjectPlugin.acceptedOrIntersectVersion(inComingStr, existingStr) == expected
        assert GradleProjectPlugin.acceptedOrIntersectVersion(existingStr, inComingStr) == expected
    }

    def "GradleProjectPlugin version align core tests"() {
        // NOTE: Must keep given: here for tests to run!
        given:
        // # Both Exact Versions test
        assertAcceptedOrIntersectVersion('1.0.0', '2.0.0', '2.0.0')

        // # Latest & SubVersionSelector
        assertAcceptedOrIntersectVersion('+', '+', '+')
        assertAcceptedOrIntersectVersion('1.+', '2.+', '2.+')
        assertAcceptedOrIntersectVersion('1.0.+', '2.0.+', '2.0.+')
        assertAcceptedOrIntersectVersion('1.+', '1.0.+', '1.+')
        assertAcceptedOrIntersectVersion('+', '1.0.0', '+')

        // # Latest with all other version types
        assertAcceptedOrIntersectVersion('+', '12.0.0', '+')
        assertAcceptedOrIntersectVersion('+', '12.0.+', '+')
        assertAcceptedOrIntersectVersion('+', '12.+', '+')
        assertAcceptedOrIntersectVersion('+', '[11.0.0, 12.0.0]', '+')
        assertAcceptedOrIntersectVersion('+', '[10.2.1, 12.1.0)', '+')

        // # Version Range & Exact versions = Use exact if in or above range
        // ## Between range = Narrowing Upper
        assertAcceptedOrIntersectVersion('1.5.0', '[1.0.0, 1.99.99]', '1.5.0')
        // ## Above range = Lock to newer exact version
        assertAcceptedOrIntersectVersion('3.0.0', '[1.0.0, 1.99.99]', '3.0.0')
        // ## Below range = Keep range. Future: Could lock to lowest number in range but;
//               A. Gradle 4.6(Latest) and older just keeps the range as is
//               B. Can't do this with exclusive lowerBound without knowing the next version candidate
        assertAcceptedOrIntersectVersion('0.5.0', '[1.0.0, 1.99.99]', '[1.0.0, 1.99.99]')
        // ## At range, making upper and lower the same - Exact version
        assertAcceptedOrIntersectVersion('2.0.0', '[1.0.0, 2.0.0]', '2.0.0')


        // # Both Version Ranges Tests
        // ## Narrowing from both directions
        assertAcceptedOrIntersectVersion('[1.0.0, 1.99.99]', '[1.5.0, 1.6.0]', '[1.5.0,1.6.0]')
        // ## Narrowing lower
        assertAcceptedOrIntersectVersion('[1.0.0, 1.99.99]', '[0.5.0, 1.5.0]', '[1.0.0,1.5.0]')
        // ## Narrowing upper
        assertAcceptedOrIntersectVersion('[1.0.0, 1.99.99]', '[1.5.0, 2.5.0]', '[1.5.0,1.99.99]')
        // ## Ranges miss = Use newer range
        assertAcceptedOrIntersectVersion('[1.0.0, 1.99.99]', '[3.0.0, 4.0.0]', '[3.0.0, 4.0.0]')

        // # Both Version Ranges - Exclusive
        // ## Narrowing from both directions
        assertAcceptedOrIntersectVersion('(1.0.0, 1.99.99)', '[1.5.0, 1.6.0]', '[1.5.0,1.6.0]')
        assertAcceptedOrIntersectVersion('(1.0.0, 1.99.99)', '(1.5.0, 1.6.0)', ']1.5.0,1.6.0[')
        assertAcceptedOrIntersectVersion('(1.0.0, 1.99.99)', '[1.5.0, 1.6.0)', '[1.5.0,1.6.0[')

        // # Latest and Version Ranges
        // ## Plus at range = Lock to lower 1.+
        assertAcceptedOrIntersectVersion('1.+', '[1.0.0, 2.99.99)', '1.+')
        // ## Plus below range = Keep range - Same as Exact
        assertAcceptedOrIntersectVersion('1.+', '[2.0.0, 2.99.99)', '[2.0.0, 2.99.99)')
        // ## Plus above range = Lock to newer version - Same as Exact
        assertAcceptedOrIntersectVersion('3.0.+', '[1.0.0, 1.99.99)', '3.0.+')
        // ## Plus in range = Lock to lower 1.5.+
        assertAcceptedOrIntersectVersion('1.5.+', '[1.0.0, 1.99.99)', '1.5.+')


        assertAcceptedOrIntersectVersion('[26.0.0, 27.1.0)', '25.+', '[26.0.0, 27.1.0)')
    }

    static assertIntersectCompat(String inComingStr, String existingStr, String expected) {
        def inComing = GradleProjectPlugin.parseSelector(inComingStr)
        def existing = GradleProjectPlugin.parseSelector(existingStr)

        assert VersionCompatHelpers.intersect(inComing, existing)?.selector == expected
        assert VersionCompatHelpers.intersect(inComing, existing)?.selector == expected
    }

    // Gradle 2.14.1 compat unit test
    def "GradleProjectPlugin test intersectCompat"() {
        // NOTE: Must keep given: here for tests to run!
        given:
        assertIntersectCompat('[1.0.0, 1.99.99]', '[1.5.0, 1.6.0]', '[1.5.0,1.6.0]')
        // ## Narrowing lower
        assertIntersectCompat('[1.0.0, 1.99.99]', '[0.5.0, 1.5.0]', '[1.0.0,1.5.0]')
        // ## Narrowing upper
        assertIntersectCompat('[1.0.0, 1.99.99]', '[1.5.0, 2.5.0]', '[1.5.0,1.99.99]')
        // ## Ranges miss = return null
        assertIntersectCompat('[1.0.0, 1.99.99]', '[3.0.0, 4.0.0]', null)

        // # Both Version Ranges - Exclusive
        // ## Narrowing from both directions
        assertIntersectCompat('(1.0.0, 1.99.99)', '[1.5.0, 1.6.0]', '[1.5.0,1.6.0]')
        assertIntersectCompat('(1.0.0, 1.99.99)', '(1.5.0, 1.6.0)', ']1.5.0,1.6.0[')
        assertIntersectCompat('(1.0.0, 1.99.99)', '[1.5.0, 1.6.0)', '[1.5.0,1.6.0[')
    }

    def "GradleProjectPlugin test lowerMaxVersion"() {
        given:
        assert GradleProjectPlugin.lowerMaxVersion("[26.0.0, 27.1.0)", "27.+") == '[26.0.0, 27.1.0)'
    }

    def 'isAGPVersionOlderThan should thrown when getAGPVersion is null'() {
        given:
        GradleProjectPlugin.isAGPVersionOlderThan(null, '3.0.0')
    }

    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 26"() {
        when:
        def results = runGradleProject([
            compileSdkVersion: 26,
            compileLines : "compile 'com.onesignal:OneSignal:3.5.+'",
            skipGradleVersion: GRADLE_OLDEST_VERSION // This check requires AGP 3.0.1+ which requires Gradle 4.1+
        ])

        then:
        assertResults(results) {
            assert it.value.contains('--- com.onesignal:OneSignal:3.5.+ -> 3.6.3')
            assert it.value.contains(' +--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
        }
    }

    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 29"() {
        when:
        def results = runGradleProject([
            compileSdkVersion: 29,
            compileLines : """\
                compile 'com.onesignal:OneSignal:3.5.+'
                compile 'com.android.support:support-fragment:28.0.0'
            """,
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        // TODO: Results should ALWAYS run a generic FAILED search check to ensure that there isn't any other issues.
        assertResults(results) {
            assert it.value.contains('--- com.onesignal:OneSignal:3.5.+ -> 3.7.0')
            assert it.value.contains(' +--- com.google.android.gms:play-services-gcm:[10.2.1, 11.6.99] -> 11.6.2')
        }
    }

    def "Upgrade to compatible OneSignal SDK when firebase-iid:20.1.1 is used"() {
        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            compileLines : """\
                implementation 'com.onesignal:OneSignal:3.0.0'
                implementation 'com.google.firebase:firebase-iid:20.1.1'
            """,
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assertResults(results) {
            assert it.value.contains('--- com.onesignal:OneSignal:3.0.0 -> 3.13.0')
        }
    }

    def "Upgrade to compatible OneSignal SDK when firebase-iid:20.1.6 is used"() {
        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            compileLines : """\
                implementation 'com.onesignal:OneSignal:3.0.0'
                implementation 'com.google.firebase:firebase-iid:20.1.6'
            """,
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assertResults(results) {
            assert it.value.contains('--- com.onesignal:OneSignal:3.0.0 -> 3.13.0')
        }
    }

    def 'Works with Jetifier and picks correct version AndroidX version'() {
        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            'android.enableJetifier': true,
            compileLines: """
                implementation 'com.google.android.gms:play-services-ads:18.1.0'
                implementation 'com.android.support:cardview-v7:[26.0.0, 27.2.0)'
            """,
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.android.support:cardview-v7:[26.0.0, 27.2.0) -> androidx.cardview:cardview:1.0.0')
        }
    }

    def "Upgrade to compatible OneSignal SDK when using Android Support library rev 27"() {
        when:
        def results = runGradleProject([
            compileLines : """\
                compile 'com.onesignal:OneSignal:3.6.+'
                compile 'com.android.support:support-v4:27.0.0'
            """,
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assertResults(results) {
            assert it.value.contains('--- com.onesignal:OneSignal:3.6.+ -> 3.7.0')
            // Note: If you get 11.2.2 instead of 11.6.2 then this plugin didn't do a 2nd pass as 3.7.0
            assert it.value.contains(' +--- com.google.android.gms:play-services-gcm:[10.2.1, 11.6.99] -> 11.6.2')
        }
    }

    def "Aligns support library"() {
        def compileLines = """\
            compile 'com.android.support:appcompat-v7:25.0.0'
            compile 'com.android.support:support-v4:26.0.0'
            compile 'com.onesignal:OneSignal:3.6.4'
        """

        when:
        def results = runGradleProject([compileLines: compileLines])

        then:
        assertResults(results) {
            assert it.value.contains('+--- com.android.support:appcompat-v7:25.0.0 -> 26.0.0')
        }
    }

    def "OneSignal 3_8_3 with play games"() {
        def compileLines = """\
            compile 'com.onesignal:OneSignal:3.8.3'
            compile 'com.google.android.gms:play-services-games:11.8.0'
        """

        when:
        def results = runGradleProject([compileLines: compileLines])

        then:
        assertResults(results) {
            // Allow project's com.google.android.gms version limit the range of OneSignal's dependencies
            assert it.value.contains('com.google.android.gms:play-services-gcm:[10.2.1, 12.1.0) -> 11.8.0')
            // No override
            assert it.value.contains('com.google.android.gms:play-services-games:11.8.0')
        }
    }

    def 'Uses support library 25 when compileSdkVersion is 25'() {
        when:
        def results = runGradleProject([
            compileSdkVersion: 25,
            compileLines: "compile 'com.android.support:support-v4:26.0.0'"
        ])

        then:
        assertResults(results) {
            assert it.value.contains('--- com.android.support:support-v4:26.0.0 -> 25.4.0')
        }
    }

    def 'Uses support library 25 when compileSdkVersion is 25 - apply onesignal plugin last'() {
        // Gradle 2.14.1 didn't support delayed plugin apply so test with 3.3
        GradleTestTemplate.gradleVersions.remove(GRADLE_OLDEST_VERSION)
        GradleTestTemplate.buildArgumentSets['3.3'] = GradleTestTemplate.buildArgumentSets[GRADLE_OLDEST_VERSION]
        GradleTestTemplate.gradleVersions['3.3'] = 'com.android.tools.build:gradle:2.2.3'

        when:
        def results = runGradleProject([
            compileSdkVersion: 25,
            compileLines: "compile 'com.android.support:support-v4:26.0.0'",
            // Delay apply so it is done after the AGP
            onesignalPluginId: "id 'com.onesignal.androidsdk.onesignal-gradle-plugin' apply false",
            applyPlugins: "apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'"
        ])

        then:
        assertResults(results) {
            assert it.value.contains('--- com.android.support:support-v4:26.0.0 -> 25.4.0')
        }
    }

    def 'Uses gms 11.8.0 when googlePlayServicesVersion equals 11.8.0 - apply onesignal plugin last'() {
        // Not used with AGP 3.0.0+
        GradleTestTemplate.gradleVersions.remove(GRADLE_LATEST_VERSION)

        // Gradle 2.14.1 didn't support delayed plugin apply so test with 3.3
        GradleTestTemplate.gradleVersions.remove(GRADLE_OLDEST_VERSION)
        GradleTestTemplate.buildArgumentSets['3.3'] = GradleTestTemplate.buildArgumentSets[GRADLE_OLDEST_VERSION]
        GradleTestTemplate.gradleVersions['3.3'] = 'com.android.tools.build:gradle:2.2.3'

        when:
        def results = runGradleProject([
            compileLines: "compile 'com.google.android.gms:play-services-gcm:12.0.1'",
            // Delay apply so it is done after the AGP
            onesignalPluginId: "id 'com.onesignal.androidsdk.onesignal-gradle-plugin' apply false",
            applyPlugins: "apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'",
            projectExts: "googlePlayServicesVersion = '11.8.0'"
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.android.gms:play-services-gcm:12.0.1 -> 11.8.0')
        }
    }

    def 'Upper limit com.google.android.gms:play-services:12.0.1'() {
        def compileLines = """\
        compile 'com.google.android.gms:play-services:12.0.1'
        compile 'com.google.android.gms:play-services-location:15.0.0'
        compile 'com.google.firebase:firebase-core:15.0.0'
        """

        when:
        def results = runGradleProject([compileLines: compileLines,
                                        skipGradleVersion: GRADLE_OLDEST_VERSION])

        then:
        assertResults(results) {
            assert it.value.contains("com.google.android.gms:play-services:12.0.1$NEW_LINE")
            assert it.value.contains('com.google.android.gms:play-services-location:15.0.0 -> 12.0.1')
            assert it.value.contains('com.google.firebase:firebase-core:15.0.0 -> 12.0.1')
        }
    }

    def "Aligns gms and firebase"() {
        def compileLines = """\
        compile 'com.google.firebase:firebase-core:11.0.0'
        compile 'com.google.android.gms:play-services-gcm:11.2.0'
        compile 'com.google.android.gms:play-services-location:11.4.0'
        """

        when:
        def results = runGradleProject([compileLines: compileLines])

        then:
        assertResults(results) {
            assert it.value.contains('+--- com.google.firebase:firebase-core:11.0.0 -> 11.4.0')
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:11.2.0 -> 11.4.0')
            assert it.value.contains('--- com.google.android.gms:play-services-location:11.4.0')
        }
    }

    def "Aligns when using LATEST"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:25.0.0'
        compile 'com.android.support:support-v4:+'
        """

        when:
        def results = runGradleProject([compileLines: compileLines])

        then:
        assertResults(results) {
            assert it.value.contains('com.android.support:support-v4:+ -> 28.0.0')
        }
    }

    def "Aligns when using +'s"() {
        def compileLines = """\
            compile 'com.android.support:appcompat-v7:25.0.+'
            compile 'com.android.support:support-v4:25.+'
        """

        when:
        // Don't increase 'com.android.support' even if compileSdkVersion is newer
        def results = runGradleProject([
            compileLines: compileLines,
            compileSdkVersion: 26
        ])

        then:
        assertResults(results) {
            assert it.value.contains('+--- com.android.support:appcompat-v7:25.0.+ -> 25.4.0')
            assert it.value.contains('--- com.android.support:support-v4:25.+ -> 25.4.0 (*)')
        }
    }

    def "Test exclusive upper bound ending on major version"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:[25.0.0, 26.0.0)'
        compile 'com.android.support:support-v4:25.+'
        """

        when:
        def results = runGradleProject([
            compileLines: compileLines,
            compileSdkVersion: 26
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.android.support:appcompat-v7:[25.0.0, 26.0.0) -> 25.4.0')
            assert it.value.contains('com.android.support:support-v4:25.+ -> 25.4.0')
        }
    }

    def "Test with 'com.google.gms.google-services' plugin"() {
        def buildParams = [
            'buildscriptDependencies': "classpath 'com.google.gms:google-services:3.1.0'",
            'compileLines': "compile 'com.google.android.gms:play-services-gcm:11.2.0'",
            'applyPlugins': "apply plugin: 'com.google.gms.google-services'"
        ]

        when:
        def results = runGradleProject(buildParams)

        then:
        // apply plugin: 'com.google.gms.google-services' adds `com.google.firebase:firebase-core:9.0.0`
        //  Making sure our plugin aligns to the match other groups.
        assertResults(results) {
            assert it.value.contains('+--- com.google.firebase:firebase-core:9.0.0 -> 11.2.0')
        }
    }

    def "Test with sub project"() {
        def compileLines = """\
            compile 'com.onesignal:OneSignal:3.6.4'
            compile 'com.android.support:appcompat-v7:25.0.0'
            compile 'com.android.support:support-v4:26.0.0'
        """

        when:
        def results = runGradleProject([
            compileLines : compileLines,
            subProjectCompileLines: "compile 'com.android.support:appcompat-v7:24.0.0'"
        ])

        // Use task 'androidDependencies' for easier debugging

        then:
        assertResults(results) {
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.google.android.gms:play-services-location:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.android.support:support-v4:[26.0.0,26.2.0) -> 26.0.0')
            assert it.value.contains('+--- com.android.support:appcompat-v7:25.0.0 -> 26.0.0')
            assert it.value.contains('\\--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.0.0')
        }
    }

    def "Ensure flavors work on Gradle 3_3 and latest"() {
        GradleTestTemplate.gradleVersions['3.3'] = 'com.android.tools.build:gradle:2.3.3'

        GradleTestTemplate.buildArgumentSets.remove(GRADLE_OLDEST_VERSION)
        GradleTestTemplate.buildArgumentSets['3.3'] = [
            ['dependencies', '--configuration', 'compile'],
            ['dependencies', '--configuration', '_sandboxDebugCompile']
        ]

        GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [
            ['dependencies', '--configuration', 'compile'],
            ['dependencies', '--configuration', 'sandboxDebugCompileClasspath']
        ]

        def compileLines = """\
        compile 'com.onesignal:OneSignal:3.6.4'
        compile 'com.android.support:appcompat-v7:25.0.0'
        """

        def androidSectionExtras = """\
        flavorDimensions 'tier'
        productFlavors {
            sandbox {
                dimension 'tier'
            }
        }
        """

        when:
        def results = runGradleProject([
            compileLines: compileLines,
            androidSectionExtras : androidSectionExtras,
        ])

        then:
        assertResults(results) {
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.google.android.gms:play-services-location:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.android.support:support-v4:[26.0.0,26.2.0) -> 26.1.0 (*)')
            assert it.value.contains('--- com.android.support:appcompat-v7:25.0.0 -> 26.1.0')
            assert it.value.contains('--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.1.0')
        }
    }

    def "support missing targetSdkVersion"() {
        // Testing that minSdkVersion is used as a fallback
        when:
        def results = runGradleProject([
            compileLines : "compile 'com.onesignal:OneSignal:3.6.4'",
            skipTargetSdkVersion: true,
            minSdkVersion: 27
        ])

        then:
        assertResults(results) {
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
        }
    }

    // TODO: Does NOT work with project.gradle.projectsEvaluated
    def "support sub library projects"() {
        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : "compile 'com.onesignal:OneSignal:3.6.4'",
            subProjectCompileLines: """\
                compile 'com.android.support:cardview-v7:[26.0.0, 27.1.0)'
                compile 'com.android.support:support-v4:25.+'
            """,
            libProjectExtras: "apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'"
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.android.support:cardview-v7:[26.0.0, 27.1.0) -> 26.1.0')
            assert it.value.contains('com.android.support:support-v4:25.+ -> 26.1.0')
        }
    }

    def 'gms and firebase 15 - Support mix minor versions keep'() {
        def compileLines = """\
            compile 'com.google.android.gms:play-services-base:15.0.0'
            compile 'com.google.firebase:firebase-auth:15.1.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            // firebase-auth depends on play-services-base:[15.0.1, 16.0.0) so this bump to 15.0.1 is expected
            assert it.value.contains('com.google.android.gms:play-services-base:15.0.0 -> 15.0.1')
            // This will be unchanged
            assert it.value.contains("com.google.firebase:firebase-auth:15.1.0$NEW_LINE")
        }
    }

    def 'Compatible with google-services Gradle Plugin 4.0.1'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:17.6.0'
            compile 'com.google.firebase:firebase-iid:18.0.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            buildscriptDependencies: "classpath 'com.google.gms:google-services:4.0.1'",
            applyPlugins: "apply plugin: 'com.google.gms.google-services'",
            compileLines : compileLines
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
    }


    def 'Compatible with google-services Gradle Plugin 4.1.0'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:17.6.0'
            compile 'com.google.firebase:firebase-iid:18.0.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            buildscriptDependencies: "classpath 'com.google.gms:google-services:4.1.0'",
            applyPlugins: "apply plugin: 'com.google.gms.google-services'",
            compileLines : compileLines
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
    }

    // Skipping test for google-services:4.2.0. Seems incompatible with our mock project setup

    def 'Compatible with google-services Gradle Plugin 4.3.0'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:17.6.0'
            compile 'com.google.firebase:firebase-iid:18.0.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            buildscriptDependencies: "classpath 'com.google.gms:google-services:4.3.0'",
            applyPlugins: "apply plugin: 'com.google.gms.google-services'",
            compileLines : compileLines
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
    }

    def 'can disable "Google Services Gradle Plugin" version checks with disableVersionCheck'() {
        // com.onesignal:OneSignal:3.15.6' defines a version range of play-services-base@[10.2.1, 16.1.99]
        // 17.3.0 is outside of that but this is ok, since enableJetifier is enabled in this case.
        // However "Google Services Gradle Plugin" thinks there is a version out of range and fails the build
        // We are ensuring this plugin sets disableVersionCheck correctly to disable it
        def compileLines = """\
            compile 'com.onesignal:OneSignal:3.15.6'
            compile 'com.google.android.gms:play-services-base:17.3.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            'android.useAndroidX': true,
            'android.enableJetifier': true,
            buildscriptDependencies: "classpath 'com.google.gms:google-services:4.3.4'",
            applyPlugins: "apply plugin: 'com.google.gms.google-services'",
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
        }
    }

    // Makes sure we can detect the AGP version if it was applied from "apply from: 'filename.gradle'" file
    // This requires a special edge case as "apply from:" doesn't not inherit the classpath from the host file.
    // See https://github.com/gradle/gradle/issues/4007 for more details.
    def 'can detect AGP version within an apply from .gradle file'() {
        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            onesignalPluginId: "id 'com.onesignal.androidsdk.onesignal-gradle-plugin' apply false",
            applyFromFileContents: "apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'"
        ])

        then:
        assertResults(results) {
            assert !it.value.contains(GradleProjectPlugin.WARNING_MSG_COULD_NOT_GET_AGP_VERSION)
        }
    }

    def 'firebase 15 - keep mixed minor versions'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-ads:15.0.0'
            compile 'com.google.firebase:firebase-perf:15.2.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            // Ensure versions are unchanged
            assert it.value.contains("com.google.firebase:firebase-ads:15.0.0$NEW_LINE")
            assert it.value.contains("com.google.firebase:firebase-perf:15.2.0$NEW_LINE")
        }
    }

    // Same as the test above but handles the afterEvaluate case. See a plugin that does this below
    // https://github.com/dpa99c/cordova-plugin-firebasex/blob/11.0.3/src/android/build.gradle#L43
    def 'can disable "Google Services Gradle Plugin" version checks with disableVersionCheck - even if added in afterEvaluate'() {
        def compileLines = """\
            compile 'com.onesignal:OneSignal:3.15.6'
            compile 'com.google.android.gms:play-services-base:17.3.0'
        """

        when:
        def results = runGradleProject([
                skipGradleVersion: GRADLE_OLDEST_VERSION,
                'android.useAndroidX': true,
                'android.enableJetifier': true,
                buildscriptDependencies: "classpath 'com.google.gms:google-services:4.3.4'",
                applyPlugins: "afterEvaluate { apply plugin: 'com.google.gms.google-services' }",
                compileLines : compileLines,
        ])

        then:
        assertResults(results) {
        }
    }

    def 'gms 15 - Support for 12 and 15 version'() {
        def compileLines = """\
            compile 'com.google.android.gms:play-services-gcm:12.0.1'
            compile 'com.google.android.gms:play-services-analytics:15.0.2'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            // The range should result in the highest available exact version in the range
            assert it.value.contains('com.google.android.gms:play-services-gcm:12.0.1 -> 15.0.1')

            // This will be unchanged
            assert it.value.contains("com.google.android.gms:play-services-analytics:15.0.2$NEW_LINE")
        }
    }


    def 'gms and firebase 15 - With OneSignal 3.9.1'() {
        def compileLines = """\
            compile 'com.onesignal:OneSignal:3.9.1'
            compile 'com.google.firebase:firebase-auth:15.1.0'
            compile 'com.google.android.gms:play-services-analytics:15.0.2'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            // The range should result in the highest available exact version in the range
            assert it.value.contains('com.google.firebase:firebase-messaging:[10.2.1, 12.1.0) -> 15.0.2')

            // These should remain unchanged
            assert it.value.contains("com.google.android.gms:play-services-analytics:15.0.2$NEW_LINE")
            assert it.value.contains("com.google.firebase:firebase-auth:15.1.0$NEW_LINE")
        }
    }

    def 'fcm 15 and crashlytics with firebase platform BOM'() {
        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : """\
                implementation platform('com.google.firebase:firebase-bom:25.4.1')
                implementation 'com.google.firebase:firebase-messaging:15.0.0'
                implementation 'com.google.firebase:firebase-crashlytics'
            """
        ])

        then:
        assertResults(results) {
            // 1. Ensure firebase-bom is allowed to upgrade firebase-messaging
            assert it.value.contains('com.google.firebase:firebase-messaging:15.0.0 -> 20.2.0')
            // 2. Ensure version from firebase-bom is used and we don't try to downgrade
            assert it.value.contains('com.google.firebase:firebase-crashlytics -> 17.0.1')
        }
    }

    def 'pre-15 firebase with firebase-bom platform 17 BOM'() {
        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : """\
                implementation platform('com.google.firebase:firebase-bom:17.0.0')
                implementation 'com.google.firebase:firebase-messaging:12.0.0'
            """
        ])

        then:
        assertResults(results) {
            // 1. Ensure firebase-bom is allowed to upgrade firebase-messaging
            assert it.value.contains('com.google.firebase:firebase-messaging:12.0.0 -> 17.5.0')
            // 2. Ensures this sub dependency get force downgraded to 12.0.0
            assert it.value.contains("com.google.firebase:firebase-measurement-connector:17.0.1${NEW_LINE}")
        }
    }

    def 'fcm 15 and crashlytics with firebase enforcedPlatform BOM'() {
        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : """\
                implementation enforcedPlatform('com.google.firebase:firebase-bom:25.4.1')
                implementation 'com.google.firebase:firebase-messaging:15.0.0'
                implementation 'com.google.firebase:firebase-crashlytics'
            """
        ])

        then:
        assertResults(results) {
            // 1. Ensure firebase-bom is allowed to upgrade firebase-messaging
            assert it.value.contains('com.google.firebase:firebase-messaging:15.0.0 -> 20.2.0')
            // 2. Ensure version from firebase-bom is used and we don't try to downgrade
            assert it.value.contains('com.google.firebase:firebase-crashlytics -> 17.0.1')
        }
    }

    def 'when firebase-core:16 and firebase-messaging:15.0.2 upgrade to firebase-messaging:17.0.0'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:15.0.2'
            compile 'com.google.firebase:firebase-core:16.0.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-messaging:15.0.2 -> 17.0.0')
        }
    }

    // TODO:*: Future, should step through all versions to discover limits automatically
    // This also covers case when firebase-core:16.0.4, as it depends on firebase-iid:17.0.3
    def 'when firebase-core:16.0.4 and firebase-messaging:15.0.2 upgrade to firebase-messaging:17.3.1'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:15.0.2'
            compile 'com.google.firebase:firebase-iid:17.0.3'
        """

        // Can enable the following to do a build with proguard to find minimum versions
        // GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [['build']] // , '--info']]

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-messaging:15.0.2 -> 17.3.3')
        }
    }

    def 'when firebase-core:16 and firebase-messaging:12.0.0 upgrade to firebase-messaging:17.0.0'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:12.0.0'
            compile 'com.google.firebase:firebase-core:16.0.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-messaging:12.0.0 -> 17.0.0')
        }
    }


    def 'when firebase-core:16 and firebase-messaging:17.1.0 keep as firebase-messaging:17.1.0'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:17.1.0'
            compile 'com.google.firebase:firebase-core:16.0.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains("com.google.firebase:firebase-messaging:17.1.0$NEW_LINE")
        }
    }

    def 'when play-services-measurement-base:15.0.4 upgrade to firebase-analytics:16.0.0'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-analytics:15.0.2'
            compile 'com.google.android.gms:play-services-measurement-base:15.0.4'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-analytics:15.0.2 -> 16.0.0')
        }
    }

    def 'when play-services-basement:17.0.0 upgrade play-services-base:17.0.0'() {
        def compileLines = """\
             implementation 'com.google.android.gms:play-services-base:15.0.1'
             implementation 'com.google.android.gms:play-services-basement:17.0.0'
        """

        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.android.gms:play-services-base:15.0.1 -> 17.0.0')
        }
    }

    def 'when play-services-basement:16.0.1 upgrade to firebase-messaging:17.3.3'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:17.0.0'
            compile 'com.google.android.gms:play-services-basement:16.0.1'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-messaging:17.0.0 -> 17.3.3')
        }
    }

    def 'when firebase-iid:18.0.0 ensure firebase-messaging:18.0.0'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:17.6.0'
            compile 'com.google.firebase:firebase-iid:18.0.0'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-messaging:17.6.0 -> 18.0.0')
        }
    }

    def 'when firebase-iid:20.1.6 ensure firebase-messaging:20.1.4'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:20.0.0'
            compile 'com.google.firebase:firebase-iid:20.1.6'
        """

        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines: compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-messaging:20.0.0 -> 20.1.4')
        }
    }

    def 'when firebase-core:16.0.9 ensure firebase-messaging:17.6.0'() {
        def compileLines = """\
            compile 'com.google.firebase:firebase-messaging:17.0.0'
            compile 'com.google.firebase:firebase-core:16.0.9'
        """

        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-messaging:17.0.0 -> 17.6.0')
        }
    }

    def 'when firebase-common:18.0.0 ensure firebase-iid:19.0.0 and a cascade firebase-messaging:18.0.0 update'() {
        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines: """
                implementation 'com.google.firebase:firebase-messaging:[10.2.1, 17.3.99]'
                implementation 'com.google.firebase:firebase-common:18.0.0'
            """
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.google.firebase:firebase-iid:18.0.0 -> 19.0.0')
            assert it.value.contains('com.google.firebase:firebase-messaging:[10.2.1, 17.3.99] -> 18.0.0')
        }
    }

    def 'when firebase-app-unity and firebase-messaging'() {
        when:
        def results = runGradleProject([
            skipGradleVersion: GRADLE_OLDEST_VERSION,
            compileLines : """\
                implementation 'com.google.firebase:firebase-messaging:17.0.0'
                implementation 'com.google.firebase:firebase-app-unity:6.2.2'
            """,
            preBuildClosure: {
                GradleTestTemplate.createM2repository('com.google.firebase', 'firebase-app-unity', '6.2.2')
            }
        ])

        then:
        assertResults(results) {
            assert it.value.contains("com.google.firebase:firebase-app-unity:6.2.2$NEW_LINE")
        }
    }

    // Note: Slow 20 second test, this is doing a full build
    //   This is needed as we are making sure compile and runtime versions are not being miss aligned
    //   Asserts just a double check as Gradle or AGP fails to build when this happens
    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 26 with build tasks"() {
        GradleTestTemplate.buildArgumentSets = [
            '6.7.1':  [['build']]
        ]
        GradleTestTemplate.gradleVersions['6.7.1'] = 'com.android.tools.build:gradle:4.2.1'

        when:
        def results = runGradleProject([
            compileSdkVersion: 26,
            targetSdkVersion: 26,
            compileLines: "compile 'com.onesignal:OneSignal:3.5.+'"
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
    }


    // Note: Slow 20 second test, this is doing a full build
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
    def 'upgrade support library to 25 when gms is 11.2.0'() {
        def compileLines = """\
            compile 'com.google.android.gms:play-services-base:11.2.0'
            compile 'com.android.support:appcompat-v7:23.0.1'
        """

        when:
        def results = runGradleProject([
            compileLines : compileLines
        ])

        then:
        assertResults(results) {
            assert it.value.contains("com.google.android.gms:play-services-base:11.2.0$NEW_LINE")
            assert it.value.contains('com.android.support:appcompat-v7:23.0.1 -> 25.1.0')
        }
    }

    def 'firebase-core latest select valid dependent module versions'() {
        def compileLines = """\
            compile "com.onesignal:OneSignal:3.9.1"
            compile "com.google.firebase:firebase-core:+"
        """

        when:
        def results = runGradleProject([
            'android.useAndroidX': true,
            compileLines : compileLines,
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assertResults(results) {
            // Checking for any fail but this is the case it is catching
            //   com.google.firebase:firebase-measurement-connector:16.0.0 -> [10.2.1,12.1.0[ FAILED
            assert !it.value.contains('FAILED')
        }
    }

    def 'When compileSdkVersion is 22 cap android.support at 22.2.1'() {
        when:
        def results = runGradleProject([
            compileSdkVersion: 22,
            compileLines: '''
                compile 'com.android.support:support-v4:25.0.0'
                compile 'com.android.support:appcompat-v7:25.0.0'
            ''',
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.android.support:support-v4:25.0.0 -> 22.2.1')
        }
    }

    def 'When compileSdkVersion is 23 cap android.support at 25.0.1'() {
        when:
        def results = runGradleProject([
            compileSdkVersion: 23,
            compileLines: '''
                compile 'com.android.support:support-v4:26.0.0'
                compile 'com.android.support:appcompat-v7:26.0.0'
            ''',
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.android.support:support-v4:26.0.0 -> 25.0.1')
        }
    }


    def 'When compileSdkVersion is 23 cap android.support at 25.0.1 even when gms 11.8.0'() {
        when:
        def results = runGradleProject([
            compileSdkVersion: 23,
            compileLines: '''
                compile 'com.android.support:support-v4:26.0.0'
                compile 'com.google.android.gms:play-services-gcm:11.8.0'
            ''',
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assertResults(results) {
            assert it.value.contains('com.android.support:support-v4:26.0.0 -> 25.0.1')
        }
    }


    // Note: Run manually to find the min compileSdkVersion for each support version
    def 'Find min-support version for compileSdkVersion'() {
        GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [['compileDebugSources']]

        when:
        def results = runGradleProject([
            compileSdkVersion: 27,
            compileLines: '''
                compile 'com.android.support:support-v4:+'
                compile 'com.android.support:appcompat-v7:+'
            ''',
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
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
