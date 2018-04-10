// This file contains source code from Gradle
// Copyright from source VersionRangeSelector.java attached below
/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// This exists to use newer Gradle features back to Gradle version 2.14.1
// Source from the following URL
// https://github.com/gradle/gradle/blob/v4.6.0/subprojects/dependency-management/src/main/java/org/gradle/api/internal/artifacts/ivyservice/ivyresolve/strategy/VersionRangeSelector.java

package com.onesignal.androidsdk;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.regex.Pattern;

public class VersionCompatHelpers {

    private static final String OPEN_INC = "[";

    private static final String OPEN_EXC = "]";
    private static final String OPEN_EXC_MAVEN = "(";

    private static final String CLOSE_INC = "]";

    private static final String CLOSE_EXC = "[";
    private static final String CLOSE_EXC_MAVEN = ")";

    private static final String LOWER_INFINITE = "(";

    private static final String UPPER_INFINITE = ")";

    private static final String SEPARATOR = ",";

    // following patterns are built upon constants above and should not be modified
    private static final String OPEN_INC_PATTERN = "\\" + OPEN_INC;

    private static final String OPEN_EXC_PATTERN = "\\" + OPEN_EXC + "\\" + OPEN_EXC_MAVEN;

    private static final String CLOSE_INC_PATTERN = "\\" + CLOSE_INC;

    private static final String CLOSE_EXC_PATTERN = "\\" + CLOSE_EXC + "\\" + CLOSE_EXC_MAVEN;

    private static final String LI_PATTERN = "\\" + LOWER_INFINITE;

    private static final String UI_PATTERN = "\\" + UPPER_INFINITE;

    private static final String SEP_PATTERN = "\\s*\\" + SEPARATOR + "\\s*";

    private static final String OPEN_PATTERN = "[" + OPEN_INC_PATTERN + OPEN_EXC_PATTERN + "]";

    private static final String CLOSE_PATTERN = "[" + CLOSE_INC_PATTERN + CLOSE_EXC_PATTERN + "]";

    private static final String ANY_NON_SPECIAL_PATTERN = "[^\\s" + SEPARATOR + OPEN_INC_PATTERN
            + OPEN_EXC_PATTERN + CLOSE_INC_PATTERN + CLOSE_EXC_PATTERN + LI_PATTERN + UI_PATTERN
            + "]";

    private static final String FINITE_PATTERN = OPEN_PATTERN + "\\s*(" + ANY_NON_SPECIAL_PATTERN
            + "+)" + SEP_PATTERN + "(" + ANY_NON_SPECIAL_PATTERN + "+)\\s*" + CLOSE_PATTERN;

    private static final String LOWER_INFINITE_PATTERN = LI_PATTERN + SEP_PATTERN + "("
            + ANY_NON_SPECIAL_PATTERN + "+)\\s*" + CLOSE_PATTERN;

    private static final String UPPER_INFINITE_PATTERN = OPEN_PATTERN + "\\s*("
            + ANY_NON_SPECIAL_PATTERN + "+)" + SEP_PATTERN + UI_PATTERN;

    private static final String SINGLE_VALUE_PATTERN = OPEN_INC_PATTERN + "\\s*(" + ANY_NON_SPECIAL_PATTERN + "+)" + CLOSE_INC_PATTERN;

    private static final Pattern FINITE_RANGE = Pattern.compile(FINITE_PATTERN);

    private static final Pattern LOWER_INFINITE_RANGE = Pattern.compile(LOWER_INFINITE_PATTERN);

    private static final Pattern UPPER_INFINITE_RANGE = Pattern.compile(UPPER_INFINITE_PATTERN);

    private static final Pattern SINGLE_VALUE_RANGE = Pattern.compile(SINGLE_VALUE_PATTERN);

    public static final Pattern ALL_RANGE = Pattern.compile(FINITE_PATTERN + "|"
            + LOWER_INFINITE_PATTERN + "|" + UPPER_INFINITE_PATTERN + "|" + SINGLE_VALUE_RANGE);


    // Source from Gradle 4.6 VersionRangeSelector.intersect
    static public VersionRangeSelector intersect(VersionRangeSelector thisVersion, VersionRangeSelector other) {
        StringBuilder sb = new StringBuilder();
        Version lower = null;
        Version upper = null;
        boolean lowerInc = false;
        if (getLowerBound(thisVersion) == null) {
            if (getLowerBound(other) == null) {
                sb.append(LOWER_INFINITE);
            } else {
                sb.append(isLowerInclusive(other) ? OPEN_INC : OPEN_EXC);
                sb.append(getLowerBound(other));
                lower = getLowerBoundVersion(other);
                lowerInc = isLowerInclusive(other);
            }
        } else {
            if (getLowerBound(other) == null || isHigher(getLowerBoundVersion(thisVersion), getLowerBoundVersion(other), isLowerInclusive(thisVersion))) {
                lowerInc = getLowerBound(thisVersion).equals(getLowerBound(other)) ? isLowerInclusive(thisVersion) && isLowerInclusive(other) : isLowerInclusive(thisVersion);
                sb.append(lowerInc ? OPEN_INC : OPEN_EXC);
                sb.append(getLowerBound(thisVersion));
                lower = getLowerBoundVersion(thisVersion);
            } else {
                lowerInc = getLowerBound(other).equals(getLowerBound(thisVersion)) ? isLowerInclusive(thisVersion) && isLowerInclusive(other) : isLowerInclusive(other);
                sb.append(lowerInc ? OPEN_INC : OPEN_EXC);
                sb.append(getLowerBound(other));
                lower = getLowerBoundVersion(other);
                lowerInc = isLowerInclusive(other);
            }
        }
        sb.append(SEPARATOR);
        if (getUpperBound(thisVersion) == null) {
            if (getUpperBound(other) == null) {
                sb.append(UPPER_INFINITE);
            } else {
                sb.append(getUpperBound(other));
                sb.append(isUpperInclusive(other) ? CLOSE_INC : CLOSE_EXC);
                upper = getUpperBoundVersion(other);
            }
        } else {
            if (getUpperBound(other) == null || isLower(getUpperBoundVersion(thisVersion), getUpperBoundVersion(other), isUpperInclusive(thisVersion))) {
                sb.append(getUpperBound(thisVersion));
                boolean inclusive = getUpperBound(thisVersion).equals(getUpperBound(other)) ? isUpperInclusive(thisVersion) && isUpperInclusive(other) : isUpperInclusive(thisVersion);
                sb.append(inclusive ? CLOSE_INC : CLOSE_EXC);
                upper = getUpperBoundVersion(thisVersion);
            } else {
                sb.append(getUpperBound(other));
                boolean inclusive = getUpperBound(other).equals(getUpperBound(thisVersion)) ? isUpperInclusive(thisVersion) && isUpperInclusive(other) : isUpperInclusive(other);
                sb.append(inclusive ? CLOSE_INC : CLOSE_EXC);
                upper = getUpperBoundVersion(other);
            }
        }

        if (lower != null && upper != null && isHigher(lower, upper, lowerInc)) {
            return null;
        }
        if (lower != null && lower.equals(upper) && !lowerInc) {
            return null;
        }

        return new VersionRangeSelector(sb.toString(), getComparator());
    }


    /**
     * Tells if version1 is lower than version2.
     */
    static private boolean isLower(Version version1, Version version2, boolean inclusive) {
        int result = getComparator().compare(version1, version2);
        return result <= (inclusive ? 0 : -1);
    }

    /**
     * Tells if version1 is higher than version2.
     */
    static private boolean isHigher(Version version1, Version version2, boolean inclusive) {
        int result = getComparator().compare(version1, version2);
        return result >= (inclusive ? 0 : 1);
    }

    static private Comparator<Version> getComparator() {
        return new DefaultVersionComparator().asVersionComparator();
    }



    // Using reflection to access private fields on VersionRangeSelector that did not have public methods
    //   back on Gradle 2.14.1
    static private Object get(VersionRangeSelector version, String fieldName) {
        try {
            Field field = VersionRangeSelector.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(version);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    static private String getLowerBound(VersionRangeSelector version) {
        return (String)get(version, "lowerBound");
    }

    static private String getUpperBound(VersionRangeSelector version) {
        return (String)get(version, "upperBound");
    }

    static private Version getLowerBoundVersion(VersionRangeSelector version) {
        String lowerBound = getLowerBound(version);
        return lowerBound == null ? null : new VersionParser().transform(lowerBound);
    }

    static private Version getUpperBoundVersion(VersionRangeSelector version) {
        String upperBound = getUpperBound(version);
        return upperBound == null ? null : new VersionParser().transform(upperBound);
    }

    static private boolean isLowerInclusive(VersionRangeSelector version) {
        return (Boolean)get(version, "lowerInclusive");
    }

    static private boolean isUpperInclusive(VersionRangeSelector version) {
        return (Boolean)get(version, "upperInclusive");
    }

}
