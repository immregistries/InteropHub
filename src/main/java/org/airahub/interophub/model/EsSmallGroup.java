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
@Table(name = "es_small_group")
public class EsSmallGroup {

    public enum Status {
        PLANNING("Planning"),
        RECRUITING("Recruiting"),
        ACTIVE("Active"),
        HANDOFF("Handoff"),
        CLOSED("Closed"),
        CANCELLED("Cancelled");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            return CodedLabel.labelOf(Status.class, code, value -> value.label);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_small_group_id")
    private Long esSmallGroupId;

    @Column(name = "es_initiative_id", nullable = false)
    private Long esInitiativeId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "es_topic_meeting_id")
    private Long esTopicMeetingId;

    @Column(name = "small_group_name", nullable = false, length = 200)
    private String smallGroupName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status;

    @Column(name = "cycle_start_date")
    private LocalDate cycleStartDate;

    @Column(name = "working_day_date")
    private LocalDate workingDayDate;

    @Column(name = "closeout_date")
    private LocalDate closeoutDate;

    @Column(name = "break_note", columnDefinition = "TEXT")
    private String breakNote;

    @Column(name = "primary_product_areas", length = 80)
    private String primaryProductAreas;

    @Column(name = "charter_url", length = 2048)
    private String charterUrl;

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
        if (status == null) {
            status = Status.PLANNING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsSmallGroupId() {
        return esSmallGroupId;
    }

    public void setEsSmallGroupId(Long esSmallGroupId) {
        this.esSmallGroupId = esSmallGroupId;
    }

    public Long getEsInitiativeId() {
        return esInitiativeId;
    }

    public void setEsInitiativeId(Long esInitiativeId) {
        this.esInitiativeId = esInitiativeId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public Long getEsTopicMeetingId() {
        return esTopicMeetingId;
    }

    public void setEsTopicMeetingId(Long esTopicMeetingId) {
        this.esTopicMeetingId = esTopicMeetingId;
    }

    public String getSmallGroupName() {
        return smallGroupName;
    }

    public void setSmallGroupName(String smallGroupName) {
        this.smallGroupName = smallGroupName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDate getCycleStartDate() {
        return cycleStartDate;
    }

    public void setCycleStartDate(LocalDate cycleStartDate) {
        this.cycleStartDate = cycleStartDate;
    }

    public LocalDate getWorkingDayDate() {
        return workingDayDate;
    }

    public void setWorkingDayDate(LocalDate workingDayDate) {
        this.workingDayDate = workingDayDate;
    }

    public LocalDate getCloseoutDate() {
        return closeoutDate;
    }

    public void setCloseoutDate(LocalDate closeoutDate) {
        this.closeoutDate = closeoutDate;
    }

    public String getBreakNote() {
        return breakNote;
    }

    public void setBreakNote(String breakNote) {
        this.breakNote = breakNote;
    }

    public String getPrimaryProductAreas() {
        return primaryProductAreas;
    }

    public void setPrimaryProductAreas(String primaryProductAreas) {
        this.primaryProductAreas = primaryProductAreas;
    }

    public String getCharterUrl() {
        return charterUrl;
    }

    public void setCharterUrl(String charterUrl) {
        this.charterUrl = charterUrl;
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

    public boolean acceptsTemplateUpgrade() {
        return status != Status.CLOSED && status != Status.CANCELLED;
    }
}
