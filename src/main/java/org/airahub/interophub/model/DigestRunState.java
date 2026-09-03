package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks the last successful run of a scheduled digest job (one row per
 * digest key), so each run knows the "since" boundary for its content
 * sources and can't double-fire within the same calendar day.
 */
@Entity
@Table(name = "digest_run_state")
public class DigestRunState {

    @Id
    @Column(name = "digest_key", length = 40)
    private String digestKey;

    @Column(name = "last_run_at", nullable = false)
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_date", nullable = false)
    private LocalDate lastRunDate;

    public String getDigestKey() {
        return digestKey;
    }

    public void setDigestKey(String digestKey) {
        this.digestKey = digestKey;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public LocalDate getLastRunDate() {
        return lastRunDate;
    }

    public void setLastRunDate(LocalDate lastRunDate) {
        this.lastRunDate = lastRunDate;
    }
}
