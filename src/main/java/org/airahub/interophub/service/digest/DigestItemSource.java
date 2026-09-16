package org.airahub.interophub.service.digest;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A single content type contributed to the daily digest (e.g. new topic
 * followers). Add a new implementation and register it in
 * {@link DailyDigestService} to extend the digest with another reason people
 * might need to hear from InteropHub — each source owns its own recipient
 * resolution.
 */
public interface DigestItemSource {

    /** Stable key for logging, e.g. "NEW_FOLLOWERS". */
    String key();

    /** Returns notices for everything new in (since, until]. */
    List<DigestNotice> collect(LocalDateTime since, LocalDateTime until);

    /**
     * Whether a recipient's general community unsubscribe
     * ({@code EsSubscriptionDao.hasGeneralUnsubscribed}) suppresses this
     * source's notices. True for community content (the default - matches
     * every source that existed before this method was added). Operational
     * reminders tied to a person's own staff responsibilities (e.g. the
     * meeting-cadence action queue) should return false here: a community
     * content opt-out must not silently stop someone from being reminded of
     * their own required duties. See docs/interophub-meeting-cadence-design.md
     * ("Operational reminders to authorized staff may require different
     * preference handling from community subscription email").
     */
    default boolean respectsCommunityUnsubscribe() {
        return true;
    }
}
