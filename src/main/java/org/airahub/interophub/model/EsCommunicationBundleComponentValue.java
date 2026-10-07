package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_communication_bundle_component_value")
public class EsCommunicationBundleComponentValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_value_id")
    private Long componentValueId;

    @Column(name = "bundle_id", nullable = false)
    private Long bundleId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "component_id", nullable = false)
    private Long componentId;

    @Column(name = "content_text", columnDefinition = "LONGTEXT")
    private String contentText;

    @Column(name = "content_json", columnDefinition = "LONGTEXT")
    private String contentJson;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getComponentValueId() { return componentValueId; }
    public void setComponentValueId(Long value) { componentValueId = value; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long value) { bundleId = value; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long value) { templateId = value; }
    public Long getComponentId() { return componentId; }
    public void setComponentId(Long value) { componentId = value; }
    public String getContentText() { return contentText; }
    public void setContentText(String value) { contentText = value; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String value) { contentJson = value; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(Long value) { updatedByUserId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
