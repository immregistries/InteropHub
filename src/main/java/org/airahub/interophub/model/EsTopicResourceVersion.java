package org.airahub.interophub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_topic_resource_version")
public class EsTopicResourceVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resource_version_id")
    private Long resourceVersionId;
    @Column(name = "topic_resource_id", nullable = false)
    private Long topicResourceId;
    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;
    @Column(name = "stored_file_id")
    private Long storedFileId;
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 24)
    private EsTopicResource.ResourceType resourceType;
    @Column(name = "title", nullable = false, length = 255)
    private String title;
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    @Column(name = "attribution", length = 500)
    private String attribution;
    @Column(name = "external_url", length = 2000)
    private String externalUrl;
    @Column(name = "preserved_by_user_id", nullable = false)
    private Long preservedByUserId;
    @Column(name = "preserved_at", nullable = false)
    private LocalDateTime preservedAt;

    public Long getResourceVersionId() { return resourceVersionId; }
    public void setResourceVersionId(Long value) { resourceVersionId = value; }
    public Long getTopicResourceId() { return topicResourceId; }
    public void setTopicResourceId(Long value) { topicResourceId = value; }
    public Long getEsTopicId() { return esTopicId; }
    public void setEsTopicId(Long value) { esTopicId = value; }
    public Long getStoredFileId() { return storedFileId; }
    public void setStoredFileId(Long value) { storedFileId = value; }
    public EsTopicResource.ResourceType getResourceType() { return resourceType; }
    public void setResourceType(EsTopicResource.ResourceType value) { resourceType = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getAttribution() { return attribution; }
    public void setAttribution(String value) { attribution = value; }
    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String value) { externalUrl = value; }
    public Long getPreservedByUserId() { return preservedByUserId; }
    public void setPreservedByUserId(Long value) { preservedByUserId = value; }
    public LocalDateTime getPreservedAt() { return preservedAt; }
    public void setPreservedAt(LocalDateTime value) { preservedAt = value; }

    public EsTopicResource resource() {
        var resource = new EsTopicResource();
        resource.setTopicResourceId(topicResourceId);
        resource.setEsTopicId(esTopicId);
        resource.setStoredFileId(storedFileId);
        resource.setResourceType(resourceType);
        resource.setTitle(title);
        resource.setDescription(description);
        resource.setAttribution(attribution);
        resource.setExternalUrl(externalUrl);
        resource.setStatus(EsTopicResource.Status.ACTIVE);
        return resource;
    }
}
