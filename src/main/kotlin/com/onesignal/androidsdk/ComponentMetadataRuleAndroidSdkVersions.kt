package com.onesignal.androidsdk

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/// KEEP, required to get all the Kotlin Gradle DSL features
import org.gradle.kotlin.dsl.*

val myAttributeCompileSdkVersion: Attribute<Integer> = Attribute.of("custom.compileSdkVersion", Integer::class.java)

@CacheableRule
abstract class ComponentMetadataRuleAndroidSdkVersions @Inject constructor(val compileSdkVersion: Int) : ComponentMetadataRule {
    @get:Inject
    abstract val objects: ObjectFactory

    override fun execute(context: ComponentMetadataContext) {
        println("HERE WorkRuntimeCapabilitiesRule version: " + context.details.id.version)
        // TODO: This is a string compare, need to change to Version types
        if (context.details.id.version < "2.6.0") {
            println("  Skipping older version: ${context.details.id.version}")
            return
        }
        println("  context.details: " + context.details)
        context.details.allVariants {
            println("  context.details.allVariants: $this")
            attributes {
                attribute(myAttributeCompileSdkVersion, 31 as Integer)
            }
        }
        // TODO: Why does using "releaseVariantReleaseApiPublication" as a base cause it to fail?
        // NOTE: releaseVariantReleaseRuntimePublication is in 2.7.0, but is a different name in 2.4.0
        context.details.addVariant("custom.compileSdkVersion_require_min_30") {
            withDependencies {
                add("androidx.work:work-runtime:2.6.0")
            }

            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
//                // TODO: Should remove instead of setting. Or if not possible to set 0 so it always uses this as a fallback
                attribute(myAttributeCompileSdkVersion, 30 as Integer)
            }
        }
    }
}
