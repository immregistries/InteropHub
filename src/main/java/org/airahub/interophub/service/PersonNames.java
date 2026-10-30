package org.airahub.interophub.service;

/**
 * Names stored on the roster and on topic follows. An email address is never a name.
 * Display order is the account name, then the roster name, then the email.
 */
public final class PersonNames {

    public record Names(String firstName, String lastName) {
    }

    private PersonNames() {
    }

    public static boolean looksLikeEmail(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        return at > 0 && at < trimmed.length() - 1 && trimmed.indexOf(' ') < 0 && trimmed.indexOf('@', at + 1) < 0;
    }

    /** A stored name, or null when the value is blank or an email address. */
    public static String nameOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty() || looksLikeEmail(trimmed)) {
            return null;
        }
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    /** Splits a single contact column on the last space. An email yields no name. */
    public static Names splitFullName(String raw) {
        String trimmed = nameOrNull(raw);
        if (trimmed == null) {
            return new Names(null, null);
        }
        int space = trimmed.lastIndexOf(' ');
        if (space < 0) {
            return new Names(trimmed, null);
        }
        return new Names(nameOrNull(trimmed.substring(0, space)), nameOrNull(trimmed.substring(space + 1)));
    }

    /** Account names. First and last win; a display-name override is split only when both are blank. */
    public static Names accountNames(String displayName, String firstName, String lastName) {
        String first = nameOrNull(firstName);
        String last = nameOrNull(lastName);
        if (first != null || last != null) {
            return new Names(first, last);
        }
        return splitFullName(displayName);
    }

    /**
     * Fills only blank name fields. A value that is an email is treated as blank,
     * so it is cleared or replaced and never kept as a name.
     */
    public static Names fillBlanks(String currentFirst, String currentLast, String incomingFirst, String incomingLast) {
        String first = nameOrNull(currentFirst);
        String last = nameOrNull(currentLast);
        if (first == null) {
            first = nameOrNull(incomingFirst);
        }
        if (last == null) {
            last = nameOrNull(incomingLast);
        }
        return new Names(first, last);
    }

    public static String display(String accountDisplay, String accountFirst, String accountLast, String rosterFirst,
            String rosterLast, String email) {
        String account = nameOrNull(accountDisplay);
        if (account == null) {
            account = join(nameOrNull(accountFirst), nameOrNull(accountLast));
        }
        if (account != null) {
            return account;
        }
        String roster = join(nameOrNull(rosterFirst), nameOrNull(rosterLast));
        if (roster != null) {
            return roster;
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return "Unnamed contact";
    }

    public static String join(String first, String last) {
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return name.isEmpty() ? null : name;
    }
}
