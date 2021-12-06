package com.onesignal.androidsdk

import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class TestCustomGradleModuleMetadata extends Specification {

    static final NEW_LINE = System.getProperty('line.separator')

    static final PROJECT_PROPS = 'PROJECT_PROPS'
    static final APP_GRADLE_DOT_BUILD_PROPS = 'APP_GRADLE_DOT_BUILD_PROPS'
    static final LOCAL_REPO = 'LOCAL_REPO'
    static final LOCAL_REPO_DIR = 'DIR'

    static TemporaryFolder createTestRepo() {
        final testRepoDir = new TemporaryFolder()
        testRepoDir.create()

        createLocalTestLib(testRepoDir)
        testRepoDir
    }

    static void createLocalTestLib(TemporaryFolder repoDir) {
        final group = 'com.test.local'
        final module = 'liba'

        saveModuleFile(
            repoDir,
            group, module, '1.0.0',
            createGradleModuleFile1_0_0_example(group, module)
        )

        saveModuleFile(
            repoDir,
            group, module, '1.1.0',
            createGradleModuleFile1_1_0_example(group, module)
        )

        writeMetaDataXmlFile(
            repoDir,
            group, module,
            mavenMetaDataXmlFile(
                group,
                module,
                '1.1.0',
                """
                    <version>1.0.0</version>
                    <version>1.1.0</version>
                """
            )
        )

    }

    static saveModuleFile(TemporaryFolder repoDir, String group, String module, String version, String contents) {
        final libPath = "${group.replace('.', '/')}/${module}/${version}"

        repoDir.newFolder(libPath.split('/'))
        final filePathWithPrefix = "${libPath}/${module}-${version}"
        repoDir.newFile("${filePathWithPrefix}.module") << contents
        // Required dummy .pom file is required since Gradle looks for this before a .module file
        repoDir.newFile("${filePathWithPrefix}.pom") << createPomFile(group, module, version)
    }

    static String createGradleModuleFile1_0_0_example(String group, String module) {
        """
        {
          "formatVersion": "1.1",
          "component": {
            "group": "${group}",
            "module": "${module}",
            "version": "1.0.0"
          },
          "variants": [
            {
              "name": "my.custom_with_2_required",
              "attributes": {
                "org.gradle.category": "library",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.libraryelements": "aar",
                "org.gradle.usage": "java-runtime",
                "org.gradle.jvm.environment": "sdfasdfd",
                "org.gradle.jvm.version": 18,
                "my.custom": "2"
              }
            },
            {
              "name": "my.customA_with_2_or_higher",
              "attributes": {
                "org.gradle.category": "library",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.libraryelements": "aar",
                "org.gradle.usage": "java-runtime",
                "org.gradle.jvm.environment": "sdfasdfd",
                "org.gradle.jvm.version": 18,
                "my.customA": 2
              }
            },
            {
              "name": "my.custom-not-required",
              "attributes": {
                "org.gradle.category": "library",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.libraryelements": "aar",
                "org.gradle.usage": "java-runtime",
                "org.gradle.jvm.environment": "sdfasdfd",
                "org.gradle.jvm.version": 18
              }
            }
          ]
        }
        """
    }

    static String createGradleModuleFile1_1_0_example(String group, String module) {
        """
        {
          "formatVersion": "1.1",
          "component": {
            "group": "${group}",
            "module": "${module}",
            "version": "1.1.0"
          },
          "variants": [
            {
              "name": "custom.compileSdkVersion_require_31",
              "attributes": {
                "org.gradle.category": "library",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.libraryelements": "aar",
                "org.gradle.usage": "java-runtime",
                "org.gradle.jvm.environment": "sdfasdfd",
                "org.gradle.jvm.version": 18,
                "custom.compileSdkVersion": 31
              }
            },
            {
              "name": "custom.compileSdkVersion_at_30_downgrade_to_1.0.0",
              "attributes": {
                "org.gradle.category": "library",
                "org.gradle.dependency.bundling": "external",
                "org.gradle.libraryelements": "aar",
                "org.gradle.usage": "java-runtime",
                "org.gradle.jvm.environment": "sdfasdfd",
                "org.gradle.jvm.version": 18,
                "custom.compileSdkVersion": 30
              },
              "dependencies": [
                {
                  "group": "${group}",
                  "module": "${module}",
                  "version": {
                    "requires": "1.0.0"
                  }
                }
              ]
            }
          ]
        }
        """
    }

    static String createPomFile(String group, String module, String version) {
        """\
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns="http://maven.apache.org/POM/4.0.0"
             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
      <!-- This module was also published with a richer model, Gradle metadata,  -->
      <!-- which should be used instead. Do not delete the following line which  -->
      <!-- is to indicate to Gradle or any Gradle module metadata file consumer  -->
      <!-- that they should prefer consuming it instead. -->
      <!-- do_not_remove: published-with-gradle-metadata -->
      <modelVersion>4.0.0</modelVersion>
      <groupId>${group}</groupId>
      <artifactId>${module}</artifactId>
      <version>${version}</version>
      <packaging>pom</packaging>
      <dependencies></dependencies>
</project>
"""
    }

    static String mavenMetaDataXmlFile(String group, String module, String latestVersion, String versionsListXml) {
"""\
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>${group}</groupId>
  <artifactId>${module}</artifactId>
  <versioning>
    <latest>${latestVersion}</latest>
    <versions>
      ${versionsListXml}
    </versions>
    <lastUpdated>20211206000831</lastUpdated>
  </versioning>
</metadata>
"""
    }

    static void writeMetaDataXmlFile(TemporaryFolder repoDir, String group, String module, String contents) {
        final libPath = "${group.replace('.', '/')}/${module}"
        repoDir.newFile("${libPath}/maven-metadata.xml") << contents
    }

    static TemporaryFolder createProject(Map<String, Map<String, String>> projectProps) {
        final testProjectDir = new TemporaryFolder()
        testProjectDir.create()
        testProjectDir.newFile('build.gradle') << rootBuildDotGradle(projectProps[LOCAL_REPO])

        testProjectDir.newFile('gradle.properties')
        testProjectDir.newFile('settings.gradle') << settingsDotGradle()

        testProjectDir.newFolder('app')
        testProjectDir.newFile('app/build.gradle') << appBuildDotGradle(projectProps[APP_GRADLE_DOT_BUILD_PROPS])

        testProjectDir.newFolder('app', 'src', 'main')
        testProjectDir.newFile('app/src/main/AndroidManifest.xml') << androidManifest()

        testProjectDir
    }

    static String rootBuildDotGradle(Map<String, String> buildProps) {
        """
            buildscript {
                repositories {
                    google()
                    mavenCentral()
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:7.0.3'
                }
            }

            allprojects {
                repositories {
                    google()

                    // Local maven repo to test local libraries;
                    maven { url(uri('file://${buildProps[LOCAL_REPO_DIR]}')) }

                    mavenCentral()
                }
            }
        """
    }

    static String appBuildDotGradle(Map<String, String> buildProps) {
         """
            apply plugin: 'com.android.application'

// 
class CustomClass implements Comparable<Integer>, Serializable {
    int value

    CustomClass(int value) {
        this.value = value
    }

    @Override
    int compareTo(Integer o) {
        println("HERE compareTO!!!!!!")
        0
    }
}

            android {
                compileSdkVersion ${buildProps['compileSdkVersion']}
                defaultConfig {
                    applicationId 'com.app.example'

                    minSdkVersion 16
                    versionCode 1
                    versionName '1.0'
                }

                 lintOptions {
                    checkReleaseBuilds false
                    abortOnError false
                }
                
                buildTypes {
                    debug { }
                }
            }
            
            dependencies {
                ${buildProps['compileLines']}
            }

// TODO: Integer seems to be exact, can't find how to define a minium
            final myAttributeCompileSdkVersion = Attribute.of('custom.compileSdkVersion', Integer)
            dependencies.attributesSchema {
                attribute(myAttributeCompileSdkVersion)
            }
            configurations.all {
                attributes {
                    attribute(myAttributeCompileSdkVersion, 30)
                }
            }

// Required for --scan
//            gradleEnterprise {
//                buildScan {
//                    termsOfServiceUrl = "https://gradle.com/terms-of-service"
//                    termsOfServiceAgree = "yes"
//                }
//            }
        """
    }

    static String settingsDotGradle() {
        """\
            include ':app'
        """
    }

    static String androidManifest(String packageName = 'com.app.example') {
        """\
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            package="${packageName}">
            </manifest>
        """.stripIndent()
    }

    static Object runGradleProject(Map<String, Map<String, Map<String, String>>> props) {
        final localRepoDir = createTestRepo()
        println("localRepoDir: ${localRepoDir.root}")
        props[PROJECT_PROPS][LOCAL_REPO] = [(LOCAL_REPO_DIR): localRepoDir.root.toString()]
        
        final testProjectDir = createProject(props[PROJECT_PROPS])
        println("testProjectDir: ${testProjectDir.root}")

        final result =
            GradleRunner.create()
                .withProjectDir(testProjectDir.root)
//                .withArguments([':app:dependencies', '--configuration', 'debugCompileClasspath', '--info'])
//                .withArguments(['app:build', '--scan'])
                .withArguments(['-q', 'app:dependencyInsight', '--dependency', 'liba', '--configuration', 'debugCompileClasspath', '--stacktrace'])
                .withPluginClasspath()
                .withGradleVersion('7.3.1')
                .build()
        println(result.output)
        result
    }

    // TEST if we define custom attributes for Gradle Model Metadata if it will select a lower compatible library for us.
    def 'testCompileSdkVersion'() {
        given:
        final compileLines = """\
            implementation 'com.test.local:liba:[1.0.0, 2.0)'
        """
        final props = [
            (PROJECT_PROPS): [
                (APP_GRADLE_DOT_BUILD_PROPS): [
                    'compileSdkVersion': 30,
                    'compileLines': compileLines
                ]
            ]
        ]

        when:
        final results = runGradleProject(props)

        then:
        assert results.output.contains("com.test.local:liba:1.0.0$NEW_LINE")
    }
}


/**
 * # Notes
 * ## Goals
 * This is to help understand how Gradle Module Metadata wroks (.module files) and how interduce custom proprties.
 * Some the ideas are to slove the issues outline here:
 *    - https://blog.gradle.org/alignment-with-gradle-module-metadata
 *    - https://issuetracker.google.com/issues/209034970
 * ## Questions
 * 1. How do I define a custom module attribute as a minimum value?
 *    - If I use an Integer it requires the values to match instead.
 * ## Bugs
 * 1. Gradle BUG - If app asks for a version range of a library and
 *    the .module of the most recent version doesn't contain a compatible variant it won't downgrade.
 *    - As a workaround downgrading does work by defining an another variant with a dependencies section
 *      to use the older version of itself.
 *        - This probably requires other libraries to use version ranges so the downgrade can happen.
 *          Unless maybe strict is used?
 *        - I think this would work even if a the module included a POM too. As one would be not picked until
 *          a usable variant is found.
 *
 */