package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_topic_resource")
public class EsTopicResource {

    public enum ResourceType {
        IMAGE,
        PDF,
        DOCUMENT,
        PRESENTATION,
        EXTERNAL_LINK
    }

    public enum Status {
        ACTIVE,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "topic_resource_id")
    private Long topicResourceId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "stored_file_id", unique = true)
    private Long storedFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 24)
    private ResourceType resourceType;

    @Column(name = "external_url", length = 2000)
    private String externalUrl;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "attribution", length = 500)
    private String attribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

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
        if (status == null) {
            status = Status.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getTopicResourceId() { return topicResourceId; }
    public void setTopicResourceId(Long value) { topicResourceId = value; }
    public Long getEsTopicId() { return esTopicId; }
    public void setEsTopicId(Long value) { esTopicId = value; }
    public Long getStoredFileId() { return storedFileId; }
    public void setStoredFileId(Long value) { storedFileId = value; }
    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType value) { resourceType = value; }
    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String value) { externalUrl = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getAttribution() { return attribution; }
    public void setAttribution(String value) { attribution = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long value) { createdByUserId = value; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(Long value) { updatedByUserId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
