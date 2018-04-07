package com.onesignal.androidsdk

import spock.lang.Specification

class MainTest extends Specification {

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
            assert it.value.contains('com.onesignal:OneSignal:[3.8.3, 3.99.99] -> 3.8.3')
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

        // # Version Range with Exact versions
        // ## Between range = Narrowing Lower
        assertAcceptedOrIntersectVersion('1.5.0', '[1.0.0, 1.99.99]', '[1.5.0, 1.99.99]')
        // ## Above range = Expand Upper
        assertAcceptedOrIntersectVersion('3.0.0', '[1.0.0, 1.99.99]', '[1.0.0, 3.0.0]')
        // ## Below range = Keep range
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
        // ## Plus at range = Replace lower
        assertAcceptedOrIntersectVersion('1.+', '[1.0.0, 2.99.99)', '[1.+, 2.99.99)')
        // ## Plus below range = Keep range - Same as Exact
        assertAcceptedOrIntersectVersion('1.+', '[2.0.0, 2.99.99)', '[2.0.0, 2.99.99)')
        // ## Plus above range = Expand upper - Same as Exact
        assertAcceptedOrIntersectVersion('3.0.+', '[1.0.0, 1.99.99)', '[1.0.0, 3.0.+]')
        // ## Plus in range = Narrow lower - Same as Exact
        assertAcceptedOrIntersectVersion('1.5.+', '[1.0.0, 1.99.99)', '[1.5.+, 1.99.99)')


        assertAcceptedOrIntersectVersion('[26.0.0, 27.1.0)', '25.+', '[26.0.0, 27.1.0)')
    }


      def "GradleProjectPlugin test lowerMaxVersion"() {
          // Used to
          given:
          assert GradleProjectPlugin.lowerMaxVersion("[26.0.0, 27.1.0)", "27.+") == '[26.0.0, 27.1.0)'
      }

    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 26"() {
        when:
        def results = runGradleProject([
                compileLines : "compile 'com.onesignal:OneSignal:3.5.+'",
                skipGradleVersion: '2.14.1'
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
            skipGradleVersion: '2.14.1'
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert it.value.contains('--- com.onesignal:OneSignal:3.6.+ -> 3.7.1')
            // Note: If you get 11.2.2 instead of 11.6.2 then this plugin didn't do a 2nd pass as 3.7.1
            assert it.value.contains(' +--- com.google.android.gms:play-services-gcm:[10.2.1, 11.7.0) -> 11.6.2')
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
            assert it.value.contains('+--- com.android.support:appcompat-v7:25.0.0 -> 26.1.0')
        }
    }

    def "OneSignal 3_8_3 with play games"() {
        def compileLines = """\
            compile 'com.onesignal:OneSignal:[3.8.3, 3.99.99]'
            compile 'com.google.android.gms:play-services-games:11.8.0'
        """

        when:
        def results = runGradleProject([compileLines: compileLines])

        then:
        results.each {
            // TODO: Since we are including 11.8.0 it should be drop to [10.2.1, 11.8.0]????
            assert it.value.contains('com.google.android.gms:play-services-gcm:[10.2.1, 12.1.0) -> 12.0.1')
            assert it.value.contains('com.google.android.gms:play-services-games:11.8.0 -> 12.0.1')
        }
    }

    def "Uses support library 25 when compileSdkVersion is 25"() {
        def compileLines = "compile 'com.android.support:support-v4:26.0.0'"

        when:
        def results = runGradleProject([compileSdkVersion: 25, compileLines: compileLines])

        then:
        results.each {
            assert it.value.contains('--- com.android.support:support-v4:26.0.0 -> 25.4.0')
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
            assert it.value.contains('com.android.support:appcompat-v7:[25.0.0, 26.0.0) -> 26.0.0-beta2')
            assert it.value.contains('com.android.support:support-v4:25.+ -> 26.0.0-beta2')
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
            compile(project(path: 'subProject'))
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
            assert it.value.contains('+--- com.android.support:support-v4:[26.0.0,26.2.0) -> 26.1.0 (*)')
            assert it.value.contains('+--- com.android.support:appcompat-v7:25.0.0 -> 26.1.0')
            assert it.value.contains('\\--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.1.0')
        }
    }

    // Need to add compat code for older gradle version.
    def "Ensure flavors work on Gradle 3_3 and latest"() {
        GradleTestTemplate.gradleVersions['3.3'] = 'com.android.tools.build:gradle:2.3.3'

        GradleTestTemplate.buildArgumentSets.remove('2.14.1')

        GradleTestTemplate.buildArgumentSets['3.3'] = [
            ['dependencies', '--configuration', 'compile', '--info'],
            ['dependencies', '--configuration', '_sandboxDebugCompile', '--info']
        ]

        GradleTestTemplate.buildArgumentSets['4.6'] = [
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
            skipGradleVersion: '2.14.1',
            compileLines : "compile 'com.onesignal:OneSignal:3.6.4'",
            subProjectCompileLines: """\
                compile 'com.android.support:cardview-v7:[26.0.0, 27.1.0)'
                compile 'com.android.support:support-v4:25.+'
            """,
            libProjectExtras: "apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'"
        ])

        then:
        results.each {
            assert it.value.contains('com.android.support:cardview-v7:[26.0.0, 27.1.0) -> 27.0.2')
            assert it.value.contains('com.android.support:support-v4:25.+ -> 27.0.2 (*)')
        }
    }

    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 26 with build tasks"() {
        GradleTestTemplate.buildArgumentSets['4.6'] = [
            ['build', '--info']
        ]

        when:
        def results = runGradleProject([
            compileLines : "compile 'com.onesignal:OneSignal:3.5.+'",
            skipGradleVersion: '2.14.1'
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert it.value.contains("com.onesignal:OneSignal overridden from '3.5.+' to '3.6.3'")
            assert it.value.contains("com.android.support:support-v4 overridden from '25.2.0' to '[26.1.0,26.2.0['")
        }
    }
}