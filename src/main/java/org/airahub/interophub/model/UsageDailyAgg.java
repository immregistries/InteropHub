package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_daily_agg")
public class UsageDailyAgg {
    @EmbeddedId
    private UsageDailyAggId id;

    @Column(name = "count_value", nullable = false)
    private Long countValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (countValue == null) {
            countValue = 0L;
        }
    }

    public UsageDailyAggId getId() {
        return id;
    }

    public void setId(UsageDailyAggId id) {
        this.id = id;
    }

    public Long getCountValue() {
        return countValue;
    }

    public void setCountValue(Long countValue) {
        this.countValue = countValue;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
