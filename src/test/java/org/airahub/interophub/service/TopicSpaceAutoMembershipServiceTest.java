package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicSpaceMemberDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicSpaceMember;
import org.airahub.interophub.model.User;
import org.junit.jupiter.api.Test;

class TopicSpaceAutoMembershipServiceTest {

    private static final List<String> AIRA = List.of("immregistries.org");

    @Test
    void normalizeDomainListAcceptsCommonSeparatorsAndCanonicalizes() {
        assertEquals("immregistries.org, cdc.gov",
                TopicSpaceAutoMembershipService.normalizeDomainList(" @ImmRegistries.org;cdc.gov\n immregistries.org "));
        assertNull(TopicSpaceAutoMembershipService.normalizeDomainList("  , ; "));
        assertNull(TopicSpaceAutoMembershipService.normalizeDomainList(null));
    }

    @Test
    void normalizeDomainListRejectsAnythingThatIsNotADomain() {
        assertThrows(IllegalArgumentException.class,
                () -> TopicSpaceAutoMembershipService.normalizeDomainList("nbunker@immregistries.org"));
        assertThrows(IllegalArgumentException.class,
                () -> TopicSpaceAutoMembershipService.normalizeDomainList("localhost"));
        assertThrows(IllegalArgumentException.class,
                () -> TopicSpaceAutoMembershipService.normalizeDomainList("*.gov"));
    }

    @Test
    void qualifiesRequiresActiveVerifiedUserWithExactDomainMatch() {
        assertTrue(TopicSpaceAutoMembershipService.qualifies(user(1L, "a@immregistries.org", true), AIRA));
        assertTrue(TopicSpaceAutoMembershipService.qualifies(user(1L, " A@ImmRegistries.ORG ", true), AIRA));
        assertFalse(TopicSpaceAutoMembershipService.qualifies(user(1L, "a@immregistries.org", false), AIRA),
                "unverified email must not qualify");
        assertFalse(TopicSpaceAutoMembershipService.qualifies(user(1L, "a@evilimmregistries.org", true), AIRA),
                "suffix match must not qualify");
        assertFalse(TopicSpaceAutoMembershipService.qualifies(user(1L, "a@mail.immregistries.org", true), AIRA),
                "subdomain must not qualify");
        assertFalse(TopicSpaceAutoMembershipService.qualifies(user(1L, "a@cdc.gov", true), AIRA));

        User disabled = user(1L, "a@immregistries.org", true);
        disabled.setStatus(User.UserStatus.DISABLED);
        assertFalse(TopicSpaceAutoMembershipService.qualifies(disabled, AIRA));
    }

    @Test
    void appliesOnlyToPrivateSpacesWithDomains() {
        assertTrue(TopicSpaceAutoMembershipService.appliesTo(space(3L, EsTopicSpace.Visibility.PRIVATE, "immregistries.org")));
        assertFalse(TopicSpaceAutoMembershipService.appliesTo(space(3L, EsTopicSpace.Visibility.PUBLIC, "immregistries.org")));
        assertFalse(TopicSpaceAutoMembershipService.appliesTo(space(3L, EsTopicSpace.Visibility.PRIVATE, null)));
    }

    @Test
    void signInAddsOnlyWhenNoRowExistsSoRemovalsAndAdminsAreLeftAlone() {
        EsTopicSpace nursery = space(3L, EsTopicSpace.Visibility.PRIVATE, "immregistries.org");
        EsTopicSpace publicSpace = space(1L, EsTopicSpace.Visibility.PUBLIC, null);
        FakeMembers members = new FakeMembers();
        EsTopicSpaceMember removed = new EsTopicSpaceMember();
        removed.setEsTopicSpaceId(3L);
        removed.setUserId(20L);
        removed.setMemberStatus(EsTopicSpaceMember.MemberStatus.REMOVED);
        members.existing.add(removed);

        TopicSpaceAutoMembershipService service = service(List.of(publicSpace, nursery), members, List.of());

        service.applyForUser(user(10L, "new@immregistries.org", true));
        service.applyForUser(user(20L, "removed@immregistries.org", true));
        service.applyForUser(user(30L, "outside@cdc.gov", true));

        assertEquals(1, members.saved.size());
        EsTopicSpaceMember added = members.saved.get(0);
        assertEquals(3L, added.getEsTopicSpaceId());
        assertEquals(10L, added.getUserId());
        assertEquals(EsTopicSpaceMember.MemberRole.MEMBER, added.getRole());
        assertEquals(EsTopicSpaceMember.MemberStatus.ACTIVE, added.getMemberStatus());
        assertEquals(EsTopicSpaceMember.MemberSource.AUTO_DOMAIN, added.getMemberSource());
    }

    @Test
    void signInNeverFailsWhenAutoMembershipBreaks() {
        EsTopicSpaceMemberDao broken = new EsTopicSpaceMemberDao() {
            @Override
            public Optional<EsTopicSpaceMember> findBySpaceIdAndUserId(Long spaceId, Long userId) {
                throw new IllegalStateException("database down");
            }
        };
        TopicSpaceAutoMembershipService service = new TopicSpaceAutoMembershipService(
                spaces(List.of(space(3L, EsTopicSpace.Visibility.PRIVATE, "immregistries.org"))), broken, new UserDao());
        service.applyForUser(user(10L, "new@immregistries.org", true));
    }

    @Test
    void backfillSkipsAnyoneWithARowAndCountsAdditions() {
        EsTopicSpace nursery = space(3L, EsTopicSpace.Visibility.PRIVATE, "immregistries.org, cdc.gov");
        FakeMembers members = new FakeMembers();
        members.userIdsWithRow = Set.of(20L);
        List<User> users = List.of(
                user(10L, "a@immregistries.org", true),
                user(20L, "admin@immregistries.org", true),
                user(30L, "b@cdc.gov", true),
                user(40L, "c@immregistries.org", false),
                user(50L, "d@example.com", true));

        int added = service(List.of(nursery), members, users).backfillSpace(nursery);

        assertEquals(2, added);
        assertEquals(List.of(10L, 30L), members.saved.stream().map(EsTopicSpaceMember::getUserId).toList());
    }

    private static TopicSpaceAutoMembershipService service(List<EsTopicSpace> allSpaces, FakeMembers members,
            List<User> allUsers) {
        UserDao users = new UserDao() {
            @Override
            public List<User> findAllNonDeletedWithEmail() {
                return allUsers;
            }
        };
        return new TopicSpaceAutoMembershipService(spaces(allSpaces), members, users);
    }

    private static EsTopicSpaceDao spaces(List<EsTopicSpace> allSpaces) {
        return new EsTopicSpaceDao() {
            @Override
            public List<EsTopicSpace> findAllOrdered() {
                return allSpaces;
            }
        };
    }

    private static final class FakeMembers extends EsTopicSpaceMemberDao {
        final List<EsTopicSpaceMember> existing = new ArrayList<>();
        final List<EsTopicSpaceMember> saved = new ArrayList<>();
        Set<Long> userIdsWithRow = Set.of();

        @Override
        public Optional<EsTopicSpaceMember> findBySpaceIdAndUserId(Long spaceId, Long userId) {
            return existing.stream()
                    .filter(m -> m.getEsTopicSpaceId().equals(spaceId) && m.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public Set<Long> findUserIdsWithAnyRowBySpaceId(Long spaceId) {
            return userIdsWithRow;
        }

        @Override
        public EsTopicSpaceMember saveOrUpdate(EsTopicSpaceMember member) {
            saved.add(member);
            return member;
        }
    }

    private static User user(Long id, String email, boolean verified) {
        User user = new User();
        user.setUserId(id);
        user.setEmail(email);
        user.setEmailNormalized(email);
        user.setEmailVerified(verified);
        user.setStatus(User.UserStatus.ACTIVE);
        return user;
    }

    private static EsTopicSpace space(Long id, EsTopicSpace.Visibility visibility, String domains) {
        EsTopicSpace space = new EsTopicSpace();
        space.setEsTopicSpaceId(id);
        space.setVisibility(visibility);
        space.setAutoMemberEmailDomains(domains);
        return space;
    }
}
