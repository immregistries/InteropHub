package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_campaign_topic")
public class EsCampaignTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_campaign_topic_id")
    private Long esCampaignTopicId;

    @Column(name = "es_campaign_id", nullable = false)
    private Long esCampaignId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "topic_set_no")
    private Integer topicSetNo;

    @Column(name = "table_no")
    private Integer tableNo;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

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
    }

    public Long getEsCampaignTopicId() {
        return esCampaignTopicId;
    }

    public void setEsCampaignTopicId(Long esCampaignTopicId) {
        this.esCampaignTopicId = esCampaignTopicId;
    }

    public Long getEsCampaignId() {
        return esCampaignId;
    }

    public void setEsCampaignId(Long esCampaignId) {
        this.esCampaignId = esCampaignId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public Integer getTopicSetNo() {
        return topicSetNo;
    }

    public void setTopicSetNo(Integer topicSetNo) {
        this.topicSetNo = topicSetNo;
    }

    public Integer getTableNo() {
        return tableNo;
    }

    public void setTableNo(Integer tableNo) {
        this.tableNo = tableNo;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
