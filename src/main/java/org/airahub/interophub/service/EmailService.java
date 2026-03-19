package org.airahub.interophub.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
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
    // Email sending is enabled by default. Override with:
    // -Dinterophub.email.send.enabled=false
    private static final String EMAIL_SEND_ENABLED_PROPERTY = "interophub.email.send.enabled";

    private final HubSettingDao hubSettingDao;

    public EmailService() {
        this.hubSettingDao = new HubSettingDao();
    }

    public String sendWelcomeEmail(String recipientEmail) {
        HubSetting settings = hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .orElseGet(this::createDefaultSettings);
        String loginLink = buildHomeLink(settings.getExternalBaseUrl());
        return sendWelcomeEmail(recipientEmail, loginLink);
    }

    public String sendWelcomeEmail(String recipientEmail, String loginLink) {
        String normalizedEmail = normalizeEmail(recipientEmail);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Recipient email is required.");
        }
        if (loginLink == null || loginLink.isBlank()) {
            throw new IllegalArgumentException("Login link is required.");
        }

        HubSetting settings = hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .orElseGet(this::createDefaultSettings);

        validateSettings(settings);

        String trimmedLink = loginLink.trim();
        if (!isEmailSendEnabled()) {
            LOGGER.info("Welcome email send is disabled via property '" + EMAIL_SEND_ENABLED_PROPERTY
                    + "'. Returning login link without SMTP send.");
            return trimmedLink;
        }
        sendSmtpMessage(settings, normalizedEmail, trimmedLink);
        return trimmedLink;
    }

    private boolean isEmailSendEnabled() {
        return Boolean.parseBoolean(System.getProperty(EMAIL_SEND_ENABLED_PROPERTY, "true"));
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
        return hubSettingDao.saveOrUpdate(settings);
    }

    private void validateSettings(HubSetting settings) {
        if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
            throw new IllegalStateException("SMTP host is missing. Update /settings and save.");
        }
        if (settings.getSmtpPort() == null || settings.getSmtpPort() < 1 || settings.getSmtpPort() > 65535) {
            throw new IllegalStateException("SMTP port is invalid. Update /settings and save.");
        }
        if (Boolean.TRUE.equals(settings.getSmtpAuth())) {
            if (settings.getSmtpUsername() == null || settings.getSmtpUsername().isBlank()) {
                throw new IllegalStateException("SMTP username is missing. Update /settings and save.");
            }
            if (settings.getSmtpPassword() == null || settings.getSmtpPassword().isBlank()) {
                throw new IllegalStateException("SMTP password is missing. Update /settings and save.");
            }
        }
    }

    private void sendSmtpMessage(HubSetting settings, String recipientEmail, String loginLink) {
        Properties props = new Properties();
        props.put("mail.smtp.host", settings.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(settings.getSmtpPort()));
        props.put("mail.smtp.auth", String.valueOf(Boolean.TRUE.equals(settings.getSmtpAuth())));
        props.put("mail.smtp.starttls.enable", String.valueOf(Boolean.TRUE.equals(settings.getSmtpStarttls())));
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
            message.setSubject("Welcome to InteropHub", StandardCharsets.UTF_8.name());

            String textBody = "Welcome to InteropHub!\n\n"
                    + "Use this temporary link to continue:\n"
                    + loginLink + "\n\n"
                    + "This is a test email to verify SMTP delivery.";
            message.setText(textBody, StandardCharsets.UTF_8.name());

            Transport.send(message);
            LOGGER.info("Welcome email sent to " + recipientEmail + ".");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to send welcome email.", ex);
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
}
