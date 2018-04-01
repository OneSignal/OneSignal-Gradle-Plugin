package com.onesignal.androidsdk

import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder

class GradleTestTemplate {

    static TemporaryFolder testProjectDir
    static String buildFileStr
    static File buildFile

    static def gradleVersions = [:]
    static def buildArgumentSets = [:]

    static def defaultBuildParams = [
        compileSdkVersion: 26,
        targetSdkVersion: 26,
        minSdkVersion: 15
    ]

    static def setup() {
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

    static def createManifest(String path) {
        def androidManifest = testProjectDir.newFile("${path}/AndroidManifest.xml")
        androidManifest << '''\
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                      package="com.app.example">
            </manifest>
        '''.stripIndent()
    }

    static def createBuildFile(buildSections) {
        testProjectDir = new TemporaryFolder()
        testProjectDir.create()
        testProjectDir.newFolder('src', 'main')
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

                    minSdkVersion ${buildSections['minSdkVersion']}
                    versionCode 1
                    versionName '1.0'
                    multiDexEnabled true

                    manifestPlaceholders = [onesignal_app_id: '', onesignal_google_project_number: '']

                    ${buildSections['defaultConfigExtras']}
                }
                
                buildTypes {
                    debug { }
                }

                ${buildSections['androidSectionExtras']}
            }
            
            dependencies {
                ${buildSections['compileLines']}

                ${
                if (buildSections['subProjectCompileLines'] != null)
                   "compile(project(':subProject'))"
                else
                    ''
                }
            }
        """\
    }

    static def createSubProject(buildSections) {
        if (buildSections['subProjectCompileLines'] == null)
            return

        testProjectDir.newFolder('subProject', 'src', 'main')

        createManifest('subProject/src/main')
        testProjectDir.newFile('settings.gradle') << "include ':subProject'"

        def subProjectBuildFile = testProjectDir.newFile('subProject/build.gradle')
        subProjectBuildFile << subProjectBuildDotGradle(buildSections)
    }

    static def subProjectBuildDotGradle(buildSections) {
        """\
            ${buildSections['libProjectExtras']}
            apply plugin: 'com.android.library'

            android {
                compileSdkVersion ${buildSections['compileSdkVersion']}
                buildToolsVersion '26.0.2'
                 defaultConfig {
                    minSdkVersion ${buildSections['minSdkVersion']}
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
    }

    static def runGradleProject(Object buildParams) {
        def results = [:]

        gradleVersions.each { gradleVersion ->
            buildArgumentSets[gradleVersion.key].each { buildArguments ->
                if (buildParams['skipGradleVersion'] == gradleVersion.key)
                    return // == "closure break"

                if (testProjectDir != null)
                    testProjectDir.delete()

                def currentParams = defaultBuildParams + buildParams

                if (!buildParams['skipTargetSdkVersion'])
                  currentParams['defaultConfigExtras'] = "targetSdkVersion ${currentParams['targetSdkVersion']}"

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

        results
    }
}