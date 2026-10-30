package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GuideScopes {
    public static final String COMMENTS = "comments";
    public static final String TOPICS_READ = "topics:read";
    public static final String TOPICS_WRITE = "topics:write";
    public static final String GUIDE_TOKEN_SCOPES = COMMENTS;

    public static final List<String> PERSONAL_CHOICES = List.of(COMMENTS, TOPICS_READ, TOPICS_WRITE);

    private GuideScopes() {
    }

    public static Set<String> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        for (String part : stored.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                scopes.add(trimmed);
            }
        }
        return Set.copyOf(scopes);
    }

    /** Stores the chosen personal-access scopes in a stable order. */
    public static String joinPersonal(Collection<String> selected) {
        if (selected == null || selected.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one scope.");
        }
        LinkedHashSet<String> chosen = new LinkedHashSet<>();
        for (String scope : selected) {
            if (scope == null || !PERSONAL_CHOICES.contains(scope)) {
                throw new IllegalArgumentException("Choose a scope from the list.");
            }
            chosen.add(scope);
        }
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one scope.");
        }
        List<String> ordered = new ArrayList<>();
        for (String scope : PERSONAL_CHOICES) {
            if (chosen.contains(scope)) {
                ordered.add(scope);
            }
        }
        return String.join(",", ordered);
    }

    public static boolean satisfied(Set<String> have, String required) {
        if (required == null || required.isBlank()) {
            return false;
        }
        if (have != null && have.contains(required)) {
            return true;
        }
        return TOPICS_READ.equals(required) && have != null && have.contains(TOPICS_WRITE);
    }

    public static String guideLabel(String origin) {
        String label = "Guide sign-in: " + (origin == null ? "" : origin);
        if (label.length() <= 120) {
            return label;
        }
        return label.substring(0, 120);
    }
}
