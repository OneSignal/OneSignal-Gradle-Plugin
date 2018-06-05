package com.onesignal.androidsdk

import spock.lang.Specification

class MainTest extends Specification {

    static def GRADLE_LATEST_VERSION = GradleTestTemplate.GRADLE_LATEST_VERSION
    static def GRADLE_OLDEST_VERSION = GradleTestTemplate.GRADLE_OLDEST_VERSION

    // Before each test
    def setup() {
        GradleTestTemplate.setup()
    }

    def runGradleProject(params) {
        GradleTestTemplate.runGradleProject(params)
    }

    // This version range is in the OneSignal instructions
    def "OneSignal version range test"() {
        def compileLines = "compile 'com.onesignal:OneSignal:[3.8.3, 3.99.99]'"

        when:
        def results = runGradleProject([compileLines : compileLines])

        then:
        results.each {
            assert it.value.contains('com.onesignal:OneSignal:[3.8.3, 3.99.99] -> 3.9.1')
        }
    }

    def "OneSignal version 3_6_4 With compileSdkVersion 27"() {
        def compileLines = "compile 'com.onesignal:OneSignal:3.6.4'"

        when:
        def results = runGradleProject([compileSdkVersion: 27, compileLines : compileLines])

        then:
        results.each {
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
        results.each {
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
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

    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 26"() {
        when:
        def results = runGradleProject([
            compileSdkVersion: 26,
            compileLines : "compile 'com.onesignal:OneSignal:3.5.+'",
            skipGradleVersion: GRADLE_OLDEST_VERSION // This check requires AGP 3.0.1+ which requires Gradle 4.1+
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert it.value.contains('--- com.onesignal:OneSignal:3.5.+ -> 3.6.3')
            assert it.value.contains(' +--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
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
        results.each {
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
        results.each {
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
        results.each {
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
        results.each {
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
        results.each {
            assert it.value.contains('com.google.android.gms:play-services-gcm:12.0.1 -> 11.8.0')
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
        results.each {
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
        results.each {
            assert it.value.contains('com.android.support:support-v4 overridden from \'+\' to \'27.+\'')
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
        results.each {
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
        results.each {
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
        results.each {
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
        results.each {
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
            ['dependencies', '--configuration', 'compile', '--info'],
            ['dependencies', '--configuration', '_sandboxDebugCompile', '--info']
        ]

        GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [
            ['dependencies', '--configuration', 'compile', '--info'],
            ['dependencies', '--configuration', 'sandboxDebugCompileClasspath', '--info']
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
        results.each {
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
        results.each {
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
        }
    }

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
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert it.value.contains('com.android.support:cardview-v7:[26.0.0, 27.1.0) -> 27.0.2')
            assert it.value.contains('com.android.support:support-v4:25.+ -> 27.0.2 (*)')
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
            compileLines : compileLines,
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
        results.each {
            // firebase-auth depends on play-services-base:[15.0.1, 16.0.0) so this bump to 15.0.1 is expected
            assert it.value.contains('com.google.android.gms:play-services-base:15.0.0 -> 15.0.1')
            // This will be unchanged
            assert it.value.contains('com.google.firebase:firebase-auth:15.1.0\n')
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
            // Ensure versions are unchanged
            assert it.value.contains('com.google.firebase:firebase-ads:15.0.0\n')
            assert it.value.contains('com.google.firebase:firebase-perf:15.2.0\n')
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert it.value.contains("OneSignalProjectPlugin: com.google.android.gms:play-services-gcm overridden from '12.0.1' to '[15.0.0, 16.0.0)'")
            // The range should result in the highest available exact version in the range
            assert it.value.contains('com.google.android.gms:play-services-gcm:12.0.1 -> 15.0.1')

            // This will be unchanged
            assert it.value.contains('com.google.android.gms:play-services-analytics:15.0.2\n')
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert it.value.contains("OneSignalProjectPlugin: com.google.firebase:firebase-messaging overridden from '[10.2.1, 12.1.0)' to '[15.0.0, 16.0.0)'")
            // The range should result in the highest available exact version in the range
            assert it.value.contains('com.google.firebase:firebase-messaging:[10.2.1, 12.1.0) -> 15.0.2')

            // These should remain unchanged
            assert it.value.contains('com.google.android.gms:play-services-analytics:15.0.2\n')
            assert it.value.contains('com.google.firebase:firebase-auth:15.1.0\n')
        }
    }


    // Note: Slow 20 second test, this is doing a full build
    //   This is needed as we are making sure compile and runtime versions are not being miss aligned
    //   Asserts just a double check as Gradle or AGP fails to build when this happens
    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 26 with build tasks"() {
        GradleTestTemplate.buildArgumentSets[GRADLE_LATEST_VERSION] = [['build', '--info']]

        when:
        def results = runGradleProject([
            compileSdkVersion: 26,
            targetSdkVersion: 26,
            compileLines: "compile 'com.onesignal:OneSignal:3.5.+'",
            skipGradleVersion: GRADLE_OLDEST_VERSION
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert it.value.contains("com.onesignal:OneSignal overridden from '3.5.+' to '3.6.3'")
            // 25.2.0 comes from; + com.onesignal:OneSignal:3.6.3 ->
            //                    |- + com.google.android.gms:play-services-basement:[10.2.1,11.3.0) -> 11.2.2 ->
            //                    \---- + com.android.support:25.2.0
            assert it.value.contains("com.android.support:support-v4 overridden from '25.2.0' to '[26.0.0,26.2.0['")
        }
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
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert !it.value.toLowerCase().contains('failure')
        }
    }
}
