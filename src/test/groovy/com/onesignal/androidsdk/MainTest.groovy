package com.onesignal.androidsdk

import spock.lang.Specification
import org.junit.rules.TemporaryFolder

import org.gradle.testkit.runner.GradleRunner

class MainTest extends Specification {

    TemporaryFolder testProjectDir
    String buildFileStr
    File buildFile

    def gradleVersions = [:]
    def buildArgumentSets = [:]

    def defaultBuildParams = [
        compileSdkVersion: 26,
        targetSdkVersion: 26
    ]

    // Before each test
    def setup() {
        gradleVersions = [
            '2.14.1': 'com.android.tools.build:gradle:2.2.3',
            '4.4': 'com.android.tools.build:gradle:3.0.1'
        ]

        buildArgumentSets = [
            '2.14.1': [
                ['dependencies', '--configuration', 'compile', '--info'],
                ['dependencies', '--configuration', '_debugCompile', '--info']
            ],
            '4.4': [
                // compile does not work on it's own for tests since we use variant.compileConfiguration
                ['dependencies', '--configuration', 'compile', '--info'],
                ['dependencies', '--configuration', 'debugCompileClasspath', '--info'] //  '--stacktrace'
            ]
        ]
    }

    def createManifest(String path) {
        def androidManifest = testProjectDir.newFile("${path}/AndroidManifest.xml")
        androidManifest << '''\
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.app.example">
            </manifest>
        '''.stripIndent()
    }

    def createBuildFile(Object buildSections) {
        testProjectDir = new TemporaryFolder()
        testProjectDir.create()
        testProjectDir.newFolder("src", "main")
        createManifest('src/main')

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

                ${buildSections['androidSectionExtras']}
            }
            
            dependencies {
                ${buildSections['compileLines']}
            }
        """\
    }

    def createSubProject(Object buildSections) {
        if (buildSections['subProjectCompileLines'] == null)
            return

        testProjectDir.newFolder('subProject', "src", "main")

        createManifest('subProject/src/main')
        testProjectDir.newFile('settings.gradle') << "include ':subProject'"

        def subProjectBuildFile = testProjectDir.newFile('subProject/build.gradle')

        def subBuildFileStr = """\
            apply plugin: 'com.android.library'

            android {
                compileSdkVersion ${buildSections['compileSdkVersion']}
                buildToolsVersion '26.0.2'
                 defaultConfig {
                    minSdkVersion 15
                    targetSdkVersion ${buildSections['targetSdkVersion']}
                }
                buildTypes {
                    main { }
                    debug { }
                }
            }
            
            dependencies {
                ${buildSections['subProjectCompileLines']}
            }
        """\

        subProjectBuildFile << subBuildFileStr
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

                def currentParams = defaultBuildParams + buildParams

                createBuildFile(currentParams)
                buildFileStr = buildFileStr.replace('com.android.tools.build:gradle:XX.XX.XX', gradleVersion.value)
                buildFile = testProjectDir.newFile('build.gradle')
                buildFile << buildFileStr

                createSubProject(currentParams)

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
        def compileLines = "compile 'com.android.support:support-v4:26.0.0'"

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

    def "Test with sub project"() {
        def compileLines = """\
        compile 'com.onesignal:OneSignal:3.6.4'
        compile 'com.android.support:appcompat-v7:25.0.0'
        compile 'com.android.support:support-v4:26.0.0'
        compile(project(path: "subProject"))
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

    def "Ensure flavors work on Gradle 3_3 and latest"() {
        gradleVersions['3.3'] = 'com.android.tools.build:gradle:2.3.3'

        buildArgumentSets.remove('2.14.1')

        buildArgumentSets['3.3'] = [
            ['dependencies', '--configuration', 'compile', '--info'],
            ['dependencies', '--configuration', '_sandboxDebugCompile', '--info']
        ]

        buildArgumentSets['4.4'] = [
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
            assert it.value.contains('\\--- com.android.support:appcompat-v7:25.0.0 -> 26.1.0')
            assert it.value.contains('\\--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.1.0')
        }
    }
}