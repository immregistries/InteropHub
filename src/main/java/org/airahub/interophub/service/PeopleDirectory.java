package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.airahub.interophub.dao.EsInitiativeMemberDao;
import org.airahub.interophub.dao.EsInitiativeOrganizationDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.dao.OrganizationDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsInitiativeMember;
import org.airahub.interophub.model.EsInitiativeOrganization;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.Organization;
import org.airahub.interophub.model.User;

/** Loads people-search candidates for one initiative. Results expose only name, email, and organization. */
public class PeopleDirectory {

    private static final int SOURCE_LIMIT = 15;
    private static final int RESULT_LIMIT = 20;

    private final EsInitiativeMemberDao memberDao = new EsInitiativeMemberDao();
    private final EsInitiativeOrganizationDao participationDao = new EsInitiativeOrganizationDao();
    private final OrganizationDao organizationDao = new OrganizationDao();
    private final UserDao userDao = new UserDao();
    private final EsSubscriptionDao subscriptionDao = new EsSubscriptionDao();
    private final EsTopicMeetingMemberDao seriesMemberDao = new EsTopicMeetingMemberDao();

    public List<PeopleSearch.Hit> search(Long initiativeId, String query) {
        if (!PeopleSearch.eligible(query) || initiativeId == null) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<PeopleSearch.Candidate> candidates = new ArrayList<>();
        Map<Long, User> users = new HashMap<>();
        Map<Long, String> organizations = organizationNames(initiativeId);
        int roster = 0;
        for (EsInitiativeMember member : memberDao.findByInitiativeId(initiativeId)) {
            String organization = organizations.get(member.getEsInitiativeOrganizationId());
            User account = account(users, member.getUserId());
            if (!contains(needle, member.getContactFirstName(), member.getContactLastName(), member.getEmail(),
                    organization, account == null ? null : account.getFirstName(),
                    account == null ? null : account.getLastName(),
                    account == null ? null : account.getDisplayName(),
                    account == null ? null : account.getEmail())) {
                continue;
            }
            candidates.add(candidate(PeopleSearch.Source.ROSTER, member, account, organization));
            if (++roster >= SOURCE_LIMIT) {
                break;
            }
        }
        int accounts = 0;
        for (User user : userDao.searchUsers(query)) {
            if (user.getStatus() == User.UserStatus.DELETED) {
                continue;
            }
            candidates.add(new PeopleSearch.Candidate(PeopleSearch.Source.ACCOUNT, null, user.getUserId(),
                    user.getEmail(), user.getDisplayName(), user.getFirstName(), user.getLastName(), null, null,
                    user.getOrganization()));
            if (++accounts >= SOURCE_LIMIT) {
                break;
            }
        }
        int known = 0;
        for (EsSubscription subscription : subscriptionDao.searchTopicContacts(query, SOURCE_LIMIT)) {
            User account = account(users, subscription.getUserId());
            candidates.add(new PeopleSearch.Candidate(PeopleSearch.Source.KNOWN, null, subscription.getUserId(),
                    subscription.getEmail(), account == null ? null : account.getDisplayName(),
                    account == null ? null : account.getFirstName(), account == null ? null : account.getLastName(),
                    subscription.getContactFirstName(), subscription.getContactLastName(),
                    subscription.getContactOrganization()));
            if (++known >= SOURCE_LIMIT) {
                break;
            }
        }
        int registrants = 0;
        for (EsTopicMeetingMember registrant : seriesMemberDao.searchByEmail(query, SOURCE_LIMIT)) {
            User account = account(users, registrant.getUserId());
            candidates.add(new PeopleSearch.Candidate(PeopleSearch.Source.KNOWN, null, registrant.getUserId(),
                    registrant.getEmail(), account == null ? null : account.getDisplayName(),
                    account == null ? null : account.getFirstName(), account == null ? null : account.getLastName(),
                    null, null, null));
            if (++registrants >= SOURCE_LIMIT) {
                break;
            }
        }
        List<PeopleSearch.Hit> hits = PeopleSearch.rank(candidates);
        return hits.size() <= RESULT_LIMIT ? hits : hits.subList(0, RESULT_LIMIT);
    }

    private PeopleSearch.Candidate candidate(PeopleSearch.Source source, EsInitiativeMember member, User account,
            String organization) {
        return new PeopleSearch.Candidate(source, member.getEsInitiativeMemberId(),
                member.getUserId() != null ? member.getUserId() : account == null ? null : account.getUserId(),
                member.getEmail() != null ? member.getEmail() : account == null ? null : account.getEmail(),
                account == null ? null : account.getDisplayName(), account == null ? null : account.getFirstName(),
                account == null ? null : account.getLastName(), member.getContactFirstName(),
                member.getContactLastName(), organization);
    }

    private Map<Long, String> organizationNames(Long initiativeId) {
        Map<Long, String> names = new HashMap<>();
        for (EsInitiativeOrganization participation : participationDao.findByInitiativeId(initiativeId)) {
            organizationDao.findById(participation.getOrganizationId())
                    .ifPresent(organization -> names.put(participation.getEsInitiativeOrganizationId(),
                            organization.getOrganizationName()));
        }
        return names;
    }

    private User account(Map<Long, User> users, Long userId) {
        if (userId == null) {
            return null;
        }
        if (users.containsKey(userId)) {
            return users.get(userId);
        }
        User user = userDao.findById(userId).filter(row -> row.getStatus() != User.UserStatus.DELETED).orElse(null);
        users.put(userId, user);
        return user;
    }

    private static boolean contains(String needle, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
