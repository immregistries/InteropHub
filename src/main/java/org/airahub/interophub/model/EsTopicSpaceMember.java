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
@Table(name = "es_topic_space_member")
public class EsTopicSpaceMember {

    public enum MemberRole {
        MEMBER,
        ADMIN
    }

    /** Only ACTIVE rows grant access. REMOVED is kept (not deleted) so auto-membership never re-adds that person. */
    public enum MemberStatus {
        ACTIVE,
        REMOVED
    }

    public enum MemberSource {
        MANUAL("Added by admin"),
        AUTO_DOMAIN("Auto-added by email domain");

        private final String label;

        MemberSource(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_space_member_id")
    private Long esTopicSpaceMemberId;

    @Column(name = "es_topic_space_id", nullable = false)
    private Long esTopicSpaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false, length = 16)
    private MemberStatus memberStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_source", nullable = false, length = 16)
    private MemberSource memberSource;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "status_changed_by_user_id")
    private Long statusChangedByUserId;

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
        if (role == null) {
            role = MemberRole.MEMBER;
        }
        if (memberStatus == null) {
            memberStatus = MemberStatus.ACTIVE;
        }
        if (memberSource == null) {
            memberSource = MemberSource.MANUAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicSpaceMemberId() {
        return esTopicSpaceMemberId;
    }

    public void setEsTopicSpaceMemberId(Long esTopicSpaceMemberId) {
        this.esTopicSpaceMemberId = esTopicSpaceMemberId;
    }

    public Long getEsTopicSpaceId() {
        return esTopicSpaceId;
    }

    public void setEsTopicSpaceId(Long esTopicSpaceId) {
        this.esTopicSpaceId = esTopicSpaceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public MemberStatus getMemberStatus() {
        return memberStatus;
    }

    public void setMemberStatus(MemberStatus memberStatus) {
        this.memberStatus = memberStatus;
    }

    public MemberSource getMemberSource() {
        return memberSource;
    }

    public void setMemberSource(MemberSource memberSource) {
        this.memberSource = memberSource;
    }

    public LocalDateTime getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(LocalDateTime statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public Long getStatusChangedByUserId() {
        return statusChangedByUserId;
    }

    public void setStatusChangedByUserId(Long statusChangedByUserId) {
        this.statusChangedByUserId = statusChangedByUserId;
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