package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.User;
import org.junit.jupiter.api.Test;

class TopicFollowerManagementServiceTest {

    // -------------------------------------------------------------------------
    // canManageFollowers
    // -------------------------------------------------------------------------

    @Test
    void canManageFollowersAllowsAdminSpaceAdminOrChampionEquivalent() {
        assertTrue(TopicFollowerManagementService.canManageFollowers(true, false, false));
        assertTrue(TopicFollowerManagementService.canManageFollowers(false, true, false));
        assertTrue(TopicFollowerManagementService.canManageFollowers(false, false, true));
    }

    @Test
    void canManageFollowersDeniesUnrelatedUser() {
        assertFalse(TopicFollowerManagementService.canManageFollowers(false, false, false));
    }

    @Test
    void isChampionOrSupportForTopicMatchesByUserIdOrEmail() {
        User viewer = new User();
        viewer.setUserId(42L);
        viewer.setEmailNormalized("viewer@example.org");

        EsSubscription championByUserId = new EsSubscription();
        championByUserId.setUserId(42L);
        championByUserId.setStatus(EsSubscription.SubscriptionStatus.CHAMPION);

        assertTrue(TopicFollowerManagementService.isChampionOrSupportForTopic(viewer, List.of(championByUserId)));

        EsSubscription supportByEmail = new EsSubscription();
        supportByEmail.setUserId(null);
        supportByEmail.setEmailNormalized("viewer@example.org");
        supportByEmail.setStatus(EsSubscription.SubscriptionStatus.SUPPORT);

        assertTrue(TopicFollowerManagementService.isChampionOrSupportForTopic(viewer, List.of(supportByEmail)));

        EsSubscription plainFollower = new EsSubscription();
        plainFollower.setUserId(42L);
        plainFollower.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);

        assertFalse(TopicFollowerManagementService.isChampionOrSupportForTopic(viewer, List.of(plainFollower)));
        assertFalse(TopicFollowerManagementService.isChampionOrSupportForTopic(viewer, List.of()));
    }

    // -------------------------------------------------------------------------
    // resolveDisplayName / resolveDisplayOrganization
    // -------------------------------------------------------------------------

    @Test
    void resolveDisplayNamePrefersRegisteredUserThenContactNameThenEmail() {
        User registered = new User();
        registered.setFirstName("Ada");
        registered.setLastName("Lovelace");

        EsSubscription sub = new EsSubscription();
        sub.setContactFirstName("Grace");
        sub.setContactLastName("Hopper");
        sub.setEmail("someone@example.org");

        assertEquals("Ada Lovelace", TopicFollowerManagementService.resolveDisplayName(registered, sub));
        assertEquals("Grace Hopper", TopicFollowerManagementService.resolveDisplayName(null, sub));

        EsSubscription emailOnly = new EsSubscription();
        emailOnly.setEmail("someone@example.org");
        assertEquals("someone@example.org", TopicFollowerManagementService.resolveDisplayName(null, emailOnly));
    }

    @Test
    void hasDisplayNameReflectsRegisteredUserOrContactName() {
        User registered = new User();
        registered.setFirstName("Ada");
        registered.setLastName("Lovelace");
        assertTrue(TopicFollowerManagementService.hasDisplayName(registered, new EsSubscription()));

        EsSubscription contactOnly = new EsSubscription();
        contactOnly.setContactFirstName("Grace");
        assertTrue(TopicFollowerManagementService.hasDisplayName(null, contactOnly));

        EsSubscription emailOnly = new EsSubscription();
        emailOnly.setEmail("someone@example.org");
        assertFalse(TopicFollowerManagementService.hasDisplayName(null, emailOnly));
        assertFalse(TopicFollowerManagementService.hasDisplayName(null, null));
    }

    @Test
    void resolveDisplayOrganizationPrefersRegisteredUserThenContactOrganization() {
        User registered = new User();
        registered.setOrganization("CDC");

        EsSubscription sub = new EsSubscription();
        sub.setContactOrganization("Acme Health");

        assertEquals("CDC", TopicFollowerManagementService.resolveDisplayOrganization(registered, sub));
        assertEquals("Acme Health", TopicFollowerManagementService.resolveDisplayOrganization(null, sub));
        assertEquals("", TopicFollowerManagementService.resolveDisplayOrganization(null, new EsSubscription()));
    }

    // -------------------------------------------------------------------------
    // resolveFollowerStatus
    // -------------------------------------------------------------------------

    @Test
    void resolveFollowerStatusReflectsRegistrationAndVerification() {
        assertEquals(TopicFollowerManagementService.FollowerStatus.NOT_REGISTERED,
                TopicFollowerManagementService.resolveFollowerStatus(null));

        User unverified = new User();
        unverified.setStatus(User.UserStatus.ACTIVE);
        unverified.setEmailVerified(Boolean.FALSE);
        assertEquals(TopicFollowerManagementService.FollowerStatus.UNVERIFIED,
                TopicFollowerManagementService.resolveFollowerStatus(unverified));

        User verified = new User();
        verified.setStatus(User.UserStatus.ACTIVE);
        verified.setEmailVerified(Boolean.TRUE);
        assertEquals(TopicFollowerManagementService.FollowerStatus.REGISTERED,
                TopicFollowerManagementService.resolveFollowerStatus(verified));

        User deleted = new User();
        deleted.setStatus(User.UserStatus.DELETED);
        deleted.setEmailVerified(Boolean.TRUE);
        assertEquals(TopicFollowerManagementService.FollowerStatus.NOT_REGISTERED,
                TopicFollowerManagementService.resolveFollowerStatus(deleted));
    }

    // -------------------------------------------------------------------------
    // isWithinCooldown
    // -------------------------------------------------------------------------

    @Test
    void isWithinCooldownRespectsTheCooldownWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 12, 0);

        assertFalse(TopicFollowerManagementService.isWithinCooldown(null, now, 7));

        LocalDateTime justInside = now.minusDays(6).minusHours(23);
        assertTrue(TopicFollowerManagementService.isWithinCooldown(justInside, now, 7));

        LocalDateTime justOutside = now.minusDays(7).minusHours(1);
        assertFalse(TopicFollowerManagementService.isWithinCooldown(justOutside, now, 7));
    }
}
