package com.onesignal.androidsdk

import com.google.common.collect.Ordering
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributesSchema

val COMPILE_SDK_VERSION_NAME_ATTRIBUTE = Attribute.of(
    "com.onesignal.android.compileSdkVersion",
    Integer::class.java,
)

class InitGradleAttributesAndSchema {
    fun initAndroidSdkVersions(
        attributesSchema: AttributesSchema,
        configurations: ConfigurationContainer,
        compileSdkVersion: Int
    ) {
        val targetPlatformSchema = attributesSchema.attribute(COMPILE_SDK_VERSION_NAME_ATTRIBUTE)
        targetPlatformSchema.compatibilityRules.ordered(Ordering.natural())
        targetPlatformSchema.disambiguationRules.pickLast(Ordering.natural())

        configurations.all {
            attributes {
                attribute(COMPILE_SDK_VERSION_NAME_ATTRIBUTE, compileSdkVersion as Integer)
            }
        }
    }
}
