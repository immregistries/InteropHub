package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_meeting_agenda_attachment")
public class EsMeetingAgendaAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long attachmentId;
    @Column(name = "es_meeting_agenda_item_id", nullable = false)
    private Long agendaItemId;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false)
    private StoredFile storedFile;
    @Column(name = "attached_by_user_id", nullable = false)
    private Long attachedByUserId;
    @Column(name = "attached_at", nullable = false)
    private LocalDateTime attachedAt;
    @Column(name = "removed_by_user_id")
    private Long removedByUserId;
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    public Long getAttachmentId() { return attachmentId; }
    public void setAttachmentId(Long value) { attachmentId = value; }
    public Long getAgendaItemId() { return agendaItemId; }
    public void setAgendaItemId(Long value) { agendaItemId = value; }
    public StoredFile getStoredFile() { return storedFile; }
    public void setStoredFile(StoredFile value) { storedFile = value; }
    public Long getAttachedByUserId() { return attachedByUserId; }
    public void setAttachedByUserId(Long value) { attachedByUserId = value; }
    public LocalDateTime getAttachedAt() { return attachedAt; }
    public void setAttachedAt(LocalDateTime value) { attachedAt = value; }
    public Long getRemovedByUserId() { return removedByUserId; }
    public void setRemovedByUserId(Long value) { removedByUserId = value; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime value) { removedAt = value; }
}
