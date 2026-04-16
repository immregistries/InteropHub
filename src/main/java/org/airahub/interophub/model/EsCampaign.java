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
@Table(name = "es_campaign")
public class EsCampaign {

    public enum CampaignStatus {
        DRAFT,
        ACTIVE,
        CLOSED,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_campaign_id")
    private Long esCampaignId;

    @Column(name = "campaign_code", nullable = false, unique = true, length = 80)
    private String campaignCode;

    @Column(name = "campaign_name", nullable = false, length = 160)
    private String campaignName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "campaign_type", nullable = false, length = 80)
    private String campaignType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CampaignStatus status;

    @Column(name = "allow_topic_comments", nullable = false)
    private Boolean allowTopicComments;

    @Column(name = "allow_general_comments", nullable = false)
    private Boolean allowGeneralComments;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = CampaignStatus.DRAFT;
        }
        if (campaignType == null) {
            campaignType = "DEEP_DIVE";
        }
        if (allowTopicComments == null) {
            allowTopicComments = Boolean.TRUE;
        }
        if (allowGeneralComments == null) {
            allowGeneralComments = Boolean.TRUE;
        }
    }

    public Long getEsCampaignId() {
        return esCampaignId;
    }

    public void setEsCampaignId(Long esCampaignId) {
        this.esCampaignId = esCampaignId;
    }

    public String getCampaignCode() {
        return campaignCode;
    }

    public void setCampaignCode(String campaignCode) {
        this.campaignCode = campaignCode;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public void setCampaignName(String campaignName) {
        this.campaignName = campaignName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCampaignType() {
        return campaignType;
    }

    public void setCampaignType(String campaignType) {
        this.campaignType = campaignType;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public Boolean getAllowTopicComments() {
        return allowTopicComments;
    }

    public void setAllowTopicComments(Boolean allowTopicComments) {
        this.allowTopicComments = allowTopicComments;
    }

    public Boolean getAllowGeneralComments() {
        return allowGeneralComments;
    }

    public void setAllowGeneralComments(Boolean allowGeneralComments) {
        this.allowGeneralComments = allowGeneralComments;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
