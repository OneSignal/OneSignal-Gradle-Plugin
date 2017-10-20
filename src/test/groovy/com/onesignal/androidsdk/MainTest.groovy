import spock.lang.Specification
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import org.gradle.testkit.runner.GradleRunner

class MainTest extends Specification {

    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    List<File> pluginClasspath

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def createBuildFile(compileVersion, compileLines) {
        def gradleProps = testProjectDir.newFile('gradle.properties')
        gradleProps <<'''\
            android.enableAapt2=false
        '''.stripIndent()

        testProjectDir.newFolder("src", "main")
        def androidManifest = testProjectDir.newFile('src/main/AndroidManifest.xml')
        androidManifest << '''\
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.app.example">
            </manifest>
        '''.stripIndent()

        buildFile << """\
            buildscript {
                repositories {
                      jcenter()
                      maven { url 'https://maven.google.com' }
                }
                dependencies {
                   classpath 'com.android.tools.build:gradle:3.0.0-beta6'
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
            }
            
            dependencies {
                ${compileLines}
            }
        """\
    }

    def runGradleProject() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('dependencies', '--configuration', 'compile')
            .withPluginClasspath()
            .build()
    }

    def "Aligns support library"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:25.0.0'
        compile 'com.android.support:support-v4:26.0.0'
        """

        createBuildFile(26, compileLines)

        when:
        def result = runGradleProject()

        then:
        result.output.contains('+--- com.android.support:appcompat-v7:25.0.0 -> 26.1.0')
    }

    def "Uses support library 25 when compileSdkVersion is 25"() {
        def compileLines = """\
        compile 'com.android.support:support-v4:26.0.0'
        """

        createBuildFile(25, compileLines)

        when:
        def result = runGradleProject()

        then:
        result.output.contains('\\--- com.android.support:support-v4:26.0.0 -> 25.4.0')
    }

    def "Aligns gms and firebase"() {
        def compileLines = """\
        compile 'com.google.firebase:firebase-core:11.0.0'
        compile 'com.google.android.gms:play-services-gcm:11.2.0'
        compile 'com.google.android.gms:play-services-location:11.4.0'
        """

        createBuildFile(26, compileLines)

        when:
        def result = runGradleProject()

        then:
        result.output.contains('+--- com.google.firebase:firebase-core:11.0.0 -> 11.4.2')
        result.output.contains('+--- com.google.android.gms:play-services-gcm:11.2.0 -> 11.4.2')
        result.output.contains('+--- com.google.android.gms:play-services-gcm:11.2.0 -> 11.4.2')
    }

}