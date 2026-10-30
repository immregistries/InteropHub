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
@Table(name = "es_topic_change")
public class EsTopicChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_change_id")
    private Long esTopicChangeId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "changed_by_user_id", nullable = false)
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "change_type", nullable = false, length = 16)
    private String changeType;

    @Column(name = "changes_json", nullable = false, columnDefinition = "JSON")
    private String changesJson;

    @Column(name = "app_user_token_id")
    private Long appUserTokenId;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }

    public Long getEsTopicChangeId() {
        return esTopicChangeId;
    }

    public void setEsTopicChangeId(Long esTopicChangeId) {
        this.esTopicChangeId = esTopicChangeId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(Long changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getChangesJson() {
        return changesJson;
    }

    public void setChangesJson(String changesJson) {
        this.changesJson = changesJson;
    }

    public Long getAppUserTokenId() {
        return appUserTokenId;
    }

    public void setAppUserTokenId(Long appUserTokenId) {
        this.appUserTokenId = appUserTokenId;
    }
}
