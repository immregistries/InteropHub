package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "es_communication_bundle_audit")
public class EsCommunicationBundleAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "bundle_id")
    private Long bundleId;

    @Column(name = "topic_resource_id")
    private Long topicResourceId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "changed_by_user_id", nullable = false)
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static EsCommunicationBundleAudit event(Long bundleId, Long topicResourceId,
            String eventType, String details, Long changedByUserId) {
        EsCommunicationBundleAudit event = new EsCommunicationBundleAudit();
        event.setBundleId(bundleId);
        event.setTopicResourceId(topicResourceId);
        event.setEventType(eventType);
        event.setDetails(details);
        event.setChangedByUserId(changedByUserId);
        return event;
    }

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long value) { auditId = value; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long value) { bundleId = value; }
    public Long getTopicResourceId() { return topicResourceId; }
    public void setTopicResourceId(Long value) { topicResourceId = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getDetails() { return details; }
    public void setDetails(String value) { details = value; }
    public Long getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Long value) { changedByUserId = value; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime value) { changedAt = value; }
}
