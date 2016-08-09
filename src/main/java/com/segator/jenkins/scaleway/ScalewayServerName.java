/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Maxim Biro <nurupo.contributions@gmail.com>
 *               2016 Isaac Aymerich <isaac.aymerich@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.segator.jenkins.scaleway;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScalewayServerName {
    private static final String PREFIX = "jenkins";
    private static final String CLOUD_REGEX = "([a-zA-Z0-9\\.]+)";
    private static final String SLAVE_REGEX = "([a-zA-Z0-9\\.]+)";
    private static final String UUID_REGEX = "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}";

    private static final String SCALE_REGEX = PREFIX + "-" + CLOUD_REGEX + "-" + SLAVE_REGEX + "-" + UUID_REGEX;

    private static final Pattern CLOUD_PATTERN = Pattern.compile("^" + CLOUD_REGEX + "$");
    private static final Pattern SLAVE_PATTERN = Pattern.compile("^" + SLAVE_REGEX + "$");
    private static final Pattern SCALE_PATTERN = Pattern.compile("^" + SCALE_REGEX + "$");


    private ScalewayServerName() {
        throw new AssertionError();
    }

    public static boolean isValidCloudName(final String cloudName) {
        return CLOUD_PATTERN.matcher(cloudName).matches();
    }

    public static boolean isValidSlaveName(final String slaveName) {
        return SLAVE_PATTERN.matcher(slaveName).matches();
    }

    public static String generateServerName(final String cloudName, final String slaveName) {
        return PREFIX + "-" + cloudName + "-" + slaveName + "-" + UUID.randomUUID().toString();
    }

    public static boolean isServerInstanceOfCloud(final String serverName, final String cloudName) {
        Matcher m = SCALE_PATTERN.matcher(serverName);
        return m.matches() && m.group(1).equals(cloudName);
    }

    public static boolean isServerInstanceOfSlave(final String serverName, final String cloudName, final String slaveName) {
        Matcher m = SCALE_PATTERN.matcher(serverName);
        return m.matches() && m.group(1).equals(cloudName) && m.group(2).equals(slaveName);
    }

}
