package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_magic_link_send_event")
public class MagicLinkSendEvent {
    public enum EventType {
        SEND_REQUESTED,
        SMTP_SEND_STARTED,
        SMTP_SEND_SUCCEEDED,
        SMTP_SEND_FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "send_event_id")
    private Long sendEventId;

    @Column(name = "magic_id")
    private Long magicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "app_id")
    private Long appId;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private EventType eventType;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "request_ip", length = 16)
    private byte[] requestIp;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "smtp_message_id", length = 255)
    private String smtpMessageId;

    @Column(name = "smtp_provider", length = 80)
    private String smtpProvider;

    @Column(name = "smtp_reply_code", length = 32)
    private String smtpReplyCode;

    @Column(name = "error_class", length = 120)
    private String errorClass;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "server_node", length = 120)
    private String serverNode;

    @PrePersist
    protected void onCreate() {
        if (eventAt == null) {
            eventAt = LocalDateTime.now();
        }
    }

    public Long getSendEventId() {
        return sendEventId;
    }

    public void setSendEventId(Long sendEventId) {
        this.sendEventId = sendEventId;
    }

    public Long getMagicId() {
        return magicId;
    }

    public void setMagicId(Long magicId) {
        this.magicId = magicId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public void setEmailNormalized(String emailNormalized) {
        this.emailNormalized = emailNormalized;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getEventAt() {
        return eventAt;
    }

    public void setEventAt(LocalDateTime eventAt) {
        this.eventAt = eventAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public byte[] getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(byte[] requestIp) {
        this.requestIp = requestIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
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

    public String getSmtpReplyCode() {
        return smtpReplyCode;
    }

    public void setSmtpReplyCode(String smtpReplyCode) {
        this.smtpReplyCode = smtpReplyCode;
    }

    public String getErrorClass() {
        return errorClass;
    }

    public void setErrorClass(String errorClass) {
        this.errorClass = errorClass;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getServerNode() {
        return serverNode;
    }

    public void setServerNode(String serverNode) {
        this.serverNode = serverNode;
    }
}
