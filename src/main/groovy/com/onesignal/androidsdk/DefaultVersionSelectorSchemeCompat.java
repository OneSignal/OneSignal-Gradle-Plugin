package com.onesignal.androidsdk;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

class DefaultVersionSelectorSchemeCompat {
    static VersionSelectorScheme get() {
        try {
            // DefaultVersionSelectorScheme with VersionParser required in Gradle 7.0, induced as optional in 4.7 or 4.8
            return new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser());
        } catch (NoSuchMethodError e) {
            return getCompat();
        }
    }

    // Use reflection to support Gradle 4.7 and older
    private static VersionSelectorScheme getCompat() {
        try {
            Class<?> defaultVersionSelectorSchemeClass = Class.forName("org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme");
            Constructor<?> defaultVersionSelectorSchemeConstructor = defaultVersionSelectorSchemeClass.getConstructor(VersionComparator.class);
            Object defaultVersionSelectorSchemeInstance = defaultVersionSelectorSchemeConstructor.newInstance(new DefaultVersionComparator());
            return (VersionSelectorScheme)defaultVersionSelectorSchemeInstance;

        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }
}
