package com.onesignal.androidsdk

import spock.lang.Specification
import org.junit.rules.TemporaryFolder

import org.gradle.testkit.runner.GradleRunner

class MainTest extends Specification {

    TemporaryFolder testProjectDir
    String buildFileStr
    File buildFile

    def gradleVersions = [
        '2.14.1': 'com.android.tools.build:gradle:2.2.3',
        '4.3': 'com.android.tools.build:gradle:3.0.0'
    ]

    def buildArgumentSets = [
        '2.14.1': [
            ['dependencies', '--configuration', 'compile'],
            ['dependencies', '--configuration', '_debugCompile']
        ],
        '4.3': [
            // compile does not work on it's own for tests since we use variant.compileConfiguration
            ['dependencies', '--configuration', 'compile'],
            ['dependencies', '--configuration', 'debugCompileClasspath'] //  '--stacktrace'
        ]
    ]

    def defaultBuildParams = [
        compileSdkVersion: 26,
        targetSdkVersion: 26
    ]

    // Before All tests
    def setup() { }

    def createBuildFile(Object buildSections) {
        testProjectDir = new TemporaryFolder()
        testProjectDir.create()
        testProjectDir.newFolder("src", "main")
        def androidManifest = testProjectDir.newFile('src/main/AndroidManifest.xml')
        androidManifest << '''\
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.app.example">
            </manifest>
        '''.stripIndent()

        buildFileStr = """\
            buildscript {
                repositories {
                      jcenter()
                      maven { url 'https://maven.google.com' }
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:XX.XX.XX'
                    ${buildSections['buildscriptDependencies']}
                }
            }
            
            plugins {
                id 'com.onesignal.androidsdk.onesignal-gradle-plugin'
            }
            
            allprojects {
                repositories {
                    jcenter()
                    maven { url 'https://maven.google.com' }
                }
            }

            apply plugin: 'com.android.application'
            ${buildSections['applyPlugins']}

            android {
                compileSdkVersion ${buildSections['compileSdkVersion']}
                buildToolsVersion '26.0.2'
                 defaultConfig {
                    applicationId 'com.app.example'

                    minSdkVersion 15
                    targetSdkVersion ${buildSections['targetSdkVersion']}
                    versionCode 1
                    versionName "1.0"
                }
                buildTypes {
                    debug { }
                }
            }
            
            dependencies {
                ${buildSections['compileLines']}
            }
        """\
    }

    def runGradleProject(Object buildParams) {
        def results = [:]

        gradleVersions.each { gradleVersion ->
            buildArgumentSets[gradleVersion.key].each { buildArguments ->
                println "gradleVersion.key: ${gradleVersion.key}"
                println "buildParams['skipGradleVersion']:  ${buildParams['skipGradleVersion']}"
                if (buildParams['skipGradleVersion'] == gradleVersion.key)
                    return // return here == "closure break"

                if (testProjectDir != null)
                    testProjectDir.delete()

                createBuildFile(defaultBuildParams + buildParams)

                buildFileStr = buildFileStr.replace('com.android.tools.build:gradle:XX.XX.XX', gradleVersion.value)
                buildFile = testProjectDir.newFile('build.gradle')
                buildFile << buildFileStr

                def result =
                    GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments(buildArguments)
                        .withPluginClasspath()
                        .withGradleVersion(gradleVersion.key)
                        .build()
                results[gradleVersion.key] = result.output

                println result.output
            }
        }

        return results
    }

    def "OneSignal version 3.6.4"() {
        def compileLines = "compile 'com.onesignal:OneSignal:3.6.4'"

        when:
        def results = runGradleProject([compileLines : compileLines])

        then:
        results.each {
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.google.android.gms:play-services-location:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.android.support:support-v4:[26.0.0,26.2.0) -> 26.1.0 (*)')
            assert it.value.contains('\\--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.1.0')
        }
    }

    def "Upgrade to compatible OneSignal SDK when targetSdkVersion is 26"() {
        def compileLines = "compile 'com.onesignal:OneSignal:3.5.+'"

        when:
        def results = runGradleProject([
            compileLines : compileLines,
            skipGradleVersion: '2.14.1'
        ])

        then:
        assert results // Asserting existence and contains 1+ entries
        results.each {
            assert it.value.contains('\\--- com.onesignal:OneSignal:3.5.+ -> 3.6.3')
            assert it.value.contains(' +--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
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

    def "Uses support library 25 when compileSdkVersion is 25"() {
        def compileLines = """\
        compile 'com.android.support:support-v4:26.0.0'
        """

        when:
        def results = runGradleProject([compileSdkVersion: 25, compileLines: compileLines])

        then:
        results.each {
            assert it.value.contains('\\--- com.android.support:support-v4:26.0.0 -> 25.4.0')
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
            assert it.value.contains('\\--- com.google.android.gms:play-services-location:11.4.0')
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
            assert it.value.contains('OneSignalProjectPlugin: com.android.support:support-v4 overridden from \'+\' to \'26.+\'')
        }
    }

    def "Aligns when using +'s"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:25.0.+'
        compile 'com.android.support:support-v4:25.+'
        """

        when:
        def results = runGradleProject([compileLines: compileLines])

        then:
        results.each {
            assert it.value.contains('+--- com.android.support:appcompat-v7:25.0.+ -> 25.4.0')
            assert it.value.contains('\\--- com.android.support:support-v4:25.+ -> 25.4.0 (*)')
        }
    }

    def "Test exclusive upper bound ending on major version"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:[25.0.0, 26.0.0)'
        compile 'com.android.support:support-v4:25.+'
        """

        when:
        def results = runGradleProject([compileLines: compileLines])

        then:
        results.each {
            assert it.value.contains('+--- com.android.support:appcompat-v7:[25.0.0, 26.0.0) -> 26.0.0-beta2')
            assert it.value.contains('\\--- com.android.support:support-v4:25.+ -> 26.0.0-beta2 (*)')
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
}