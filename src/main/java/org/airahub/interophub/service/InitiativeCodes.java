package org.airahub.interophub.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class InitiativeCodes {

    public static final Set<String> RESERVED = Set.of("new");

    private static final Pattern CODE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private InitiativeCodes() {
    }

    public static String slug(String name) {
        if (name == null) {
            return "";
        }
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-+$", "");
        }
        if (RESERVED.contains(slug)) {
            slug = slug + "-initiative";
        }
        return slug;
    }

    /** @return an error message, or null when the code can be stored */
    public static String validationError(String code) {
        if (code == null || code.isBlank()) {
            return "A code is required.";
        }
        if (code.length() > 80) {
            return "Code must be 80 characters or less.";
        }
        if (RESERVED.contains(code)) {
            return "That code is reserved.";
        }
        if (!CODE.matcher(code).matches()) {
            return "Use lowercase letters, numbers, and hyphens.";
        }
        return null;
    }
}
