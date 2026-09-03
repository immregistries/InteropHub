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
}
