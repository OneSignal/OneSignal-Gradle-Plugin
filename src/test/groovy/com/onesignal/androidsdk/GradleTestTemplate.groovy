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

    // Set to '--info' or '--stacktrace' to debug issues
    static def GRADLE_LOG_LEVEL = null

    static def GRADLE_LATEST_VERSION = '4.10.2'
    static def GRADLE_OLDEST_VERSION = '2.14.1'

    static void setup() {
        gradleVersions = [
            (GRADLE_OLDEST_VERSION): 'com.android.tools.build:gradle:2.2.3',
         // (GRADLE_LATEST_VERSION): 'com.android.tools.build:gradle:3.2.1'
            (GRADLE_LATEST_VERSION): 'com.android.tools.build:gradle:3.3.0-beta01'
        ]

        buildArgumentSets = [
            (GRADLE_OLDEST_VERSION): [
                ['dependencies', '--configuration', 'compile', GRADLE_LOG_LEVEL],
                ['dependencies', '--configuration', '_debugCompile', GRADLE_LOG_LEVEL]
            ],
            (GRADLE_LATEST_VERSION): [
                // compile does not work on it's own for tests since we use variant.compileConfiguration
                ['dependencies', '--configuration', 'compile', GRADLE_LOG_LEVEL],
                ['dependencies', '--configuration', 'debugCompileClasspath', GRADLE_LOG_LEVEL] //  '--stacktrace'
//                ['dependencyInsight', '--dependency', 'com.google.firebase', '--configuration', 'debugCompileClasspath', GRADLE_LOG_LEVEL]
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

    // Don't warn on any android specific classes.
    // Used to detect build errors with out of date support library
    // Needed for the 'Find min-support for Firebase and GMS - build' Test
    static void createProguardFile() {
        def proguardFile = testProjectDir.newFile("proguard-rules.pro")
        proguardFile << '''
           -dontwarn android.content.pm.**
           -dontwarn android.app.**
           -dontwarn android.content.**
           -dontwarn android.os.**
           -dontwarn android.graphics.**
           -dontwarn com.google.protobuf.**
           -dontwarn android.view.**
           -dontwarn android.media.**
           -dontwarn android.widget.**
           -dontwarn android.text.**
           -dontwarn android.service.**
           -dontwarn android.net.**
           -dontwarn android.support.annotation.**
           
            # Had to omit the whole class. Keeps complaining about android.os.IBinder getBinder()
            #   Warning: android.support.v4.app.JobIntentService$JobServiceEngineImpl:
            #            can't find referenced method 'android.os.IBinder getBinder()'
            #              in program class android.support.v4.app.JobIntentService$JobServiceEngineImpl
            -dontwarn android.support.v4.app.JobIntentService$JobServiceEngineImpl
        '''.stripIndent()
    }

    static void createBuildFile(buildSections) {
        testProjectDir = new TemporaryFolder()
        testProjectDir.create()
        testProjectDir.newFolder('src', 'main')
        createManifest('src/main')

        buildFileStr = """\
            buildscript {
                repositories {
                    maven { url 'https://maven.google.com' }
                    jcenter()
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:XX.XX.XX'
                    ${buildSections['buildscriptDependencies']}
                }
            }
            
            // Add local copy of this OneSignal-Gradle-Plugin to test with
            plugins {
                ${buildSections['onesignalPluginId']}
            }
            
            project.ext {
                 ${buildSections['projectExts']}
            }
            
            allprojects {
                repositories {
                    maven { url 'https://maven.google.com' }
                    jcenter()
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

                lintOptions {
                    abortOnError false
                }
                
                buildTypes {
                    debug {
                        minifyEnabled true
                        proguardFiles 'proguard-rules.pro'
                    }
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

    // Create subproject only if subProjectCompileLines is set
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

                applyOneSignalGradlePlugin(currentParams)

                createBuildFile(currentParams)
                buildFileStr = buildFileStr.replace('com.android.tools.build:gradle:XX.XX.XX', gradleVersion.value)
                buildFile = testProjectDir.newFile('build.gradle')
                buildFile << buildFileStr

                createSubProject(currentParams)
                createProguardFile()

                // Test running gradle plugin last on newest version of Gradle only
//                if (gradleVersion.key == GRADLE_OLDEST_VERSION)
//                    return
//                currentParams['onesignalPluginId'] = "id 'com.onesignal.androidsdk.onesignal-gradle-plugin' apply false"
//                currentParams['applyPlugins'] = "apply plugin: 'com.onesignal.androidsdk.onesignal-gradle-plugin'"

                buildArguments.removeAll([null])

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

    // Adds plugin OneSignal-Gradle-Plugin above other plugins if no custom definition is set
    static void applyOneSignalGradlePlugin(buildSections) {
        if (buildSections['onesignalPluginId'] == null)
            buildSections['onesignalPluginId'] = "id 'com.onesignal.androidsdk.onesignal-gradle-plugin'"
    }
}