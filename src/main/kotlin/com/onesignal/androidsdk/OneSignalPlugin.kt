package com.onesignal.androidsdk

import com.android.build.gradle.AppExtension
import com.onesignal.gradleplugin.AndroidExtensionHelpers
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.jvm.jvmName

/// KEEP, required to get all the Kotlin Gradle DSL features
import org.gradle.kotlin.dsl.*

/**
 * HERE2 AFTER apply plugin: 'com.android.application'
 * resolutionHooksForAndroidPluginV3:project.android: extension 'android'
 * resolutionHooksForAndroidPluginV3:project.android.class: class com.android.build.gradle.internal.dsl.BaseAppModuleExtension_Decorated
 */

/// So use `com.android.build.gradle.internal.dsl.BaseAppModuleExtension`?

// AGP 7 has this API
//val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
//androidComponents.finalizeDsl { extension ->
//    ...
//}

class OneSignalPlugin : Plugin<Project> {

    // Format that project.android.compileSdkVersion provides is 'android-##'
    private fun parseAndroidSdkVersion(version: String): Int {
        return version.split('-')[1].toInt()
    }

    override fun apply(project: Project) {
        println("OneSignalPlugin.kt")

//        project.plugins.whenPluginAdded<AppPlugin> {
//
//        }

//        project.plugins.whenPluginAdded(AppPlugin::class.java) {
//
//        }

//        project.extensions

//        project.plugins.withType(AppPlugin::class.java).whenPluginAdded {
//            println(" project.plugins.withType(AppPlugin::class.java).whenPluginAdded: " + it)
//        }

        // TODO: Account for library project?
        project.afterEvaluate {
//            val appExtension = project.extensions.findByType(AppExtension::class.java)
//            println("appExtension: $appExtension")

            println("OneSignalPlugin.kt - project.afterEvaluate")
            println("OneSignalPlugin.kt - project.android: " + project.getThroughReflection<AppExtension>("android"))

            project.plugins.matching { it::class.jvmName == "com.android.build.gradle.AppPlugin" }.all {
                println("HERE1111 $this")

                // Caused by: java.lang.NoClassDefFoundError: com/android/build/gradle/internal/dsl/BaseAppModuleExtension
//                val appExtension = project.extensions.findByType(com.android.build.gradle.internal.dsl.BaseAppModuleExtension::class.java)
//                println("HERE2222 appExtension: $appExtension")

                // Still get "java.lang.ClassNotFoundException", WAT!
//                val appExtension = project.extensions.findByType(Class.forName("com.android.build.gradle.internal.dsl.BaseAppModuleExtension_Decorated"))
//                println("HERE2222 appExtension: $appExtension")


                // Currently, registered extension names: [ext, _internalAndroidGradlePluginDependencyCheckerRegistered, base, defaultArtifacts, sourceSets, reporting, java, javaToolchains, buildOutputs, android, androidComponents]
//                val appext = project.extensions.getByName("android")  // android == com.android.build.gradle.internal.dsl.BaseAppModuleExtension_Decorated
//                println("HERE2222 appext: ${appext.javaClass.name}")


                val appext = project.extensions.getByName("android") // android == com.android.build.gradle.internal.dsl.BaseAppModuleExtension_Decorated
                println("HERE2222 appext: ${appext.javaClass.name}")

                println("HERE444: compileSdkVersion.raw: " + AndroidExtensionHelpers.compileSdkVersion(project))
                println("HERE444: compileSdkVersion.parsed: " + parseAndroidSdkVersion(AndroidExtensionHelpers.compileSdkVersion(project)))


                project.dependencies.components {
                    withModule<ComponentMetadataRuleAndroidSdkVersions>("androidx.work:work-runtime") {
                        params(30)
                    }
//                    val android = project.extensions.getByType(AppExtension::class.java)
//                    println("HERE2222 $android")
//                    it.withModule("androidx.work:work-runtime", ComponentMetadataRuleAndroidSdkVersions::class.java)
                }

                val compileSdkVersion = parseAndroidSdkVersion(AndroidExtensionHelpers.compileSdkVersion(project))

                configurations.all {
                    attributes {
                        attribute(myAttributeCompileSdkVersion, Integer(compileSdkVersion))
                    }
                }
            }
        }

//        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
    }

    inline fun <reified T : Any> Any.getThroughReflection(propertyName: String): T? {
        val getterName = "get" + propertyName.capitalize()
        return try {
            javaClass.getMethod(getterName).invoke(this) as? T
        } catch (e: NoSuchMethodException) {
            null
        }
    }
}