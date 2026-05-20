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
@Table(name = "es_topic_relationship")
public class EsTopicRelationship {

    /**
     * Typed directional relationship between two topics.
     * label = human-readable verb for the forward direction (A → B)
     * inverseLabel = human-readable verb for the reverse direction (B ← A)
     * Symmetric types (RELATED_TO, OVERLAPS, DUPLICATE_OF) use the same label both
     * ways.
     */
    public enum RelationshipType {
        RELATED_TO("related to", "related to"),
        OVERLAPS("overlaps", "overlaps"),
        DEPENDS_ON("depends on", "depended on by"),
        FEEDS_INTO("feeds into", "fed by"),
        DERIVED_FROM("derived from", "source of"),
        SUPERSEDES("supersedes", "superseded by"),
        BLOCKER_FOR("blocker for", "blocked by"),
        OPERATIONALIZES("operationalizes", "operationalized by"),
        DUPLICATE_OF("duplicate of", "duplicate of");

        private final String label;
        private final String inverseLabel;

        RelationshipType(String label, String inverseLabel) {
            this.label = label;
            this.inverseLabel = inverseLabel;
        }

        public String getLabel() {
            return label;
        }

        public String getInverseLabel() {
            return inverseLabel;
        }

        /** Parse from enum name (case-insensitive). Falls back to RELATED_TO. */
        public static RelationshipType fromString(String value) {
            if (value == null) {
                return RELATED_TO;
            }
            for (RelationshipType t : values()) {
                if (t.name().equalsIgnoreCase(value.trim())) {
                    return t;
                }
            }
            return RELATED_TO;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_relationship_id")
    private Long esTopicRelationshipId;

    @Column(name = "from_topic_id", nullable = false)
    private Long fromTopicId;

    @Column(name = "to_topic_id", nullable = false)
    private Long toTopicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 40)
    private RelationshipType relationshipType;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (relationshipType == null) {
            relationshipType = RelationshipType.RELATED_TO;
        }
    }

    public Long getEsTopicRelationshipId() {
        return esTopicRelationshipId;
    }

    public void setEsTopicRelationshipId(Long esTopicRelationshipId) {
        this.esTopicRelationshipId = esTopicRelationshipId;
    }

    public Long getFromTopicId() {
        return fromTopicId;
    }

    public void setFromTopicId(Long fromTopicId) {
        this.fromTopicId = fromTopicId;
    }

    public Long getToTopicId() {
        return toTopicId;
    }

    public void setToTopicId(Long toTopicId) {
        this.toTopicId = toTopicId;
    }

    public RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(RelationshipType relationshipType) {
        this.relationshipType = relationshipType;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
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
}
