package org.airahub.interophub.service.digest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.DigestRunStateDao;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.model.DigestRunState;
import org.airahub.interophub.model.EmailSendLog;
import org.airahub.interophub.service.EmailReason;
import org.airahub.interophub.service.EmailService;
import org.airahub.interophub.service.EmailTemplates;

/**
 * Orchestrates the "InteropHub Daily Digest": once per day (after
 * {@link #RUN_TIME}, server local time), collects notices from every
 * registered {@link DigestItemSource}, groups them per recipient, and sends
 * one email per recipient who has at least one notice. Register new sources
 * in the constructor to extend the digest with another content type.
 */
public class DailyDigestService {

    private static final Logger LOGGER = Logger.getLogger(DailyDigestService.class.getName());

    private static final String DIGEST_KEY = "DAILY";
    private static final LocalTime RUN_TIME = LocalTime.of(6, 0);

    private final DigestRunStateDao runStateDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EmailService emailService;
    private final EmailSendLogDao emailSendLogDao;
    private final List<DigestItemSource> sources;

    public DailyDigestService() {
        this.runStateDao = new DigestRunStateDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.emailService = new EmailService();
        this.emailSendLogDao = new EmailSendLogDao();
        this.sources = List.of(new NewFollowersDigestSource());
    }

    /**
     * Runs the digest if it hasn't already run today and it's past the run time.
     */
    public void runIfDue() {
        LocalDateTime now = LocalDateTime.now();
        DigestRunState state = runStateDao.findById(DIGEST_KEY).orElse(null);
        if (state == null) {
            // First boot: seed silently so we don't email a burst of pre-existing
            // history; the first real digest covers activity from this point on.
            seedState(now);
            return;
        }
        if (state.getLastRunDate().isEqual(now.toLocalDate())) {
            return;
        }
        if (now.toLocalTime().isBefore(RUN_TIME)) {
            return;
        }

        LocalDateTime since = state.getLastRunAt();
        try {
            runDigest(since, now);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Daily digest run failed", ex);
        }
        state.setLastRunAt(now);
        state.setLastRunDate(now.toLocalDate());
        runStateDao.save(state);
    }

    private void seedState(LocalDateTime now) {
        DigestRunState state = new DigestRunState();
        state.setDigestKey(DIGEST_KEY);
        state.setLastRunAt(now);
        state.setLastRunDate(now.toLocalDate());
        runStateDao.save(state);
        LOGGER.info("Daily digest state seeded; first digest will run tomorrow and cover activity from now on.");
    }

    private void runDigest(LocalDateTime since, LocalDateTime until) {
        Map<String, List<DigestNotice>> noticesByRecipient = new LinkedHashMap<>();
        for (DigestItemSource source : sources) {
            try {
                for (DigestNotice notice : source.collect(since, until)) {
                    noticesByRecipient
                            .computeIfAbsent(notice.recipientEmailNormalized(), k -> new ArrayList<>())
                            .add(notice);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Digest source " + source.key() + " failed", ex);
            }
        }

        for (List<DigestNotice> notices : noticesByRecipient.values()) {
            if (notices.isEmpty()) {
                continue;
            }
            String emailNormalized = notices.get(0).recipientEmailNormalized();
            try {
                if (subscriptionDao.hasGeneralUnsubscribed(emailNormalized)) {
                    continue;
                }
                sendDigestEmail(notices);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to send daily digest to " + emailNormalized, ex);
            }
        }
    }

    private void sendDigestEmail(List<DigestNotice> notices) {
        DigestNotice first = notices.get(0);

        Map<String, List<String>> bodiesBySection = new LinkedHashMap<>();
        for (DigestNotice notice : notices) {
            bodiesBySection.computeIfAbsent(notice.sectionTitle(), k -> new ArrayList<>()).add(notice.bodyText());
        }

        String subject = EmailTemplates.dailyDigestSubject();
        String body = EmailTemplates.dailyDigestBody(bodiesBySection);

        EmailService.SendResult result = emailService.send(first.recipientEmail(), subject, body);

        EmailSendLog log = new EmailSendLog();
        log.setEmailReason(EmailReason.DAILY_DIGEST);
        log.setRecipientEmail(first.recipientEmail());
        log.setRecipientEmailNormalized(first.recipientEmailNormalized());
        log.setUserId(first.recipientUserId());
        log.setSubject(subject);
        log.setBodyText(body);
        log.setSmtpMessageId(result.getSmtpMessageId());
        log.setSmtpProvider(result.getSmtpProvider());
        emailSendLogDao.log(log);
    }
}
