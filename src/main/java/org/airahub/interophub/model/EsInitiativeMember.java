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
@Table(name = "es_initiative_member")
public class EsInitiativeMember {

    public enum MemberStatus {
        PROSPECT("Prospect"),
        INVITED("Invited"),
        APPLIED("Applied"),
        JOINED("Joined"),
        DECLINED("Declined"),
        NOT_ACCEPTED("Not accepted"),
        LEFT("Left");

        private final String label;

        MemberStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            return CodedLabel.labelOf(MemberStatus.class, code, value -> value.label);
        }
    }

    public enum MemberSource {
        STAFF_ADDED("Added by staff"),
        APPLIED("Applied"),
        FOLLOWER_IMPORT("Imported from followers"),
        SPREADSHEET_IMPORT("Imported from a spreadsheet"),
        SMALL_GROUP("Added from a Small Group");

        private final String label;

        MemberSource(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            return CodedLabel.labelOf(MemberSource.class, code, value -> value.label);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_initiative_member_id")
    private Long esInitiativeMemberId;

    @Column(name = "es_initiative_id", nullable = false)
    private Long esInitiativeId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "email_normalized", length = 254)
    private String emailNormalized;

    @Column(name = "contact_first_name", length = 100)
    private String contactFirstName;

    @Column(name = "contact_last_name", length = 100)
    private String contactLastName;

    @Column(name = "es_initiative_organization_id")
    private Long esInitiativeOrganizationId;

    @Column(name = "initiative_role", length = 200)
    private String initiativeRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false, length = 32)
    private MemberStatus memberStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_source", nullable = false, length = 32)
    private MemberSource memberSource;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "status_changed_by_user_id")
    private Long statusChangedByUserId;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsInitiativeMemberId() {
        return esInitiativeMemberId;
    }

    public void setEsInitiativeMemberId(Long esInitiativeMemberId) {
        this.esInitiativeMemberId = esInitiativeMemberId;
    }

    public Long getEsInitiativeId() {
        return esInitiativeId;
    }

    public void setEsInitiativeId(Long esInitiativeId) {
        this.esInitiativeId = esInitiativeId;
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

    public String getContactFirstName() {
        return contactFirstName;
    }

    public void setContactFirstName(String contactFirstName) {
        this.contactFirstName = contactFirstName;
    }

    public String getContactLastName() {
        return contactLastName;
    }

    public void setContactLastName(String contactLastName) {
        this.contactLastName = contactLastName;
    }

    public Long getEsInitiativeOrganizationId() {
        return esInitiativeOrganizationId;
    }

    public void setEsInitiativeOrganizationId(Long esInitiativeOrganizationId) {
        this.esInitiativeOrganizationId = esInitiativeOrganizationId;
    }

    public String getInitiativeRole() {
        return initiativeRole;
    }

    public void setInitiativeRole(String initiativeRole) {
        this.initiativeRole = initiativeRole;
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

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String displayName() {
        return org.airahub.interophub.service.PersonNames.display(null, null, null, contactFirstName, contactLastName,
                email);
    }
}
