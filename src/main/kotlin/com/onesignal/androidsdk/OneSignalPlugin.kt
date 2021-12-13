package com.onesignal.androidsdk

import org.gradle.api.Plugin
import org.gradle.api.Project

/// KEEP, required to get all the Kotlin Gradle DSL features
import org.gradle.kotlin.dsl.*

// Plugin's implementationClass, this is defined in the root build.gradle
class OneSignalPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        println("OneSignalPlugin.kt")
        project.afterEvaluate {
            initialize(project)
        }
    }

    private fun initialize(project: Project) {
        val androidSdkVersion = AndroidSdkVersionProvider(project, project.logger)
        val compileSdkVersion = androidSdkVersion.compile ?: return

        runWithAndroidCompileSdkVersion(
            project,
            compileSdkVersion
        )
    }

    private fun runWithAndroidCompileSdkVersion(project: Project, compileSdkVersion: Int) {
        InitGradleAttributesAndSchema().initAndroidSdkVersions(
            project.dependencies.attributesSchema,
            project.configurations,
            compileSdkVersion
        )

        project.dependencies.components {
            withModule<ComponentMetadataRuleAndroidSdkVersions>("androidx.work:work-runtime")
        }
    }
}
