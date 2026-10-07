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
@Table(name = "es_communication_bundle_resource_placement")
public class EsCommunicationBundleResourcePlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "placement_id")
    private Long placementId;

    @Column(name = "bundle_id", nullable = false)
    private Long bundleId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "component_id", nullable = false)
    private Long componentId;

    @Column(name = "topic_resource_id", nullable = false)
    private Long topicResourceId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "context_note", columnDefinition = "TEXT")
    private String contextNote;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getPlacementId() { return placementId; }
    public void setPlacementId(Long value) { placementId = value; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long value) { bundleId = value; }
    public Long getEsTopicId() { return esTopicId; }
    public void setEsTopicId(Long value) { esTopicId = value; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long value) { templateId = value; }
    public Long getComponentId() { return componentId; }
    public void setComponentId(Long value) { componentId = value; }
    public Long getTopicResourceId() { return topicResourceId; }
    public void setTopicResourceId(Long value) { topicResourceId = value; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int value) { displayOrder = value; }
    public String getContextNote() { return contextNote; }
    public void setContextNote(String value) { contextNote = value; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long value) { createdByUserId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
