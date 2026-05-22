package org.airahub.interophub.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.model.HubSetting;

public class EmailService {
    private static final Logger LOGGER = Logger.getLogger(EmailService.class.getName());

    private final HubSettingDao hubSettingDao;

    public EmailService() {
        this.hubSettingDao = new HubSettingDao();
    }

    /**
     * Sends an email with the given subject and body to the given address.
     * This is the primary send method; all other send methods delegate here.
     *
     * @return SendResult containing smtpMessageId and smtpProvider from the SMTP
     *         server
     * @throws IllegalArgumentException if recipientEmail, subject, or bodyText are
     *                                  blank
     * @throws EmailSendException       if SMTP delivery fails
     */
    public SendResult send(String recipientEmail, String subject, String bodyText) {
        String normalizedEmail = normalizeEmail(recipientEmail);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Recipient email is required.");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Email subject is required.");
        }
        if (bodyText == null || bodyText.isBlank()) {
            throw new IllegalArgumentException("Email body is required.");
        }

        HubSetting settings = hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .orElseGet(this::createDefaultSettings);
        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            LOGGER.info("Email sending is disabled (email_enabled=false). Skipping send to " + normalizedEmail + ".");
            SendResult result = new SendResult();
            result.setSuppressed(true);
            return result;
        }
        validateSettings(settings);
        return sendSmtpMessage(settings, normalizedEmail, subject, bodyText.trim());
    }

    public void sendTestEmail(String recipientEmail) {
        String normalizedEmail = normalizeEmail(recipientEmail);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Recipient email is required.");
        }
        HubSetting settings = hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .orElseThrow(() -> new IllegalStateException("No hub settings found. Save settings first."));
        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            throw new IllegalStateException(
                    "Email sending is currently disabled. Enable it in hub settings to send test emails.");
        }
        validateSettings(settings);
        sendSmtpMessage(settings, normalizedEmail,
                "Test email from InteropHub",
                "This is a test email from InteropHub to confirm that SMTP delivery is working correctly.");
        LOGGER.info("Test email sent to " + normalizedEmail + ".");
    }

    /**
     * @deprecated Use {@link #send(String, String, String)} with
     *             {@link org.airahub.interophub.service.EmailTemplates}.
     */
    @Deprecated
    public void sendWelcomeEmail(String recipientEmail) {
        HubSetting settings = hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .orElseGet(this::createDefaultSettings);
        String loginLink = buildHomeLink(settings.getExternalBaseUrl());
        send(recipientEmail, EmailTemplates.magicLinkSubject(), EmailTemplates.magicLinkBody(loginLink));
    }

    /**
     * @deprecated Use {@link #send(String, String, String)} with
     *             {@link org.airahub.interophub.service.EmailTemplates}.
     */
    @Deprecated
    public void sendWelcomeEmail(String recipientEmail, String loginLink) {
        send(recipientEmail, EmailTemplates.magicLinkSubject(), EmailTemplates.magicLinkBody(loginLink));
    }

    /**
     * @deprecated Use {@link #send(String, String, String)} with
     *             {@link org.airahub.interophub.service.EmailTemplates}.
     */
    @Deprecated
    public SendResult sendWelcomeEmailWithResult(String recipientEmail, String loginLink) {
        return send(recipientEmail, EmailTemplates.magicLinkSubject(), EmailTemplates.magicLinkBody(loginLink));
    }

    private HubSetting createDefaultSettings() {
        HubSetting settings = new HubSetting();
        settings.setActive(Boolean.TRUE);
        settings.setExternalBaseUrl("http://localhost:8080/hub");
        settings.setSmtpHost("sandbox.smtp.mailtrap.io");
        settings.setSmtpPort(2525);
        settings.setSmtpUsername("");
        settings.setSmtpPassword("");
        settings.setSmtpAuth(Boolean.TRUE);
        settings.setSmtpStarttls(Boolean.TRUE);
        settings.setSmtpSsl(Boolean.FALSE);
        settings.setSmtpFromEmail("no-reply@interophub.local");
        settings.setSmtpFromName("InteropHub");
        settings.setEmailEnabled(Boolean.TRUE);
        return hubSettingDao.saveOrUpdate(settings);
    }

    private void validateSettings(HubSetting settings) {
        if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
            throw new IllegalStateException("SMTP host is missing. Update /admin/settings and save.");
        }
        if (settings.getSmtpPort() == null || settings.getSmtpPort() < 1 || settings.getSmtpPort() > 65535) {
            throw new IllegalStateException("SMTP port is invalid. Update /admin/settings and save.");
        }
        if (Boolean.TRUE.equals(settings.getSmtpAuth())) {
            if (settings.getSmtpUsername() == null || settings.getSmtpUsername().isBlank()) {
                throw new IllegalStateException("SMTP username is missing. Update /admin/settings and save.");
            }
            if (settings.getSmtpPassword() == null || settings.getSmtpPassword().isBlank()) {
                throw new IllegalStateException("SMTP password is missing. Update /admin/settings and save.");
            }
        }
    }

    private SendResult sendSmtpMessage(HubSetting settings, String recipientEmail, String subject, String bodyText) {
        Properties props = new Properties();
        props.put("mail.smtp.host", settings.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(settings.getSmtpPort()));
        props.put("mail.smtp.auth", String.valueOf(Boolean.TRUE.equals(settings.getSmtpAuth())));
        props.put("mail.smtp.starttls.enable", String.valueOf(Boolean.TRUE.equals(settings.getSmtpStarttls())));
        props.put("mail.smtp.starttls.required", String.valueOf(Boolean.TRUE.equals(settings.getSmtpStarttls())));
        props.put("mail.smtp.ssl.enable", String.valueOf(Boolean.TRUE.equals(settings.getSmtpSsl())));
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Authenticator authenticator = null;
        if (Boolean.TRUE.equals(settings.getSmtpAuth())) {
            authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(settings.getSmtpUsername(), settings.getSmtpPassword());
                }
            };
        }

        jakarta.mail.Session mailSession = jakarta.mail.Session.getInstance(props, authenticator);

        try {
            MimeMessage message = new MimeMessage(mailSession);
            String fromEmail = Optional.ofNullable(settings.getSmtpFromEmail())
                    .filter(v -> !v.isBlank())
                    .orElse(settings.getSmtpUsername());
            String fromName = Optional.ofNullable(settings.getSmtpFromName())
                    .filter(v -> !v.isBlank())
                    .orElse("InteropHub");

            message.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setText(bodyText, StandardCharsets.UTF_8.name());

            Transport.send(message);
            LOGGER.info("Email sent to " + recipientEmail + ".");

            SendResult result = new SendResult();
            result.setSmtpMessageId(trimToNull(message.getMessageID()));
            result.setSmtpProvider(trimToNull(settings.getSmtpHost()));
            return result;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to send welcome email.", ex);
            if (ex instanceof MessagingException messagingException) {
                throw new EmailSendException("SMTP send failed: " + ex.getMessage(), ex,
                        resolveSmtpReplyCode(messagingException), trimToNull(settings.getSmtpHost()));
            }
            if (ex instanceof MessagingException) {
                throw new IllegalStateException("SMTP send failed: " + ex.getMessage(), ex);
            }
            throw new IllegalStateException("Email send failed.", ex);
        }
    }

    private String buildHomeLink(String externalBaseUrl) {
        String base = Optional.ofNullable(externalBaseUrl)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .orElseThrow(() -> new IllegalStateException("external_base_url is required in hub_settings."));

        if (base.endsWith("/home")) {
            return base;
        }
        if (base.endsWith("/")) {
            return base + "home";
        }
        return base + "/home";
    }

    private String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }
        String normalized = rawEmail.trim().toLowerCase();
        if (normalized.isBlank() || !normalized.contains("@")) {
            return null;
        }
        return normalized;
    }

    private String resolveSmtpReplyCode(MessagingException ex) {
        Exception next = ex;
        while (next != null) {
            if (next instanceof SendFailedException sendFailedException) {
                String message = sendFailedException.getMessage();
                if (message != null && !message.isBlank()) {
                    return trimToMax(message, 32);
                }
            }
            if (next instanceof MessagingException messagingException) {
                next = messagingException.getNextException();
            } else {
                break;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToMax(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public static class SendResult {
        private String smtpMessageId;
        private String smtpProvider;
        private boolean suppressed;

        public String getSmtpMessageId() {
            return smtpMessageId;
        }

        public void setSmtpMessageId(String smtpMessageId) {
            this.smtpMessageId = smtpMessageId;
        }

        public String getSmtpProvider() {
            return smtpProvider;
        }

        public void setSmtpProvider(String smtpProvider) {
            this.smtpProvider = smtpProvider;
        }

        /** True when email sending is disabled and no SMTP attempt was made. */
        public boolean isSuppressed() {
            return suppressed;
        }

        public void setSuppressed(boolean suppressed) {
            this.suppressed = suppressed;
        }
    }

    /** @deprecated Use {@link SendResult}. */
    @Deprecated
    public static class SendWelcomeEmailResult extends SendResult {
    }

    public static class EmailSendException extends IllegalStateException {
        private final String smtpReplyCode;
        private final String smtpProvider;

        public EmailSendException(String message, Throwable cause, String smtpReplyCode, String smtpProvider) {
            super(message, cause);
            this.smtpReplyCode = smtpReplyCode;
            this.smtpProvider = smtpProvider;
        }

        public String getSmtpReplyCode() {
            return smtpReplyCode;
        }

        public String getSmtpProvider() {
            return smtpProvider;
        }
    }
}
