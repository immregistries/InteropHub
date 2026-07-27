package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_topic_note")
public class EsTopicNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_note_id")
    private Long esTopicNoteId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "es_meeting_id")
    private Long esMeetingId;

    @Column(name = "es_meeting_agenda_item_id")
    private Long esMeetingAgendaItemId;

    @Column(name = "note_title", length = 200)
    private String noteTitle;

    @Column(name = "document_json", nullable = false, columnDefinition = "json")
    private String documentJson;

    @Column(name = "document_text", columnDefinition = "LONGTEXT")
    private String documentText;

    @Column(name = "revision_no", nullable = false)
    private Long revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TopicNoteStatus status;

    @Column(name = "active_editor_user_id")
    private Long activeEditorUserId;

    @Column(name = "active_editor_started_at")
    private LocalDateTime activeEditorStartedAt;

    @Column(name = "active_editor_version", nullable = false)
    private Long activeEditorVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finalize_at")
    private LocalDateTime finalizeAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_by_user_id")
    private Long finalizedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "finalization_method", length = 16)
    private TopicNoteFinalizationMethod finalizationMethod;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = TopicNoteStatus.OPEN;
        }
        if (revisionNo == null) {
            revisionNo = 1L;
        }
        if (activeEditorVersion == null) {
            activeEditorVersion = 0L;
        }
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicNoteId() {
        return esTopicNoteId;
    }

    public void setEsTopicNoteId(Long esTopicNoteId) {
        this.esTopicNoteId = esTopicNoteId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public Long getEsMeetingAgendaItemId() {
        return esMeetingAgendaItemId;
    }

    public void setEsMeetingAgendaItemId(Long esMeetingAgendaItemId) {
        this.esMeetingAgendaItemId = esMeetingAgendaItemId;
    }

    public String getNoteTitle() {
        return noteTitle;
    }

    public void setNoteTitle(String noteTitle) {
        this.noteTitle = noteTitle;
    }

    public String getDocumentJson() {
        return documentJson;
    }

    public void setDocumentJson(String documentJson) {
        this.documentJson = documentJson;
    }

    public String getDocumentText() {
        return documentText;
    }

    public void setDocumentText(String documentText) {
        this.documentText = documentText;
    }

    public Long getRevisionNo() {
        return revisionNo;
    }

    public void setRevisionNo(Long revisionNo) {
        this.revisionNo = revisionNo;
    }

    public TopicNoteStatus getStatus() {
        return status;
    }

    public void setStatus(TopicNoteStatus status) {
        this.status = status;
    }

    public Long getActiveEditorUserId() {
        return activeEditorUserId;
    }

    public void setActiveEditorUserId(Long activeEditorUserId) {
        this.activeEditorUserId = activeEditorUserId;
    }

    public LocalDateTime getActiveEditorStartedAt() {
        return activeEditorStartedAt;
    }

    public void setActiveEditorStartedAt(LocalDateTime activeEditorStartedAt) {
        this.activeEditorStartedAt = activeEditorStartedAt;
    }

    public Long getActiveEditorVersion() {
        return activeEditorVersion;
    }

    public void setActiveEditorVersion(Long activeEditorVersion) {
        this.activeEditorVersion = activeEditorVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getFinalizeAt() {
        return finalizeAt;
    }

    public void setFinalizeAt(LocalDateTime finalizeAt) {
        this.finalizeAt = finalizeAt;
    }

    public LocalDateTime getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(LocalDateTime finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public Long getFinalizedByUserId() {
        return finalizedByUserId;
    }

    public void setFinalizedByUserId(Long finalizedByUserId) {
        this.finalizedByUserId = finalizedByUserId;
    }

    public TopicNoteFinalizationMethod getFinalizationMethod() {
        return finalizationMethod;
    }

    public void setFinalizationMethod(TopicNoteFinalizationMethod finalizationMethod) {
        this.finalizationMethod = finalizationMethod;
    }
}