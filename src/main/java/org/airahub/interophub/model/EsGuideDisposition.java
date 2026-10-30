package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "es_guide_disposition")
public class EsGuideDisposition {

    @Id
    @Column(name = "disposition_code", length = 32)
    private String dispositionCode;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public String getDispositionCode() {
        return dispositionCode;
    }

    public void setDispositionCode(String dispositionCode) {
        this.dispositionCode = dispositionCode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
