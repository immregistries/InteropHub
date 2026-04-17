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
@Table(name = "es_interest")
public class EsInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_interest_id")
    private Long esInterestId;

    @Column(name = "es_campaign_id", nullable = false)
    private Long esCampaignId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "es_campaign_registration_id")
    private Long esCampaignRegistrationId;

    @Column(name = "session_key", length = 128)
    private String sessionKey;

    @Column(name = "table_no", nullable = false)
    private Integer tableNo;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getEsInterestId() {
        return esInterestId;
    }

    public void setEsInterestId(Long esInterestId) {
        this.esInterestId = esInterestId;
    }

    public Long getEsCampaignId() {
        return esCampaignId;
    }

    public void setEsCampaignId(Long esCampaignId) {
        this.esCampaignId = esCampaignId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public Long getEsCampaignRegistrationId() {
        return esCampaignRegistrationId;
    }

    public void setEsCampaignRegistrationId(Long esCampaignRegistrationId) {
        this.esCampaignRegistrationId = esCampaignRegistrationId;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public Integer getTableNo() {
        return tableNo;
    }

    public void setTableNo(Integer tableNo) {
        this.tableNo = tableNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
