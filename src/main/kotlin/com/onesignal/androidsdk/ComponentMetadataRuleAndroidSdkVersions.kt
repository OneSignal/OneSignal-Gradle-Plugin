package com.onesignal.androidsdk

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/// KEEP, required to get all the Kotlin Gradle DSL features
import org.gradle.kotlin.dsl.*

@CacheableRule
abstract class ComponentMetadataRuleAndroidSdkVersions @Inject constructor() : ComponentMetadataRule {
    @get:Inject
    abstract val objects: ObjectFactory

    val versionToMinCompileSdkMap get() =
        mapOf(
            "2.7.0" to 31
        )

    private fun shouldApplyRule(version: String): Boolean {
        return VersionComparator().lessThan(version, "2.7.0")
    }

    override fun execute(context: ComponentMetadataContext) {
        println("1ComponentMetadataRuleAndroidSdkVersions: ${context.details.id.version}")
        if (!shouldApplyRule(context.details.id.version)) {
            return
        }
        println("2ComponentMetadataRuleAndroidSdkVersions: ${context.details.id.version}")

        applyMinCompileSdkVersion(context.details, 31)
    }

    private fun applyMinCompileSdkVersion(details: ComponentMetadataDetails, version: Int) {
        details.allVariants {
            attributes {
                attribute(COMPILE_SDK_VERSION_NAME_ATTRIBUTE, version as Integer)
            }
        }
        provideDowngradeIfBelowCompileSdkVersion(details, version)
    }

    private fun provideDowngradeIfBelowCompileSdkVersion(details: ComponentMetadataDetails, version: Int) {
        details.addVariant("compileSdkVersion_below_$version") {
            withDependencies {
                add("androidx.work:work-runtime:2.6.0") {
                    because("Because: Downgrade to support compileSdkVersion ${version - 1}")
                }
            }

            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                // The context.details.allVariants above, adds the attribute to every variant,
                // we need to assign some default value so this doesn't happen. 0 here means it becomes a non factor
                attribute(COMPILE_SDK_VERSION_NAME_ATTRIBUTE, 0 as Integer)
            }
        }
    }
}
