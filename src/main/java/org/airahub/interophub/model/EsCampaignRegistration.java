package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_campaign_registration", indexes = {
        @Index(name = "ix_es_reg_campaign_time", columnList = "es_campaign_id, created_at"),
        @Index(name = "ix_es_reg_campaign_email", columnList = "es_campaign_id, email_normalized"),
        @Index(name = "ix_es_reg_session_campaign", columnList = "session_key, es_campaign_id")
})
public class EsCampaignRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_campaign_registration_id")
    private Long esCampaignRegistrationId;

    @Column(name = "es_campaign_id", nullable = false)
    private Long esCampaignId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "email_normalized", length = 254)
    private String emailNormalized;

    @Column(name = "general_updates_opt_in", nullable = false)
    private Boolean generalUpdatesOptIn;

    @Column(name = "session_key", length = 128)
    private String sessionKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (generalUpdatesOptIn == null) {
            generalUpdatesOptIn = Boolean.FALSE;
        }
    }

    public Long getEsCampaignRegistrationId() {
        return esCampaignRegistrationId;
    }

    public void setEsCampaignRegistrationId(Long esCampaignRegistrationId) {
        this.esCampaignRegistrationId = esCampaignRegistrationId;
    }

    public Long getEsCampaignId() {
        return esCampaignId;
    }

    public void setEsCampaignId(Long esCampaignId) {
        this.esCampaignId = esCampaignId;
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

    public Boolean getGeneralUpdatesOptIn() {
        return generalUpdatesOptIn;
    }

    public void setGeneralUpdatesOptIn(Boolean generalUpdatesOptIn) {
        this.generalUpdatesOptIn = generalUpdatesOptIn;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
