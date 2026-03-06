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
@Table(name = "workspace_progress")
public class WorkspaceProgress {
    public enum ProgressStatus {
        NO_PROGRESS,
        PROBLEMS,
        PARTIAL,
        WORKS,
        NOT_APPLICABLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "progress_id")
    private Long progressId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "step_id", nullable = false)
    private Long stepId;

    @Column(name = "client_system_id", nullable = false)
    private Long clientSystemId;

    @Column(name = "server_system_id", nullable = false)
    private Long serverSystemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProgressStatus status;

    @Column(name = "note", length = 800)
    private String note;

    @Column(name = "reported_by_user_id", nullable = false)
    private Long reportedByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ProgressStatus.NO_PROGRESS;
        }
    }

    public Long getProgressId() {
        return progressId;
    }

    public void setProgressId(Long progressId) {
        this.progressId = progressId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getStepId() {
        return stepId;
    }

    public void setStepId(Long stepId) {
        this.stepId = stepId;
    }

    public Long getClientSystemId() {
        return clientSystemId;
    }

    public void setClientSystemId(Long clientSystemId) {
        this.clientSystemId = clientSystemId;
    }

    public Long getServerSystemId() {
        return serverSystemId;
    }

    public void setServerSystemId(Long serverSystemId) {
        this.serverSystemId = serverSystemId;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public void setStatus(ProgressStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getReportedByUserId() {
        return reportedByUserId;
    }

    public void setReportedByUserId(Long reportedByUserId) {
        this.reportedByUserId = reportedByUserId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
