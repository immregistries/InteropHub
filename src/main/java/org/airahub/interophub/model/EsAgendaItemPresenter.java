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
@Table(name = "es_agenda_item_presenter")
public class EsAgendaItemPresenter {

    public enum PresenterRole {
        LEAD,
        SUPPORTING,
        FACILITATOR,
        REQUESTED_REVIEWER
    }

    public enum PresenterStatus {
        INVITED,
        ACCEPTED,
        DECLINED,
        NEEDS_CHANGES,
        REMOVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_agenda_item_presenter_id")
    private Long esAgendaItemPresenterId;

    @Column(name = "es_meeting_agenda_item_id", nullable = false)
    private Long esMeetingAgendaItemId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "presenter_role", nullable = false, length = 20)
    private PresenterRole presenterRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PresenterStatus status;

    @Column(name = "response_note", columnDefinition = "TEXT")
    private String responseNote;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

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
            status = PresenterStatus.INVITED;
        }
        if (presenterRole == null) {
            presenterRole = PresenterRole.LEAD;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsAgendaItemPresenterId() {
        return esAgendaItemPresenterId;
    }

    public void setEsAgendaItemPresenterId(Long esAgendaItemPresenterId) {
        this.esAgendaItemPresenterId = esAgendaItemPresenterId;
    }

    public Long getEsMeetingAgendaItemId() {
        return esMeetingAgendaItemId;
    }

    public void setEsMeetingAgendaItemId(Long esMeetingAgendaItemId) {
        this.esMeetingAgendaItemId = esMeetingAgendaItemId;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public PresenterRole getPresenterRole() {
        return presenterRole;
    }

    public void setPresenterRole(PresenterRole presenterRole) {
        this.presenterRole = presenterRole;
    }

    public PresenterStatus getStatus() {
        return status;
    }

    public void setStatus(PresenterStatus status) {
        this.status = status;
    }

    public String getResponseNote() {
        return responseNote;
    }

    public void setResponseNote(String responseNote) {
        this.responseNote = responseNote;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
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
