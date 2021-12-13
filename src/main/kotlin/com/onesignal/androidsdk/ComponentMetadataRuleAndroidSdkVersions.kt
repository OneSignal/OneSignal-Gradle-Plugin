package com.onesignal.androidsdk

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
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

    override fun execute(context: ComponentMetadataContext) {
        println("HERE2 WorkRuntimeCapabilitiesRule version: " + context.details.id.version)
        // TODO: This is a string compare, need to change to Version types
        if (context.details.id.version < "2.6.0") {
            println("  Skipping older version: ${context.details.id.version}")
            return
        }
        println("  context.details: " + context.details)
        context.details.allVariants {
            println("  context.details.allVariants: $this")
            attributes {
                attribute(COMPILE_SDK_VERSION_NAME_ATTRIBUTE, 31 as Integer)
            }
        }
        context.details.addVariant("compileSdkVersion_below_31") {
            withDependencies {
                add("androidx.work:work-runtime:2.6.0") {
                    because("Because: Downgrade to support compileSdkVersion 30")
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
