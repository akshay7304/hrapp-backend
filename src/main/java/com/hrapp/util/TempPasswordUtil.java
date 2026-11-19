package com.hrapp.util;

import java.security.SecureRandom;

/** Generates one-time temporary passwords for new users. */
public final class TempPasswordUtil {

    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TempPasswordUtil() {
    }

    public static String generate(int length) {
        int n = Math.max(8, length);
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
