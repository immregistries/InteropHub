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

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_key", length = 128)
    private String sessionKey;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "email_normalized", length = 254)
    private String emailNormalized;

    @Column(name = "opt_in_topic_updates", nullable = false)
    private Boolean optInTopicUpdates;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (optInTopicUpdates == null) {
            optInTopicUpdates = Boolean.FALSE;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public void setEmailNormalized(String emailNormalized) {
        this.emailNormalized = emailNormalized;
    }

    public Boolean getOptInTopicUpdates() {
        return optInTopicUpdates;
    }

    public void setOptInTopicUpdates(Boolean optInTopicUpdates) {
        this.optInTopicUpdates = optInTopicUpdates;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
