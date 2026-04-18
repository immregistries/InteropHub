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
@Table(name = "es_topic_meeting_member")
public class EsTopicMeetingMember {

    public enum MembershipStatus {
        REQUESTED,
        APPROVED,
        DECLINED,
        REMOVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_meeting_member_id")
    private Long esTopicMeetingMemberId;

    @Column(name = "es_topic_meeting_id", nullable = false)
    private Long esTopicMeetingId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false, length = 16)
    private MembershipStatus membershipStatus;

    @Column(name = "source_campaign_id")
    private Long sourceCampaignId;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

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
        if (membershipStatus == null) {
            membershipStatus = MembershipStatus.REQUESTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicMeetingMemberId() {
        return esTopicMeetingMemberId;
    }

    public void setEsTopicMeetingMemberId(Long esTopicMeetingMemberId) {
        this.esTopicMeetingMemberId = esTopicMeetingMemberId;
    }

    public Long getEsTopicMeetingId() {
        return esTopicMeetingId;
    }

    public void setEsTopicMeetingId(Long esTopicMeetingId) {
        this.esTopicMeetingId = esTopicMeetingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public MembershipStatus getMembershipStatus() {
        return membershipStatus;
    }

    public void setMembershipStatus(MembershipStatus membershipStatus) {
        this.membershipStatus = membershipStatus;
    }

    public Long getSourceCampaignId() {
        return sourceCampaignId;
    }

    public void setSourceCampaignId(Long sourceCampaignId) {
        this.sourceCampaignId = sourceCampaignId;
    }

    public Long getApprovedByUserId() {
        return approvedByUserId;
    }

    public void setApprovedByUserId(Long approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
