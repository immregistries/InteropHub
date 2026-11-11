package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicSpaceMemberDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicSpaceMember;
import org.airahub.interophub.model.User;

/**
 * Adds people to a PRIVATE Topic Space automatically when their verified email is at one of the
 * space's {@code auto_member_email_domains}.
 *
 * <p>Auto-membership only ever inserts. A user who already has a row for the space, in any role or
 * status (including REMOVED), is left exactly as they are, so an admin's removal sticks and nobody
 * is downgraded. Taking a domain off the list, or a user's email moving to another domain, does not
 * remove anyone already added.
 */
public class TopicSpaceAutoMembershipService {

    private static final Logger LOGGER = Logger.getLogger(TopicSpaceAutoMembershipService.class.getName());
    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$");

    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicSpaceMemberDao memberDao;
    private final UserDao userDao;

    public TopicSpaceAutoMembershipService() {
        this(new EsTopicSpaceDao(), new EsTopicSpaceMemberDao(), new UserDao());
    }

    TopicSpaceAutoMembershipService(EsTopicSpaceDao topicSpaceDao, EsTopicSpaceMemberDao memberDao, UserDao userDao) {
        this.topicSpaceDao = topicSpaceDao;
        this.memberDao = memberDao;
        this.userDao = userDao;
    }

    /**
     * Called at sign-in, after the email has been verified. Never throws: a failure here is logged and
     * must not block the person from signing in.
     */
    public void applyForUser(User user) {
        if (user == null || user.getUserId() == null) {
            return;
        }
        List<EsTopicSpace> spaces;
        try {
            spaces = topicSpaceDao.findAllOrdered();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not load Topic Spaces for auto-membership.", ex);
            return;
        }
        for (EsTopicSpace space : spaces) {
            try {
                if (!appliesTo(space) || !qualifies(user, parseDomains(space.getAutoMemberEmailDomains()))) {
                    continue;
                }
                if (memberDao.findBySpaceIdAndUserId(space.getEsTopicSpaceId(), user.getUserId()).isPresent()) {
                    continue;
                }
                memberDao.saveOrUpdate(newAutoMember(space.getEsTopicSpaceId(), user.getUserId()));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Auto-membership failed for user " + user.getUserId()
                        + " in Topic Space " + space.getEsTopicSpaceId() + ".", ex);
            }
        }
    }

    /**
     * Adds every already-registered qualifying user who has no row for this space yet. Run when an admin
     * saves the space's domain list. Returns the number of members added.
     */
    public int backfillSpace(EsTopicSpace space) {
        if (!appliesTo(space)) {
            return 0;
        }
        List<String> domains = parseDomains(space.getAutoMemberEmailDomains());
        Set<Long> alreadyHasRow = memberDao.findUserIdsWithAnyRowBySpaceId(space.getEsTopicSpaceId());
        int added = 0;
        for (User user : userDao.findAllNonDeletedWithEmail()) {
            if (alreadyHasRow.contains(user.getUserId()) || !qualifies(user, domains)) {
                continue;
            }
            memberDao.saveOrUpdate(newAutoMember(space.getEsTopicSpaceId(), user.getUserId()));
            added++;
        }
        return added;
    }

    static boolean appliesTo(EsTopicSpace space) {
        return space != null && space.getEsTopicSpaceId() != null
                && space.getVisibility() == EsTopicSpace.Visibility.PRIVATE
                && !parseDomains(space.getAutoMemberEmailDomains()).isEmpty();
    }

    /** Active, verified email, and an exact (not suffix) match on the part after the '@'. */
    static boolean qualifies(User user, List<String> domains) {
        if (user == null || domains == null || domains.isEmpty()) {
            return false;
        }
        if (user.getStatus() != User.UserStatus.ACTIVE || !Boolean.TRUE.equals(user.getEmailVerified())) {
            return false;
        }
        String email = user.getEmailNormalized();
        if (email == null || email.isBlank()) {
            email = user.getEmail();
        }
        String domain = emailDomain(email);
        return domain != null && domains.contains(domain);
    }

    static String emailDomain(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        int at = trimmed.lastIndexOf('@');
        if (at < 1 || at == trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(at + 1);
    }

    /** Lenient parse of a stored list; skips anything that isn't a plausible domain. */
    static List<String> parseDomains(String raw) {
        List<String> domains = new ArrayList<>();
        for (String token : splitTokens(raw)) {
            if (DOMAIN_PATTERN.matcher(token).matches() && !domains.contains(token)) {
                domains.add(token);
            }
        }
        return domains;
    }

    /**
     * Strict parse of admin input. Accepts commas, semicolons, or whitespace as separators and an optional
     * leading '@'. Returns the canonical stored form ({@code "a.org, b.gov"}), or null when empty.
     *
     * @throws IllegalArgumentException naming the first entry that isn't a valid domain
     */
    public static String normalizeDomainList(String raw) {
        Set<String> domains = new LinkedHashSet<>();
        for (String token : splitTokens(raw)) {
            if (!DOMAIN_PATTERN.matcher(token).matches()) {
                throw new IllegalArgumentException("\"" + token + "\" is not a valid email domain"
                        + " (use a form like immregistries.org).");
            }
            domains.add(token);
        }
        if (domains.isEmpty()) {
            return null;
        }
        String joined = String.join(", ", domains);
        if (joined.length() > 500) {
            throw new IllegalArgumentException("The domain list is too long (500 characters at most).");
        }
        return joined;
    }

    private static List<String> splitTokens(String raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null) {
            return tokens;
        }
        for (String part : raw.split("[,;\\s]+")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            while (token.startsWith("@")) {
                token = token.substring(1);
            }
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static EsTopicSpaceMember newAutoMember(Long spaceId, Long userId) {
        EsTopicSpaceMember member = new EsTopicSpaceMember();
        member.setEsTopicSpaceId(spaceId);
        member.setUserId(userId);
        member.setRole(EsTopicSpaceMember.MemberRole.MEMBER);
        member.setMemberStatus(EsTopicSpaceMember.MemberStatus.ACTIVE);
        member.setMemberSource(EsTopicSpaceMember.MemberSource.AUTO_DOMAIN);
        return member;
    }
}
