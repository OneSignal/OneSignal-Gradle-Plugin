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
        compileSdkVersion: 27,
        targetSdkVersion: 27,
        minSdkVersion: 15
    ]

    static def GRADLE_LATEST_VERSION = '4.7'
    static def GRADLE_OLDEST_VERSION = '2.14.1'

    static void setup() {
        gradleVersions = [
            (GRADLE_OLDEST_VERSION): 'com.android.tools.build:gradle:2.2.3',
            (GRADLE_LATEST_VERSION): 'com.android.tools.build:gradle:3.1.2'
        ]

        buildArgumentSets = [
            (GRADLE_OLDEST_VERSION): [
                ['dependencies', '--configuration', 'compile', '--info'],
                ['dependencies', '--configuration', '_debugCompile', '--info']
            ],
            (GRADLE_LATEST_VERSION): [
                // compile does not work on it's own for tests since we use variant.compileConfiguration
                ['dependencies', '--configuration', 'compile', '--info'],
                ['dependencies', '--configuration', 'debugCompileClasspath', '--info'] //  '--stacktrace'
            ]
        ]
    }

    static void createManifest(String path, String packageName = 'com.app.example') {
        def androidManifest = testProjectDir.newFile("${path}/AndroidManifest.xml")
        androidManifest << """\
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            package="${packageName}">
            </manifest>
        """.stripIndent()
    }

    static void createBuildFile(buildSections) {
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
                buildToolsVersion '27.0.3'
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

    static void createSubProject(buildSections) {
        if (buildSections['subProjectCompileLines'] == null)
            return

        testProjectDir.newFolder('subProject', 'src', 'main')

        createManifest('subProject/src/main', 'com.app.example.subproject')
        testProjectDir.newFile('settings.gradle') << "include ':subProject'"

        def subProjectBuildFile = testProjectDir.newFile('subProject/build.gradle')
        subProjectBuildFile << subProjectBuildDotGradle(buildSections)
    }

    static String subProjectBuildDotGradle(buildSections) {
        """\
            ${buildSections['libProjectExtras']}
            apply plugin: 'com.android.library'

            android {
                compileSdkVersion ${buildSections['compileSdkVersion']}
                buildToolsVersion '27.0.3'
                 defaultConfig {
                    minSdkVersion ${buildSections['minSdkVersion']}
                }
                buildTypes {
                    debug { }
                }
            }
            
            dependencies {
                ${buildSections['subProjectCompileLines']}
            }
        """\
    }

    static Object runGradleProject(buildParams) {
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