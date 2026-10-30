package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.Organization;
import org.junit.jupiter.api.Test;

class InitiativeFollowRulesTest {

    @Test
    void joinedCreatesOrRestoresASilentFollowAndRecordsTheInitiative() {
        Optional<InitiativeFollowRules.FollowRequest> created = InitiativeFollowRules.plan(
                "Risk-Based Forecasting Proof of Concept", 12L, "INVITED", "JOINED",
                "ada@example.org", "Ada", "Lovelace", "HLN", null);
        assertTrue(created.isPresent());
        assertFalse(created.get().sendNotification());
        assertEquals(12L, created.get().topicId());
        assertEquals("ada@example.org", created.get().email());
        assertEquals("Joined the Risk-Based Forecasting Proof of Concept initiative.", created.get().reason());

        Optional<InitiativeFollowRules.FollowRequest> restored = InitiativeFollowRules.plan(
                "Risk-Based Forecasting Proof of Concept", 12L, "PROSPECT", "JOINED",
                "ada@example.org", "Ada", "Lovelace", "HLN", EsSubscription.SubscriptionStatus.UNSUBSCRIBED);
        assertTrue(restored.isPresent());
        assertFalse(restored.get().sendNotification());
    }

    @Test
    void anExistingFollowIsLeftAlone() {
        assertTrue(InitiativeFollowRules.plan("Pilot", 3L, "INVITED", "JOINED", "a@example.org",
                "A", "B", null, EsSubscription.SubscriptionStatus.SUBSCRIBED).isEmpty());
        assertTrue(InitiativeFollowRules.plan("Pilot", 3L, null, "JOINED", "a@example.org",
                "A", "B", null, EsSubscription.SubscriptionStatus.CHAMPION).isEmpty());
        assertTrue(InitiativeFollowRules.plan("Pilot", 3L, "PROSPECT", "JOINED", "a@example.org",
                "A", "B", null, EsSubscription.SubscriptionStatus.SUPPORT).isEmpty());
    }

    @Test
    void prospectsInvitedPeopleAndMembersWithoutEmailDoNotFollow() {
        assertTrue(InitiativeFollowRules.plan("Pilot", 3L, "PROSPECT", "INVITED", "a@example.org",
                "A", "B", null, null).isEmpty());
        assertTrue(InitiativeFollowRules.plan("Pilot", 3L, "JOINED", "JOINED", "a@example.org",
                "A", "B", null, null).isEmpty());
        assertTrue(InitiativeFollowRules.plan("Pilot", 3L, "JOINED", "LEFT", "a@example.org",
                "A", "B", null, EsSubscription.SubscriptionStatus.SUBSCRIBED).isEmpty());
        assertTrue(InitiativeFollowRules.plan("Pilot", 3L, "INVITED", "JOINED", "  ",
                "A", "B", null, null).isEmpty());
    }

    @Test
    void anEmailIsNeverStoredAsAFollowName() {
        InitiativeFollowRules.FollowRequest follow = InitiativeFollowRules.plan(
                "Pilot", 3L, "INVITED", "JOINED", "ada@example.org", "ada@example.org", " ", null, null).orElseThrow();
        assertEquals(null, follow.firstName());
        assertEquals(null, follow.lastName());
        InitiativeFollowRules.FollowRequest smallGroup = SmallGroupParticipantRules.topicFollow(
                "Packet group", 9L, "ada@example.org", "ada@example.org", null, null, null).orElseThrow();
        assertEquals(null, smallGroup.firstName());
        assertEquals(null, smallGroup.lastName());
    }
}

class OrganizationDirectoryTest {

    @Test
    void typeAheadPrefersExactThenPrefixAndSkipsInactive() {
        Organization exact = org(1L, "HLN", true);
        Organization prefix = org(2L, "HLN Consulting", true);
        Organization contains = org(3L, "North HLN Partners", true);
        Organization inactive = org(4L, "HLN Retired", false);
        Organization other = org(5L, "Skyward", true);

        List<Organization> ranked = OrganizationDirectory.rank(
                List.of(contains, inactive, other, prefix, exact), "hln", 8);

        assertEquals(List.of(exact, prefix, contains), ranked);
    }

    @Test
    void addNewRequiresANameAndAKnownType() {
        assertEquals("Organization name is required.", OrganizationDirectory.validationError("  ", "EHR"));
        assertEquals("Choose an organization type.", OrganizationDirectory.validationError("Skyward", "VENDOR"));
        assertTrue(OrganizationDirectory.validationError("Skyward", "SCHOOL_INFORMATION_SYSTEM") == null);
        assertEquals("Jurisdiction", OrganizationDirectory.label("JURISDICTION"));
        assertEquals(OrganizationDirectory.TypeMatchKind.KNOWN,
                OrganizationDirectory.resolveImportedType("IIS Vendor").kind());
        assertEquals("IIS_VENDOR", OrganizationDirectory.resolveImportedType("IIS Vendor").code());
        assertEquals(OrganizationDirectory.TypeMatchKind.UNKNOWN,
                OrganizationDirectory.resolveImportedType("Hospital").kind());
    }

    private static Organization org(Long id, String name, boolean active) {
        Organization organization = new Organization();
        organization.setOrganizationId(id);
        organization.setOrganizationName(name);
        organization.setActive(active);
        return organization;
    }
}
