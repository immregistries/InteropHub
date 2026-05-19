package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_send_log")
public class EmailSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_log_id")
    private Long emailLogId;

    @Column(name = "email_reason", nullable = false, length = 80)
    private String emailReason;

    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    @Column(name = "recipient_email_normalized", nullable = false, length = 254)
    private String recipientEmailNormalized;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "smtp_message_id", length = 255)
    private String smtpMessageId;

    @Column(name = "smtp_provider", length = 80)
    private String smtpProvider;

    @Column(name = "magic_id")
    private Long magicId;

    @Column(name = "es_meeting_communication_id")
    private Long esMeetingCommunicationId;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    public Long getEmailLogId() {
        return emailLogId;
    }

    public void setEmailLogId(Long emailLogId) {
        this.emailLogId = emailLogId;
    }

    public String getEmailReason() {
        return emailReason;
    }

    public void setEmailReason(String emailReason) {
        this.emailReason = emailReason;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getRecipientEmailNormalized() {
        return recipientEmailNormalized;
    }

    public void setRecipientEmailNormalized(String recipientEmailNormalized) {
        this.recipientEmailNormalized = recipientEmailNormalized;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

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

    public Long getMagicId() {
        return magicId;
    }

    public void setMagicId(Long magicId) {
        this.magicId = magicId;
    }

    public Long getEsMeetingCommunicationId() {
        return esMeetingCommunicationId;
    }

    public void setEsMeetingCommunicationId(Long esMeetingCommunicationId) {
        this.esMeetingCommunicationId = esMeetingCommunicationId;
    }
}
