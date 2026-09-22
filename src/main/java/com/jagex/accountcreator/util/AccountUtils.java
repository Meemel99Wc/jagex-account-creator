package com.jagex.accountcreator.util;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * Utility functions for account creation
 */
public class AccountUtils {
    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new SecureRandom();

    /**
     * Generate a random username of specified length
     */
    public static String generateUsername(int length) {
        StringBuilder username = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            username.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return username.toString();
    }

    /**
     * Get a random domain from the list
     */
    public static String getRandomDomain(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            throw new IllegalArgumentException("Domain list cannot be empty");
        }
        return domains.get(RANDOM.nextInt(domains.size()));
    }

    /**
     * Generate a random email address
     */
    public static String generateEmail(List<String> domains, int usernameLength) {
        String username = generateUsername(usernameLength);
        String domain = getRandomDomain(domains);
        return username + "@" + domain;
    }
}
