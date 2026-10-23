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
@Table(name = "es_initiative")
public class EsInitiative {

    public enum Status {
        PLANNING("Planning"),
        ACTIVE("Active"),
        CLOSED("Closed"),
        ARCHIVED("Archived");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            return label(Status.class, code);
        }
    }

    public enum Visibility {
        PRIVATE("Private"),
        LISTED("Listed");

        private final String label;

        Visibility(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            return label(Visibility.class, code);
        }
    }

    public enum JoinPolicy {
        INVITE_ONLY("Invite only"),
        APPLY("Apply"),
        OPEN("Open");

        private final String label;

        JoinPolicy(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            return label(JoinPolicy.class, code);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_initiative_id")
    private Long esInitiativeId;

    @Column(name = "initiative_code", nullable = false, unique = true, length = 80)
    private String initiativeCode;

    @Column(name = "initiative_name", nullable = false, length = 200)
    private String initiativeName;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "primary_es_topic_id", nullable = false)
    private Long primaryEsTopicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false, length = 32)
    private JoinPolicy joinPolicy;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsInitiativeId() {
        return esInitiativeId;
    }

    public void setEsInitiativeId(Long esInitiativeId) {
        this.esInitiativeId = esInitiativeId;
    }

    public String getInitiativeCode() {
        return initiativeCode;
    }

    public void setInitiativeCode(String initiativeCode) {
        this.initiativeCode = initiativeCode;
    }

    public String getInitiativeName() {
        return initiativeName;
    }

    public void setInitiativeName(String initiativeName) {
        this.initiativeName = initiativeName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getPrimaryEsTopicId() {
        return primaryEsTopicId;
    }

    public void setPrimaryEsTopicId(Long primaryEsTopicId) {
        this.primaryEsTopicId = primaryEsTopicId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public JoinPolicy getJoinPolicy() {
        return joinPolicy;
    }

    public void setJoinPolicy(JoinPolicy joinPolicy) {
        this.joinPolicy = joinPolicy;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
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

    private static <E extends Enum<E>> String label(Class<E> type, String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            E value = Enum.valueOf(type, code.trim());
            if (value instanceof Status status) {
                return status.label;
            }
            if (value instanceof Visibility visibility) {
                return visibility.label;
            }
            if (value instanceof JoinPolicy policy) {
                return policy.label;
            }
            return value.name();
        } catch (IllegalArgumentException ex) {
            return code.trim();
        }
    }
}
