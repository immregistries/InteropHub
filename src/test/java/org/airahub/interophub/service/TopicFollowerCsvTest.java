package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.User;
import org.junit.jupiter.api.Test;

class TopicFollowerCsvTest {

    // -------------------------------------------------------------------------
    // escapeField
    // -------------------------------------------------------------------------

    @Test
    void escapeFieldLeavesPlainValuesAlone() {
        assertEquals("Jane Smith", TopicFollowerCsv.escapeField("Jane Smith"));
        assertEquals("", TopicFollowerCsv.escapeField(null));
        assertEquals("", TopicFollowerCsv.escapeField(""));
    }

    @Test
    void escapeFieldQuotesCommasQuotesAndLineBreaks() {
        assertEquals("\"Health Dept, State\"", TopicFollowerCsv.escapeField("Health Dept, State"));
        assertEquals("\"The \"\"Best\"\" Org\"", TopicFollowerCsv.escapeField("The \"Best\" Org"));
        assertEquals("\"line one\nline two\"", TopicFollowerCsv.escapeField("line one\nline two"));
    }

    @Test
    void escapeFieldNeutralizesSpreadsheetFormulas() {
        assertEquals("'=1+1", TopicFollowerCsv.escapeField("=1+1"));
        assertEquals("'+123", TopicFollowerCsv.escapeField("+123"));
        assertEquals("'-x", TopicFollowerCsv.escapeField("-x"));
        assertEquals("'@SUM(A1)", TopicFollowerCsv.escapeField("@SUM(A1)"));
        assertEquals("\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"",
                TopicFollowerCsv.escapeField("=HYPERLINK(\"x\",\"y\")"));
    }

    // -------------------------------------------------------------------------
    // toFields / toCsv
    // -------------------------------------------------------------------------

    @Test
    void registeredUserRowUsesAccountDetails() {
        User user = new User();
        user.setUserId(7L);
        user.setFirstName("Ana");
        user.setLastName("García");
        user.setOrganization("State IIS");
        user.setRoleTitle("Program Manager");
        user.setEmailVerified(true);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setLastLoginAt(LocalDateTime.of(2026, 9, 30, 14, 5));

        EsSubscription sub = subscription("ana@example.org", EsSubscription.SubscriptionStatus.CHAMPION);
        sub.setUserId(7L);

        List<String> fields = TopicFollowerCsv.toFields(
                new TopicFollowerManagementService.FollowerRow(sub, user, null, false));

        assertEquals(List.of("Ana", "García", "Ana García", "State IIS", "Program Manager", "ana@example.org",
                "Registered", "Champion", "No", "2026-01-15", "", "", "", "2026-09-30 14:05"), fields);
    }

    @Test
    void managedContactRowUsesContactDetailsAndFlagsOptOut() {
        User manager = new User();
        manager.setFirstName("Pat");
        manager.setLastName("Lee");

        EsSubscription sub = subscription("bob@example.org", EsSubscription.SubscriptionStatus.SUBSCRIBED);
        sub.setContactFirstName("Bob");
        sub.setContactLastName("Jones");
        sub.setContactOrganization("County Health");
        sub.setManagedAddedAt(LocalDateTime.of(2026, 2, 1, 9, 0));
        sub.setManagedAddReason("requested by email");

        List<String> fields = TopicFollowerCsv.toFields(
                new TopicFollowerManagementService.FollowerRow(sub, null, manager, true));

        assertEquals(List.of("Bob", "Jones", "Bob Jones", "County Health", "", "bob@example.org",
                "Not registered", "Follower", "Yes", "2026-01-15", "Pat Lee", "2026-02-01",
                "requested by email", ""), fields);
    }

    @Test
    void rowWithNoNameLeavesNameColumnsBlank() {
        EsSubscription sub = subscription("anon@example.org", EsSubscription.SubscriptionStatus.SUPPORT);

        List<String> fields = TopicFollowerCsv.toFields(
                new TopicFollowerManagementService.FollowerRow(sub, null, null, false));

        assertEquals("", fields.get(0));
        assertEquals("", fields.get(1));
        assertEquals("", fields.get(2));
        assertEquals("Support", fields.get(7));
    }

    @Test
    void toCsvWritesHeaderAndCrlfLines() {
        EsSubscription sub = subscription("a@example.org", EsSubscription.SubscriptionStatus.SUBSCRIBED);
        String csv = TopicFollowerCsv.toCsv(List.of(
                new TopicFollowerManagementService.FollowerRow(sub, null, null, false)));

        String[] lines = csv.split("\r\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith("First Name,Last Name,Full Name,Organization,"));
        assertTrue(lines[0].contains("Opted Out of Email"));
        assertTrue(lines[1].contains("a@example.org"));
        assertEquals(TopicFollowerCsv.HEADERS.size() - 1, lines[1].chars().filter(c -> c == ',').count());
    }

    // -------------------------------------------------------------------------
    // fileName
    // -------------------------------------------------------------------------

    @Test
    void fileNameIsSlugifiedAndDated() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        assertEquals("immunization-data-quality-followers-2026-10-03.csv",
                TopicFollowerCsv.fileName("Immunization Data: Quality!", date));
        assertEquals("topic-followers-2026-10-03.csv", TopicFollowerCsv.fileName(null, date));
        assertEquals("topic-followers-2026-10-03.csv", TopicFollowerCsv.fileName("\"\"", date));
    }

    private static EsSubscription subscription(String email, EsSubscription.SubscriptionStatus status) {
        EsSubscription sub = new EsSubscription();
        sub.setEmail(email);
        sub.setEmailNormalized(email);
        sub.setStatus(status);
        sub.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 0));
        return sub;
    }
}
