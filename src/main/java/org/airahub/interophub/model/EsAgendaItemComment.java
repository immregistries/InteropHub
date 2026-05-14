package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_agenda_item_comment")
public class EsAgendaItemComment {

    public enum CommentType {
        COMMENT,
        CHANGE_REQUEST,
        POSTPONE_REQUEST,
        DECLINE_REASON,
        MEETING_NOTE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_agenda_item_comment_id")
    private Long esAgendaItemCommentId;

    @Column(name = "es_meeting_agenda_item_id", nullable = false)
    private Long esMeetingAgendaItemId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_type", nullable = false, length = 20)
    private CommentType commentType;

    @Column(name = "comment_markdown", nullable = false, columnDefinition = "TEXT")
    private String commentMarkdown;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (commentType == null) {
            commentType = CommentType.COMMENT;
        }
    }

    public Long getEsAgendaItemCommentId() {
        return esAgendaItemCommentId;
    }

    public void setEsAgendaItemCommentId(Long esAgendaItemCommentId) {
        this.esAgendaItemCommentId = esAgendaItemCommentId;
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

    public CommentType getCommentType() {
        return commentType;
    }

    public void setCommentType(CommentType commentType) {
        this.commentType = commentType;
    }

    public String getCommentMarkdown() {
        return commentMarkdown;
    }

    public void setCommentMarkdown(String commentMarkdown) {
        this.commentMarkdown = commentMarkdown;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
