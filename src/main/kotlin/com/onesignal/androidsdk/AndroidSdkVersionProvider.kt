package com.onesignal.androidsdk

import com.android.build.gradle.AppExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger

class AndroidSdkVersionProvider constructor(
    private val project: Project,
    private val logger: Logger?,
) {
    private val appExtension: AppExtension? by lazy {
        // TODO: Account for library project?
        project.extensions.findByType(AppExtension::class.java)
    }

    val compile: Int? by lazy {
        val localAppExtension = appExtension
        if (localAppExtension == null) {
            logger?.warn("AppExtension::class.java not found! '${project.name}' is not an Android project.")
            logClassLoaders()
            return@lazy null
        }
        val compileSdkVersion = localAppExtension.compileSdkVersion
        if (compileSdkVersion == null) {
            logger?.warn("appExtension.compileSdkVersion not found! Missing from project '${project.name}'.")
            return@lazy null
        }
        parseAndroidSdkVersion(compileSdkVersion)
    }

    // Format that project.android.compileSdkVersion provides is 'android-##'
    private fun parseAndroidSdkVersion(version: String): Int {
        return version.split('-')[1].toInt()
    }

    private fun logClassLoaders() {
        logger?.info("buildscript.classLoader.definedPackages.size: ${project.buildscript.classLoader.definedPackages.size}")
        project.buildscript.classLoader.definedPackages.forEach {
            logger?.info("project.buildscript package: $it")
        }
        logger?.info("ootProject.buildscript.classLoader.definedPackages.size: ${project.rootProject.buildscript.classLoader.definedPackages.size}")
        project?.rootProject.buildscript.classLoader.definedPackages.forEach {
            logger?.info("rootProject.buildscript package: $it")
        }
    }
}
