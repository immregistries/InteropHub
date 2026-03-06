package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class UsageDailyAggId implements Serializable {
    public enum UsageMetric {
        API_CALL,
        API_ERROR_4XX,
        API_ERROR_5XX
    }

    @Column(name = "usage_day", nullable = false)
    private LocalDate usageDay;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "token_id")
    private Long tokenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 16)
    private UsageMetric metric;

    public LocalDate getUsageDay() {
        return usageDay;
    }

    public void setUsageDay(LocalDate usageDay) {
        this.usageDay = usageDay;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getTokenId() {
        return tokenId;
    }

    public void setTokenId(Long tokenId) {
        this.tokenId = tokenId;
    }

    public UsageMetric getMetric() {
        return metric;
    }

    public void setMetric(UsageMetric metric) {
        this.metric = metric;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsageDailyAggId that)) {
            return false;
        }
        return Objects.equals(usageDay, that.usageDay)
                && Objects.equals(appId, that.appId)
                && Objects.equals(tokenId, that.tokenId)
                && metric == that.metric;
    }

    @Override
    public int hashCode() {
        return Objects.hash(usageDay, appId, tokenId, metric);
    }
}
