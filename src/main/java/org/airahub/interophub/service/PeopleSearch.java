package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranks people the initiative already knows. Roster rows come first, then
 * InteropHub accounts, then other people known by email.
 */
public final class PeopleSearch {

    public static final int MIN_QUERY_LENGTH = 2;

    public enum Source {
        ROSTER("On this initiative"),
        ACCOUNT("InteropHub account"),
        KNOWN("Known by email");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Candidate(Source source, Long memberId, Long userId, String email, String accountDisplay,
            String accountFirst, String accountLast, String firstName, String lastName, String organization) {
    }

    public record Hit(Source source, String sourceLabel, Long memberId, Long userId, String email, String name,
            String organization, String firstName, String lastName) {
    }

    private PeopleSearch() {
    }

    public static boolean eligible(String query) {
        return query != null && query.trim().length() >= MIN_QUERY_LENGTH;
    }

    public static List<Hit> rank(List<Candidate> candidates) {
        Map<String, Candidate> best = new LinkedHashMap<>();
        if (candidates != null) {
            for (Candidate candidate : candidates) {
                if (candidate == null || candidate.source() == null) {
                    continue;
                }
                String key = key(candidate);
                Candidate current = best.get(key);
                if (current == null || prefer(candidate, current)) {
                    best.put(key, candidate);
                }
            }
        }
        List<Hit> hits = new ArrayList<>();
        for (Candidate candidate : best.values()) {
            hits.add(toHit(candidate));
        }
        hits.sort(Comparator.comparingInt((Hit hit) -> hit.source().ordinal())
                .thenComparing(hit -> hit.name().toLowerCase(Locale.ROOT)));
        return hits;
    }

    private static boolean prefer(Candidate candidate, Candidate current) {
        if (candidate.source() != current.source()) {
            return candidate.source().ordinal() < current.source().ordinal();
        }
        return !hasName(current) && hasName(candidate);
    }

    private static boolean hasName(Candidate candidate) {
        return PersonNames.nameOrNull(candidate.accountDisplay()) != null
                || PersonNames.nameOrNull(candidate.accountFirst()) != null
                || PersonNames.nameOrNull(candidate.accountLast()) != null
                || PersonNames.nameOrNull(candidate.firstName()) != null
                || PersonNames.nameOrNull(candidate.lastName()) != null;
    }

    private static Hit toHit(Candidate candidate) {
        PersonNames.Names account = PersonNames.accountNames(candidate.accountDisplay(), candidate.accountFirst(),
                candidate.accountLast());
        PersonNames.Names roster = new PersonNames.Names(PersonNames.nameOrNull(candidate.firstName()),
                PersonNames.nameOrNull(candidate.lastName()));
        String first = account.firstName() != null ? account.firstName() : roster.firstName();
        String last = account.lastName() != null ? account.lastName() : roster.lastName();
        if (candidate.source() == Source.ROSTER) {
            first = roster.firstName() != null ? roster.firstName() : account.firstName();
            last = roster.lastName() != null ? roster.lastName() : account.lastName();
        }
        return new Hit(candidate.source(), candidate.source().label(), candidate.memberId(), candidate.userId(),
                candidate.email(),
                PersonNames.display(candidate.accountDisplay(), candidate.accountFirst(), candidate.accountLast(),
                        candidate.firstName(), candidate.lastName(), candidate.email()),
                blankToNull(candidate.organization()), first, last);
    }

    private static String key(Candidate candidate) {
        if (candidate.email() != null && !candidate.email().isBlank()) {
            return "e:" + candidate.email().trim().toLowerCase(Locale.ROOT);
        }
        if (candidate.userId() != null) {
            return "u:" + candidate.userId();
        }
        if (candidate.memberId() != null) {
            return "m:" + candidate.memberId();
        }
        return "n:" + PersonNames.display(candidate.accountDisplay(), candidate.accountFirst(), candidate.accountLast(),
                candidate.firstName(), candidate.lastName(), null).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
