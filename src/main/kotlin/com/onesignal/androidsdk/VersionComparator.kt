package com.onesignal.androidsdk

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser

class VersionComparator {
    private val gradleVersionComparator: Comparator<Version> by lazy {
        DefaultVersionComparator().asVersionComparator()
    }

    // Is "version1 < version2"?
    fun lessThan(version1Str: String, version2Str: String): Boolean {
        return gradleVersionComparator.compare(
            VersionParser().transform(version1Str),
            VersionParser().transform(version2Str)
        ) == -1
    }
}