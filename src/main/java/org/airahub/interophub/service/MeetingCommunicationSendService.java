package org.airahub.interophub.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.dao.EsMeetingCommunicationDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.HubSetting;

/**
 * Executes the actual email dispatch for a meeting communication.
 *
 * Two entry points:
 * - {@link #sendNow(Long)} — immediate send triggered by a user action.
 * - {@link #processDue()} — called by the background scheduler to send all
 * communications whose scheduledSendAt has passed.
 */
public class MeetingCommunicationSendService {

    private static final Logger LOGGER = Logger.getLogger(MeetingCommunicationSendService.class.getName());

    private final EsMeetingCommunicationDao communicationDao;
    private final EsMeetingDao meetingDao;
    private final MeetingCommunicationRecipientResolver resolver;
    private final MeetingCommunicationRenderer renderer;
    private final EmailService emailService;
    private final EmailSendLogDao emailSendLogDao;
    private final EsSubscriptionDao esSubscriptionDao;
    private final HubSettingDao hubSettingDao;

    public MeetingCommunicationSendService() {
        this.communicationDao = new EsMeetingCommunicationDao();
        this.meetingDao = new EsMeetingDao();
        this.resolver = new MeetingCommunicationRecipientResolver();
        this.renderer = new MeetingCommunicationRenderer();
        this.emailService = new EmailService();
        this.emailSendLogDao = new EmailSendLogDao();
        this.esSubscriptionDao = new EsSubscriptionDao();
        this.hubSettingDao = new HubSettingDao();
    }

    /**
     * Immediately sends a communication (must be in SCHEDULED or DRAFT status).
     * Uses an atomic CAS claim to prevent double-sends in concurrent environments.
     *
     * @throws IllegalArgumentException if the communication or meeting is not found
     * @throws IllegalStateException    if the communication cannot be claimed for
     *                                  sending
     */
    public void sendNow(Long esMeetingCommunicationId) {
        EsMeetingCommunication communication = communicationDao.findById(esMeetingCommunicationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Communication not found: " + esMeetingCommunicationId));

        // Ensure it is in a sendable state before claiming
        EsMeetingCommunication.CommunicationStatus status = communication.getStatus();
        if (status != EsMeetingCommunication.CommunicationStatus.SCHEDULED
                && status != EsMeetingCommunication.CommunicationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot send communication in status: " + status);
        }

        // Transition to SCHEDULED first if it is still DRAFT, so the CAS works
        if (status == EsMeetingCommunication.CommunicationStatus.DRAFT) {
            communication.setStatus(EsMeetingCommunication.CommunicationStatus.SCHEDULED);
            communication = communicationDao.saveOrUpdate(communication);
        }

        send(communication);
    }

    /**
     * Called by the background scheduler. Finds all due communications and sends
     * each one via {@link #send(EsMeetingCommunication)}.
     */
    public void processDue() {
        List<EsMeetingCommunication> due = communicationDao.findDueToSend(LocalDateTime.now());
        for (EsMeetingCommunication communication : due) {
            try {
                send(communication);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE,
                        "Failed to process due communication id=" + communication.getEsMeetingCommunicationId(),
                        ex);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Core send logic
    // ---------------------------------------------------------------------------

    private void send(EsMeetingCommunication communication) {
        // Atomic CAS: transition SCHEDULED -> SENDING (returns 0 if already taken)
        int claimed = communicationDao.claimForSending(communication.getEsMeetingCommunicationId());
        if (claimed == 0) {
            LOGGER.info("Communication id=" + communication.getEsMeetingCommunicationId()
                    + " was already claimed by another thread/node — skipping.");
            return;
        }

        EsMeeting meeting = meetingDao.findById(communication.getEsMeetingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Meeting not found for communication: " + communication.getEsMeetingCommunicationId()));

        List<CommunicationRecipientPreview> recipients = resolver.resolve(communication, meeting);
        String emailReason = renderer.handlerFor(communication.getCommunicationType()).emailReason();

        String baseUrl = hubSettingDao.findActive()
                .map(HubSetting::getExternalBaseUrl)
                .filter(u -> u != null && !u.isBlank())
                .map(u -> u.endsWith("/") ? u.substring(0, u.length() - 1) : u)
                .orElse("");

        int successCount = 0;
        int failCount = 0;
        StringBuilder errors = new StringBuilder();

        for (CommunicationRecipientPreview recipient : recipients) {
            try {
                if (esSubscriptionDao.hasGeneralUnsubscribed(recipient.getEmailNormalized())) {
                    continue;
                }

                CommunicationRenderedEmail rendered = renderer.render(communication, meeting, recipient);

                Long logId = emailSendLogDao.preInsert(
                        emailReason,
                        recipient.getEmail(),
                        recipient.getEmailNormalized(),
                        recipient.getUserId(),
                        rendered.getSubject(),
                        communication.getEsMeetingCommunicationId());

                String unsubscribeUrl = baseUrl + "/es/unsubscribe?email="
                        + URLEncoder.encode(recipient.getEmail(), StandardCharsets.UTF_8)
                        + "&log_id=" + logId;
                String finalBody = rendered.getBodyText()
                        + "\n\n--\nTo manage your email preferences: " + unsubscribeUrl;

                EmailService.SendResult result = emailService.send(
                        recipient.getEmail(), rendered.getSubject(), finalBody);

                emailSendLogDao.updateAfterSend(logId, finalBody,
                        result.getSmtpMessageId(), result.getSmtpProvider());

                successCount++;
            } catch (Exception ex) {
                failCount++;
                String msg = "Failed to send to " + recipient.getEmailNormalized() + ": " + ex.getMessage();
                LOGGER.log(Level.WARNING, msg, ex);
                if (errors.length() < 2000) {
                    errors.append(msg).append("\n");
                }
            }
        }

        if (failCount == 0) {
            communicationDao.markSent(communication.getEsMeetingCommunicationId(), LocalDateTime.now());
            LOGGER.info("Communication id=" + communication.getEsMeetingCommunicationId()
                    + " sent to " + successCount + " recipients.");
        } else if (successCount == 0) {
            communicationDao.markFailed(
                    communication.getEsMeetingCommunicationId(), errors.toString());
            LOGGER.severe("Communication id=" + communication.getEsMeetingCommunicationId()
                    + " failed — 0/" + (successCount + failCount) + " emails delivered.");
        } else {
            // Partial success: mark as SENT but record the errors
            communicationDao.markSent(communication.getEsMeetingCommunicationId(), LocalDateTime.now());
            LOGGER.warning("Communication id=" + communication.getEsMeetingCommunicationId()
                    + " partially sent: " + successCount + " ok, " + failCount + " failed. Errors: "
                    + errors);
        }
    }
}
