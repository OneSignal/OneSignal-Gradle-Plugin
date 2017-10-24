package com.onesignal.androidsdk

import spock.lang.Specification
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import org.gradle.testkit.runner.GradleRunner

class MainTest extends Specification {

    TemporaryFolder testProjectDir
    String buildFileStr
    File buildFile

    def gradleVersions = [
        '2.14.1': 'com.android.tools.build:gradle:2.2.3',
        '4.3-rc-2': 'com.android.tools.build:gradle:3.0.0-beta6'
    ]

    def buildArgumentSets = [
        '2.14.1': [
            ['dependencies', '--configuration', 'compile'],
            ['dependencies', '--configuration', '_debugCompile']
        ],
        '4.3-rc-2': [
            ['dependencies', '--configuration', 'compile'],
            ['dependencies', '--configuration', 'debugCompileClasspath']
        ]
    ]

    def setup() {
    }

    def createBuildFile(int compileVersion, Object buildSections) {
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
                compileSdkVersion ${compileVersion}
                buildToolsVersion '26.0.1'
                 defaultConfig {
                    applicationId 'com.app.example'

                    minSdkVersion 15
                    targetSdkVersion 26
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

    def runGradleProject(int compileVersion, Object buildParams) {
        def results = [:]

        gradleVersions.each { gradleVersion ->
            buildArgumentSets[gradleVersion.key].each { buildArguments ->
                if (testProjectDir != null)
                    testProjectDir.delete()

                createBuildFile(compileVersion, buildParams)

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
        def compileLines = """\
        compile 'com.onesignal:OneSignal:3.6.4'
        """

        when:
        def results = runGradleProject(26, ['compileLines' : compileLines])

        then:
        results.each {
            assert it.value.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.google.android.gms:play-services-location:[10.2.1,11.3.0) -> 11.2.2')
            assert it.value.contains('+--- com.android.support:support-v4:[26.0.0,26.2.0) -> 26.1.0 (*)')
            assert it.value.contains('\\--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.1.0')
        }
    }

    def "Aligns support library"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:25.0.0'
        compile 'com.android.support:support-v4:26.0.0'
        compile 'com.onesignal:OneSignal:3.6.4'
        """

        when:
        def results = runGradleProject(26, ['compileLines' : compileLines])

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
        def results = runGradleProject(25, ['compileLines' : compileLines])

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
        def results = runGradleProject(26, ['compileLines' : compileLines])

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
        def results = runGradleProject(26, ['compileLines' : compileLines])

        then:
        results.each {
            // TODO: Do we need to update this test? Let see if things changes before then...
            // Seems to be a 2 step process now:
//            configCopy.resolutionStrategy.eachDependency: details: org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencyResolveDetails@418858af
//            OneSignalProjectPlugin: com.android.support:support-v4 overridden from '+' to '25.0.0'
//            generateHighestVersionsForGroups: compile
//            configCopy.resolutionStrategy.eachDependency: details: org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencyResolveDetails@2a44975d
//            OneSignalProjectPlugin: com.android.support:support-v4 overridden from '25.0.0' to '26.+'
            assert it.value.contains('OneSignalProjectPlugin: com.android.support:support-v4 overridden from \'+\' to \'26.+\'')
        }
    }

    def "Aligns when using +'s"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:25.0.+'
        compile 'com.android.support:support-v4:25.+'
        """

        when:
        def results = runGradleProject(26, ['compileLines' : compileLines])

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
        def results = runGradleProject(26, ['compileLines' : compileLines])

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
        def results = runGradleProject(26, buildParams)

        then:
        // apply plugin: 'com.google.gms.google-services' adds `com.google.firebase:firebase-core:9.0.0`
        //  Making sure our plugin aligns to the match other groups.
        results.each {
            assert it.value.contains('+--- com.google.firebase:firebase-core:9.0.0 -> 11.2.0')
        }
    }
}