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
@Table(name = "es_live_vote_response")
public class EsLiveVoteResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_live_vote_response_id")
    private Long esLiveVoteResponseId;

    @Column(name = "es_live_vote_id", nullable = false)
    private Long esLiveVoteId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "response", nullable = false, length = 16)
    private LiveVoteResponseType response;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsLiveVoteResponseId() {
        return esLiveVoteResponseId;
    }

    public void setEsLiveVoteResponseId(Long esLiveVoteResponseId) {
        this.esLiveVoteResponseId = esLiveVoteResponseId;
    }

    public Long getEsLiveVoteId() {
        return esLiveVoteId;
    }

    public void setEsLiveVoteId(Long esLiveVoteId) {
        this.esLiveVoteId = esLiveVoteId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LiveVoteResponseType getResponse() {
        return response;
    }

    public void setResponse(LiveVoteResponseType response) {
        this.response = response;
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