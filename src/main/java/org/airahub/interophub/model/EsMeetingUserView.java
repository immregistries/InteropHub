package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_meeting_user_view")
public class EsMeetingUserView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_user_view_id")
    private Long esMeetingUserViewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "es_meeting_id", nullable = false)
    private Long esMeetingId;

    @Column(name = "first_viewed_at", nullable = false)
    private LocalDateTime firstViewedAt;

    @Column(name = "last_viewed_at", nullable = false)
    private LocalDateTime lastViewedAt;

    @Column(name = "visit_count", nullable = false)
    private Long visitCount;

    @Column(name = "last_counted_at", nullable = false)
    private LocalDateTime lastCountedAt;

    public Long getEsMeetingUserViewId() {
        return esMeetingUserViewId;
    }

    public void setEsMeetingUserViewId(Long esMeetingUserViewId) {
        this.esMeetingUserViewId = esMeetingUserViewId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public LocalDateTime getFirstViewedAt() {
        return firstViewedAt;
    }

    public void setFirstViewedAt(LocalDateTime firstViewedAt) {
        this.firstViewedAt = firstViewedAt;
    }

    public LocalDateTime getLastViewedAt() {
        return lastViewedAt;
    }

    public void setLastViewedAt(LocalDateTime lastViewedAt) {
        this.lastViewedAt = lastViewedAt;
    }

    public Long getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Long visitCount) {
        this.visitCount = visitCount;
    }

    public LocalDateTime getLastCountedAt() {
        return lastCountedAt;
    }

    public void setLastCountedAt(LocalDateTime lastCountedAt) {
        this.lastCountedAt = lastCountedAt;
    }
}
