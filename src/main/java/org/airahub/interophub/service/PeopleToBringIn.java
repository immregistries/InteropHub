package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * People a Small Group might add: series registrants, topic followers, and
 * joined initiative members, excluding people who are already participants.
 * Only an APPROVED registration counts as registered.
 */
public final class PeopleToBringIn {

    public static final int PAGE_SIZE = 25;

    public record Person(String emailNormalized, Long userId, Long memberId, String accountDisplay, String accountFirst,
            String accountLast, String firstName, String lastName, String email, String organization,
            String initiativeStatus, String registrationStatus, boolean follows) {
    }

    public record Row(String name, String email, String organization, String initiativeStatus, String registrationLabel,
            boolean registered, boolean follows, boolean onInitiative, Long memberId, Long userId, String firstName,
            String lastName) {
    }

    public record Page(List<Row> rows, int page, int pages, int filteredTotal, int total, int registeredCount,
            int followsCount, int onInitiativeCount) {
    }

    private PeopleToBringIn() {
    }

    public static List<Row> assemble(List<Person> joinedMembers, List<Person> followers, List<Person> registrants,
            List<Person> participants) {
        Map<String, Mutable> merged = new LinkedHashMap<>();
        addAll(merged, joinedMembers);
        addAll(merged, followers);
        addAll(merged, registrants);
        List<Row> rows = new ArrayList<>();
        for (Mutable person : merged.values()) {
            if (matchesAny(person, participants)) {
                continue;
            }
            rows.add(person.toRow());
        }
        rows.sort(Comparator.comparing(row -> row.name().toLowerCase(Locale.ROOT)));
        return rows;
    }

    public static Page page(List<Row> rows, String filter, int requestedPage) {
        List<Row> source = rows == null ? List.of() : rows;
        int registered = 0;
        int follows = 0;
        int onInitiative = 0;
        for (Row row : source) {
            if (row.registered()) {
                registered++;
            }
            if (row.follows()) {
                follows++;
            }
            if (row.onInitiative()) {
                onInitiative++;
            }
        }
        List<Row> filtered = new ArrayList<>();
        for (Row row : source) {
            if (matchesFilter(row, filter)) {
                filtered.add(row);
            }
        }
        int total = filtered.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = requestedPage < 1 ? 1 : Math.min(requestedPage, pages);
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        return new Page(new ArrayList<>(filtered.subList(from, to)), page, pages, total, source.size(), registered,
                follows, onInitiative);
    }

    public static boolean matchesFilter(Row row, String filter) {
        if (row == null || filter == null || filter.isBlank() || "all".equals(filter)) {
            return row != null;
        }
        return switch (filter) {
            case "registered" -> row.registered();
            case "follows" -> row.follows();
            case "initiative" -> row.onInitiative();
            default -> true;
        };
    }

    public static boolean approved(String registrationStatus) {
        return "APPROVED".equals(registrationStatus);
    }

    private static void addAll(Map<String, Mutable> merged, List<Person> people) {
        if (people == null) {
            return;
        }
        for (Person person : people) {
            if (person == null) {
                continue;
            }
            String key = key(person.emailNormalized(), person.userId(), person.memberId());
            if (key == null) {
                continue;
            }
            merged.computeIfAbsent(key, ignored -> new Mutable()).absorb(person);
        }
    }

    private static boolean matchesAny(Mutable person, List<Person> participants) {
        if (participants == null) {
            return false;
        }
        for (Person participant : participants) {
            if (participant != null && same(person.emailNormalized, person.userId, person.memberId,
                    participant.emailNormalized(), participant.userId(), participant.memberId())) {
                return true;
            }
        }
        return false;
    }

    static boolean same(String emailA, Long userA, Long memberA, String emailB, Long userB, Long memberB) {
        if (emailA != null && !emailA.isBlank() && emailB != null && emailA.equalsIgnoreCase(emailB)) {
            return true;
        }
        if (userA != null && userA.equals(userB)) {
            return true;
        }
        return memberA != null && memberA.equals(memberB);
    }

    private static String key(String email, Long userId, Long memberId) {
        if (email != null && !email.isBlank()) {
            return "e:" + email.trim().toLowerCase(Locale.ROOT);
        }
        if (userId != null) {
            return "u:" + userId;
        }
        if (memberId != null) {
            return "m:" + memberId;
        }
        return null;
    }

    private static final class Mutable {
        private String emailNormalized;
        private Long userId;
        private Long memberId;
        private String accountDisplay;
        private String accountFirst;
        private String accountLast;
        private String firstName;
        private String lastName;
        private String email;
        private String organization;
        private String initiativeStatus;
        private String registrationStatus;
        private boolean follows;

        private void absorb(Person person) {
            emailNormalized = first(emailNormalized, person.emailNormalized());
            userId = userId == null ? person.userId() : userId;
            memberId = memberId == null ? person.memberId() : memberId;
            accountDisplay = first(accountDisplay, person.accountDisplay());
            accountFirst = first(accountFirst, person.accountFirst());
            accountLast = first(accountLast, person.accountLast());
            firstName = first(firstName, person.firstName());
            lastName = first(lastName, person.lastName());
            email = first(email, person.email());
            organization = first(organization, person.organization());
            if (person.initiativeStatus() != null) {
                initiativeStatus = person.initiativeStatus();
            }
            if (person.registrationStatus() != null) {
                registrationStatus = person.registrationStatus();
            }
            follows = follows || person.follows();
        }

        private Row toRow() {
            boolean registered = approved(registrationStatus);
            String registration = registered ? "Approved" : "REQUESTED".equals(registrationStatus) ? "Requested" : "—";
            return new Row(
                    PersonNames.display(accountDisplay, accountFirst, accountLast, firstName, lastName, email),
                    email, organization, initiativeStatus == null ? "—" : initiativeStatus, registration, registered,
                    follows, initiativeStatus != null, memberId, userId, PersonNames.nameOrNull(firstName),
                    PersonNames.nameOrNull(lastName));
        }

        private static String first(String current, String incoming) {
            if (current != null && !current.isBlank()) {
                return current;
            }
            return incoming == null || incoming.isBlank() ? current : incoming;
        }
    }
}
