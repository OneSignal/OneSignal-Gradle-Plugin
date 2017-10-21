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
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('dependencies', '--configuration', 'compile')
            .withPluginClasspath()
            .build()
        println result.output
        return result
    }

    def "OneSignal version 3.6.4"() {
        def compileLines = """\
        compile 'com.onesignal:OneSignal:3.6.4'
        """

        createBuildFile(26, compileLines)

        when:
        def result = runGradleProject()

        then:
        result.output.contains('+--- com.google.android.gms:play-services-gcm:[10.2.1,11.3.0) -> 11.2.2')
        result.output.contains('+--- com.google.android.gms:play-services-location:[10.2.1,11.3.0) -> 11.2.2')
        result.output.contains('+--- com.android.support:support-v4:[26.0.0,26.2.0) -> 26.1.0 (*)')
        result.output.contains('\\--- com.android.support:customtabs:[26.0.0,26.2.0) -> 26.1.0')
    }

    def "Aligns support library"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:25.0.0'
        compile 'com.android.support:support-v4:26.0.0'
        compile 'com.onesignal:OneSignal:3.6.4'
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
        result.output.contains('+--- com.google.firebase:firebase-core:11.0.0 -> 11.4.0')
        result.output.contains('+--- com.google.android.gms:play-services-gcm:11.2.0 -> 11.4.0')
        result.output.contains('\\--- com.google.android.gms:play-services-location:11.4.0')
    }

    def "Aligns when using LATEST"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:25.0.0'
        compile 'com.android.support:support-v4:+'
        """

        createBuildFile(26, compileLines)

        when:
        def result = runGradleProject()

        then:
        result.output.contains('OneSignalProjectPlugin: com.android.support:support-v4 overridden from \'+\' to \'26.+\'')
    }

    def "Aligns when using +'s"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:25.0.+'
        compile 'com.android.support:support-v4:25.+'
        """

        createBuildFile(26, compileLines)

        when:
        def result = runGradleProject()

        then:
        result.output.contains('+--- com.android.support:appcompat-v7:25.0.+ -> 25.4.0')
        result.output.contains('\\--- com.android.support:support-v4:25.+ -> 25.4.0 (*)')
    }

    def "Test exclusive upper bound ending on major version"() {
        def compileLines = """\
        compile 'com.android.support:appcompat-v7:[25.0.0, 26.0.0)'
        compile 'com.android.support:support-v4:25.+'
        """

        createBuildFile(26, compileLines)

        when:
        def result = runGradleProject()

        then:
        result.output.contains('+--- com.android.support:appcompat-v7:[25.0.0, 26.0.0) -> 26.0.0-beta2')
        result.output.contains('\\--- com.android.support:support-v4:25.+ -> 26.0.0-beta2 (*)')
    }
}