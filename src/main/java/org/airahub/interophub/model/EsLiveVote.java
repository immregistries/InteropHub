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
@Table(name = "es_live_vote")
public class EsLiveVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_live_vote_id")
    private Long esLiveVoteId;

    @Column(name = "es_recorded_outcome_id", nullable = false, unique = true)
    private Long esRecordedOutcomeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LiveVoteStatus status;

    @Column(name = "motion_text", nullable = false, columnDefinition = "TEXT")
    private String motionText;

    @Column(name = "moved_by_user_id")
    private Long movedByUserId;

    @Column(name = "moved_by_name", length = 160)
    private String movedByName;

    @Column(name = "seconded_by_user_id")
    private Long secondedByUserId;

    @Column(name = "seconded_by_name", length = 160)
    private String secondedByName;

    @Column(name = "presiding_chair_user_id", nullable = false)
    private Long presidingChairUserId;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "opened_by_user_id")
    private Long openedByUserId;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    @Column(name = "participant_count_observation_id")
    private Long participantCountObservationId;

    @Column(name = "call_participant_count")
    private Integer callParticipantCount;

    @Column(name = "expected_voter_count")
    private Integer expectedVoterCount;

    @Column(name = "electronic_for_count", nullable = false)
    private int electronicForCount;

    @Column(name = "electronic_against_count", nullable = false)
    private int electronicAgainstCount;

    @Column(name = "electronic_abstain_count", nullable = false)
    private int electronicAbstainCount;

    @Column(name = "manual_for_count", nullable = false)
    private int manualForCount;

    @Column(name = "manual_against_count", nullable = false)
    private int manualAgainstCount;

    @Column(name = "manual_abstain_count", nullable = false)
    private int manualAbstainCount;

    @Column(name = "final_for_count")
    private Integer finalForCount;

    @Column(name = "final_against_count")
    private Integer finalAgainstCount;

    @Column(name = "final_abstain_count")
    private Integer finalAbstainCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 16)
    private LiveVoteResult result;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = LiveVoteStatus.PREPARED;
        }
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsLiveVoteId() {
        return esLiveVoteId;
    }

    public void setEsLiveVoteId(Long esLiveVoteId) {
        this.esLiveVoteId = esLiveVoteId;
    }

    public Long getEsRecordedOutcomeId() {
        return esRecordedOutcomeId;
    }

    public void setEsRecordedOutcomeId(Long esRecordedOutcomeId) {
        this.esRecordedOutcomeId = esRecordedOutcomeId;
    }

    public LiveVoteStatus getStatus() {
        return status;
    }

    public void setStatus(LiveVoteStatus status) {
        this.status = status;
    }

    public String getMotionText() {
        return motionText;
    }

    public void setMotionText(String motionText) {
        this.motionText = motionText;
    }

    public Long getMovedByUserId() {
        return movedByUserId;
    }

    public void setMovedByUserId(Long movedByUserId) {
        this.movedByUserId = movedByUserId;
    }

    public String getMovedByName() {
        return movedByName;
    }

    public void setMovedByName(String movedByName) {
        this.movedByName = movedByName;
    }

    public Long getSecondedByUserId() {
        return secondedByUserId;
    }

    public void setSecondedByUserId(Long secondedByUserId) {
        this.secondedByUserId = secondedByUserId;
    }

    public String getSecondedByName() {
        return secondedByName;
    }

    public void setSecondedByName(String secondedByName) {
        this.secondedByName = secondedByName;
    }

    public Long getPresidingChairUserId() {
        return presidingChairUserId;
    }

    public void setPresidingChairUserId(Long presidingChairUserId) {
        this.presidingChairUserId = presidingChairUserId;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public Long getOpenedByUserId() {
        return openedByUserId;
    }

    public void setOpenedByUserId(Long openedByUserId) {
        this.openedByUserId = openedByUserId;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Long getClosedByUserId() {
        return closedByUserId;
    }

    public void setClosedByUserId(Long closedByUserId) {
        this.closedByUserId = closedByUserId;
    }

    public Long getParticipantCountObservationId() {
        return participantCountObservationId;
    }

    public void setParticipantCountObservationId(Long participantCountObservationId) {
        this.participantCountObservationId = participantCountObservationId;
    }

    public Integer getCallParticipantCount() {
        return callParticipantCount;
    }

    public void setCallParticipantCount(Integer callParticipantCount) {
        this.callParticipantCount = callParticipantCount;
    }

    public Integer getExpectedVoterCount() {
        return expectedVoterCount;
    }

    public void setExpectedVoterCount(Integer expectedVoterCount) {
        this.expectedVoterCount = expectedVoterCount;
    }

    public int getElectronicForCount() {
        return electronicForCount;
    }

    public void setElectronicForCount(int electronicForCount) {
        this.electronicForCount = electronicForCount;
    }

    public int getElectronicAgainstCount() {
        return electronicAgainstCount;
    }

    public void setElectronicAgainstCount(int electronicAgainstCount) {
        this.electronicAgainstCount = electronicAgainstCount;
    }

    public int getElectronicAbstainCount() {
        return electronicAbstainCount;
    }

    public void setElectronicAbstainCount(int electronicAbstainCount) {
        this.electronicAbstainCount = electronicAbstainCount;
    }

    public int getManualForCount() {
        return manualForCount;
    }

    public void setManualForCount(int manualForCount) {
        this.manualForCount = manualForCount;
    }

    public int getManualAgainstCount() {
        return manualAgainstCount;
    }

    public void setManualAgainstCount(int manualAgainstCount) {
        this.manualAgainstCount = manualAgainstCount;
    }

    public int getManualAbstainCount() {
        return manualAbstainCount;
    }

    public void setManualAbstainCount(int manualAbstainCount) {
        this.manualAbstainCount = manualAbstainCount;
    }

    public Integer getFinalForCount() {
        return finalForCount;
    }

    public void setFinalForCount(Integer finalForCount) {
        this.finalForCount = finalForCount;
    }

    public Integer getFinalAgainstCount() {
        return finalAgainstCount;
    }

    public void setFinalAgainstCount(Integer finalAgainstCount) {
        this.finalAgainstCount = finalAgainstCount;
    }

    public Integer getFinalAbstainCount() {
        return finalAbstainCount;
    }

    public void setFinalAbstainCount(Integer finalAbstainCount) {
        this.finalAbstainCount = finalAbstainCount;
    }

    public LiveVoteResult getResult() {
        return result;
    }

    public void setResult(LiveVoteResult result) {
        this.result = result;
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

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(Long updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }
}