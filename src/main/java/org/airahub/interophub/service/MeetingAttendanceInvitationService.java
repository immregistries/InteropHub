package org.airahub.interophub.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.dao.EsMeetingAttendanceDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EmailSendLog;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.User;

/**
 * Meeting Attendance Console, Phase 3 (docs/meeting-attendance-console-design.md):
 * sends the "please confirm/sign your own attendance" invitation for an
 * observed-only attendance record with a known email. Mirrors
 * TopicFollowerManagementService's registered/unverified/not-registered
 * classification and resend-cooldown pattern rather than reusing its
 * topic-follower-specific email reasons/templates.
 */
public class MeetingAttendanceInvitationService {

    private static final Logger LOGGER = Logger.getLogger(MeetingAttendanceInvitationService.class.getName());

    /** Resend cooldown window, matching TopicFollowerManagementService.RESEND_COOLDOWN_DAYS. */
    public static final int RESEND_COOLDOWN_DAYS = TopicFollowerManagementService.RESEND_COOLDOWN_DAYS;

    private final EsMeetingAttendanceDao attendanceDao;
    private final EsMeetingDao meetingDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsTopicDao topicDao;
    private final EsSubscriptionDao subscriptionDao;
    private final UserDao userDao;
    private final EmailService emailService;
    private final EmailSendLogDao emailSendLogDao;
    private final HubLinkService hubLinkService;
    private final AuthFlowService authFlowService;

    public MeetingAttendanceInvitationService() {
        this.attendanceDao = new EsMeetingAttendanceDao();
        this.meetingDao = new EsMeetingDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.topicDao = new EsTopicDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.userDao = new UserDao();
        this.emailService = new EmailService();
        this.emailSendLogDao = new EmailSendLogDao();
        this.hubLinkService = new HubLinkService();
        this.authFlowService = new AuthFlowService();
    }

    /**
     * Sends the observed / registration / verify-email invite for an
     * observed-only attendance record with a known email, auto-detecting
     * which applies from whether that email belongs to a registered and/or
     * verified user - the caller doesn't need to know which template fires.
     */
    public TopicFollowerManagementService.Outcome<Void> sendInvite(Long attendanceId, HttpServletRequest request) {
        Optional<EsMeetingAttendance> attendanceOpt = attendanceDao.findById(attendanceId);
        if (attendanceOpt.isEmpty()) {
            return TopicFollowerManagementService.Outcome.failure("Attendance record not found.");
        }
        EsMeetingAttendance attendance = attendanceOpt.get();
        if (attendance.getSelfSignedAt() != null) {
            return TopicFollowerManagementService.Outcome.failure(
                    "This person has already self-signed - no invite needed.");
        }
        if (attendance.getEmail() == null || attendance.getEmail().isBlank()) {
            return TopicFollowerManagementService.Outcome.failure("An email address is required to send an invite.");
        }
        if (subscriptionDao.hasGeneralUnsubscribed(attendance.getEmailNormalized())) {
            return TopicFollowerManagementService.Outcome.failure(
                    "This address has unsubscribed from all InteropHub email.");
        }

        Optional<User> matchedUser = userDao.findByEmailNormalized(attendance.getEmailNormalized())
                .filter(u -> u.getStatus() != User.UserStatus.DELETED);
        TopicFollowerManagementService.FollowerStatus status =
                TopicFollowerManagementService.resolveFollowerStatus(matchedUser.orElse(null));

        String meetingName = meetingName(attendance.getEsMeetingId());
        String attendLink = buildAttendLink(attendance);

        try {
            switch (status) {
                case REGISTERED -> sendObservedInvite(attendance, matchedUser.get(), meetingName, attendLink);
                case NOT_REGISTERED -> sendRegistrationInvite(attendance, meetingName, attendLink);
                case UNVERIFIED -> sendVerifyEmailInvite(attendance, matchedUser.get(), meetingName, request);
            }
            return TopicFollowerManagementService.Outcome.success(null);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to send attendance invite to " + attendance.getEmail(), ex);
            return TopicFollowerManagementService.Outcome.failure("Failed to send invite: " + ex.getMessage());
        }
    }

    private void sendObservedInvite(EsMeetingAttendance attendance, User user, String meetingName,
            String attendLink) {
        String subject = EmailTemplates.meetingAttendanceObservedInviteSubject(meetingName);
        String body = EmailTemplates.meetingAttendanceObservedInviteBody(meetingName, attendLink);
        EmailService.SendResult result = emailService.send(attendance.getEmail(), subject, body);
        logSend(EmailReason.MEETING_ATTENDANCE_OBSERVED_INVITE, attendance, user, subject, body, result);
    }

    private void sendRegistrationInvite(EsMeetingAttendance attendance, String meetingName, String attendLink) {
        String subject = EmailTemplates.meetingAttendanceRegistrationInviteSubject(meetingName);
        String body = EmailTemplates.meetingAttendanceRegistrationInviteBody(meetingName, attendLink);
        EmailService.SendResult result = emailService.send(attendance.getEmail(), subject, body);
        logSend(EmailReason.MEETING_ATTENDANCE_REGISTRATION_INVITE, attendance, null, subject, body, result);
    }

    private void sendVerifyEmailInvite(EsMeetingAttendance attendance, User user, String meetingName,
            HttpServletRequest request) {
        String magicLinkUrl = authFlowService.issueMagicLink(user, request);
        String subject = EmailTemplates.meetingAttendanceVerifyEmailSubject();
        String body = EmailTemplates.meetingAttendanceVerifyEmailBody(meetingName, magicLinkUrl);
        EmailService.SendResult result = emailService.send(user.getEmail(), subject, body);
        logSend(EmailReason.MEETING_ATTENDANCE_VERIFY_EMAIL, attendance, user, subject, body, result);
    }

    /** Most recent invite-send timestamp across all three reasons, for the "last sent" display and resend cooldown. */
    public Optional<LocalDateTime> lastInviteSentAt(String emailNormalized) {
        if (emailNormalized == null) {
            return Optional.empty();
        }
        return java.util.stream.Stream.of(
                        EmailReason.MEETING_ATTENDANCE_OBSERVED_INVITE,
                        EmailReason.MEETING_ATTENDANCE_REGISTRATION_INVITE,
                        EmailReason.MEETING_ATTENDANCE_VERIFY_EMAIL)
                .map(reason -> emailSendLogDao.findMostRecentByEmailAndReason(emailNormalized, reason))
                .flatMap(Optional::stream)
                .map(EmailSendLog::getSentAt)
                .max(LocalDateTime::compareTo);
    }

    private String meetingName(Long esMeetingId) {
        if (esMeetingId == null) {
            return null;
        }
        return meetingDao.findById(esMeetingId).map(EsMeeting::getMeetingName).orElse(null);
    }

    /** The same self sign-in link (/attend/{topicCode}[/{meetingKey}]) used elsewhere in the app. */
    private String buildAttendLink(EsMeetingAttendance attendance) {
        EsTopicMeeting topicMeeting = topicMeetingDao.findById(attendance.getEsTopicMeetingId()).orElse(null);
        EsTopic topic = topicMeeting != null && topicMeeting.getEsTopicId() != null
                ? topicDao.findById(topicMeeting.getEsTopicId()).orElse(null)
                : null;
        if (topic == null || topic.getTopicCode() == null) {
            return hubLinkService.buildLink("/home");
        }
        String path = "/attend/" + URLEncoder.encode(topic.getTopicCode(), StandardCharsets.UTF_8);
        if (attendance.getEsMeetingId() != null) {
            EsMeeting meeting = meetingDao.findById(attendance.getEsMeetingId()).orElse(null);
            if (meeting != null && meeting.getMeetingKey() != null) {
                path += "/" + URLEncoder.encode(meeting.getMeetingKey(), StandardCharsets.UTF_8);
            }
        }
        return hubLinkService.buildLink(path);
    }

    private void logSend(String reason, EsMeetingAttendance attendance, User matchedUser, String subject,
            String body, EmailService.SendResult result) {
        EmailSendLog logEntry = new EmailSendLog();
        logEntry.setEmailReason(reason);
        logEntry.setRecipientEmail(attendance.getEmail());
        logEntry.setRecipientEmailNormalized(attendance.getEmailNormalized());
        logEntry.setUserId(matchedUser != null ? matchedUser.getUserId() : null);
        logEntry.setSubject(subject);
        logEntry.setBodyText(body);
        logEntry.setSmtpMessageId(result.getSmtpMessageId());
        logEntry.setSmtpProvider(result.getSmtpProvider());
        emailSendLogDao.log(logEntry);
    }
}
