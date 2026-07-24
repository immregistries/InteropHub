package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_topic_note_editor_history")
public class EsTopicNoteEditorHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_note_editor_history_id")
    private Long esTopicNoteEditorHistoryId;

    @Column(name = "es_topic_note_id", nullable = false)
    private Long esTopicNoteId;

    @Column(name = "previous_editor_user_id")
    private Long previousEditorUserId;

    @Column(name = "new_editor_user_id", nullable = false)
    private Long newEditorUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by_user_id", nullable = false)
    private Long changedByUserId;

    public Long getEsTopicNoteEditorHistoryId() {
        return esTopicNoteEditorHistoryId;
    }

    public void setEsTopicNoteEditorHistoryId(Long esTopicNoteEditorHistoryId) {
        this.esTopicNoteEditorHistoryId = esTopicNoteEditorHistoryId;
    }

    public Long getEsTopicNoteId() {
        return esTopicNoteId;
    }

    public void setEsTopicNoteId(Long esTopicNoteId) {
        this.esTopicNoteId = esTopicNoteId;
    }

    public Long getPreviousEditorUserId() {
        return previousEditorUserId;
    }

    public void setPreviousEditorUserId(Long previousEditorUserId) {
        this.previousEditorUserId = previousEditorUserId;
    }

    public Long getNewEditorUserId() {
        return newEditorUserId;
    }

    public void setNewEditorUserId(Long newEditorUserId) {
        this.newEditorUserId = newEditorUserId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(Long changedByUserId) {
        this.changedByUserId = changedByUserId;
    }
}