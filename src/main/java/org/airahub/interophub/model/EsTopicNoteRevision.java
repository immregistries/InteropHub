package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_topic_note_revision")
public class EsTopicNoteRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_note_revision_id")
    private Long esTopicNoteRevisionId;

    @Column(name = "es_topic_note_id", nullable = false)
    private Long esTopicNoteId;

    @Column(name = "revision_no", nullable = false)
    private Long revisionNo;

    @Column(name = "document_json", nullable = false, columnDefinition = "json")
    private String documentJson;

    @Column(name = "document_text", columnDefinition = "LONGTEXT")
    private String documentText;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    @Column(name = "saved_by_user_id", nullable = false)
    private Long savedByUserId;

    public Long getEsTopicNoteRevisionId() {
        return esTopicNoteRevisionId;
    }

    public void setEsTopicNoteRevisionId(Long esTopicNoteRevisionId) {
        this.esTopicNoteRevisionId = esTopicNoteRevisionId;
    }

    public Long getEsTopicNoteId() {
        return esTopicNoteId;
    }

    public void setEsTopicNoteId(Long esTopicNoteId) {
        this.esTopicNoteId = esTopicNoteId;
    }

    public Long getRevisionNo() {
        return revisionNo;
    }

    public void setRevisionNo(Long revisionNo) {
        this.revisionNo = revisionNo;
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

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
    }

    public Long getSavedByUserId() {
        return savedByUserId;
    }

    public void setSavedByUserId(Long savedByUserId) {
        this.savedByUserId = savedByUserId;
    }
}