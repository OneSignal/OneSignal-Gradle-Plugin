package com.onesignal.androidsdk;

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.Category
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.named
import javax.inject.Inject

open class ComponentMetadataRuleFirebase: ComponentMetadataRule {
    @Inject
    open fun getObjects(): ObjectFactory = throw UnsupportedOperationException()

    override fun execute(ctx: ComponentMetadataContext) {
        // TODO: Convert to use firebase. Need to provide a module BOM lookup table
        if (ctx.details.id.group == "com.fasterxml.jackson.core") {
            ctx.details.allVariants {
                withDependencies {
                    add("com.fasterxml.jackson:jackson-bom:${ctx.details.id.version}") {
                        attributes {
                            attribute(Category.CATEGORY_ATTRIBUTE,
                                getObjects().named(Category.REGULAR_PLATFORM))
                        }
                    }
                }
            }
        }
    }
}
