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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_checklist_item")
public class EsChecklistItem {

    public enum Origin {
        TEMPLATE,
        CUSTOM
    }

    public enum Status {
        OPEN("Open"),
        DONE("Done"),
        NOT_APPLICABLE("Not applicable");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            if (code == null) {
                return "";
            }
            for (Status status : values()) {
                if (status.name().equals(code)) {
                    return status.label;
                }
            }
            return code;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_checklist_item_id")
    private Long esChecklistItemId;

    @Column(name = "es_checklist_id", nullable = false)
    private Long esChecklistId;

    @Column(name = "item_key", nullable = false, length = 80)
    private String itemKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_origin", nullable = false, length = 32)
    private Origin itemOrigin;

    @Column(name = "in_current_template", nullable = false)
    private Boolean inCurrentTemplate;

    @Column(name = "is_removed", nullable = false)
    private Boolean removed;

    @Column(name = "is_locally_edited", nullable = false)
    private Boolean locallyEdited;

    @Column(name = "phase_label", nullable = false, length = 200)
    private String phaseLabel;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "guidance", columnDefinition = "TEXT")
    private String guidance;

    @Column(name = "due_anchor", length = 40)
    private String dueAnchor;

    @Column(name = "due_offset_days")
    private Integer dueOffsetDays;

    @Column(name = "due_date_override")
    private LocalDate dueDateOverride;

    @Column(name = "check_code", length = 80)
    private String checkCode;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 32)
    private Status itemStatus;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "status_changed_by_user_id")
    private Long statusChangedByUserId;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

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
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (removed == null) {
            removed = Boolean.FALSE;
        }
        if (locallyEdited == null) {
            locallyEdited = Boolean.FALSE;
        }
        if (inCurrentTemplate == null) {
            inCurrentTemplate = Boolean.TRUE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsChecklistItemId() {
        return esChecklistItemId;
    }

    public void setEsChecklistItemId(Long esChecklistItemId) {
        this.esChecklistItemId = esChecklistItemId;
    }

    public Long getEsChecklistId() {
        return esChecklistId;
    }

    public void setEsChecklistId(Long esChecklistId) {
        this.esChecklistId = esChecklistId;
    }

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public Origin getItemOrigin() {
        return itemOrigin;
    }

    public void setItemOrigin(Origin itemOrigin) {
        this.itemOrigin = itemOrigin;
    }

    public Boolean getInCurrentTemplate() {
        return inCurrentTemplate;
    }

    public void setInCurrentTemplate(Boolean inCurrentTemplate) {
        this.inCurrentTemplate = inCurrentTemplate;
    }

    public Boolean getRemoved() {
        return removed;
    }

    public void setRemoved(Boolean removed) {
        this.removed = removed;
    }

    public Boolean getLocallyEdited() {
        return locallyEdited;
    }

    public void setLocallyEdited(Boolean locallyEdited) {
        this.locallyEdited = locallyEdited;
    }

    public String getPhaseLabel() {
        return phaseLabel;
    }

    public void setPhaseLabel(String phaseLabel) {
        this.phaseLabel = phaseLabel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGuidance() {
        return guidance;
    }

    public void setGuidance(String guidance) {
        this.guidance = guidance;
    }

    public String getDueAnchor() {
        return dueAnchor;
    }

    public void setDueAnchor(String dueAnchor) {
        this.dueAnchor = dueAnchor;
    }

    public Integer getDueOffsetDays() {
        return dueOffsetDays;
    }

    public void setDueOffsetDays(Integer dueOffsetDays) {
        this.dueOffsetDays = dueOffsetDays;
    }

    public LocalDate getDueDateOverride() {
        return dueDateOverride;
    }

    public void setDueDateOverride(LocalDate dueDateOverride) {
        this.dueDateOverride = dueDateOverride;
    }

    public String getCheckCode() {
        return checkCode;
    }

    public void setCheckCode(String checkCode) {
        this.checkCode = checkCode;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Status getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(Status itemStatus) {
        this.itemStatus = itemStatus;
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

    public Long getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(Long assignedUserId) {
        this.assignedUserId = assignedUserId;
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
