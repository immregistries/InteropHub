package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "es_checklist_template_item")
public class EsChecklistTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_checklist_template_item_id")
    private Long esChecklistTemplateItemId;

    @Column(name = "es_checklist_template_id", nullable = false)
    private Long esChecklistTemplateId;

    @Column(name = "item_key", nullable = false, length = 80)
    private String itemKey;

    @Column(name = "phase_label", nullable = false, length = 200)
    private String phaseLabel;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "guidance", columnDefinition = "TEXT")
    private String guidance;

    @Column(name = "due_anchor", length = 40)
    private String dueAnchor;

    @Column(name = "due_offset_days")
    private Integer dueOffsetDays;

    @Column(name = "check_code", length = 80)
    private String checkCode;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public Long getEsChecklistTemplateItemId() {
        return esChecklistTemplateItemId;
    }

    public void setEsChecklistTemplateItemId(Long esChecklistTemplateItemId) {
        this.esChecklistTemplateItemId = esChecklistTemplateItemId;
    }

    public Long getEsChecklistTemplateId() {
        return esChecklistTemplateId;
    }

    public void setEsChecklistTemplateId(Long esChecklistTemplateId) {
        this.esChecklistTemplateId = esChecklistTemplateId;
    }

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public String getPhaseLabel() {
        return phaseLabel;
    }

    public void setPhaseLabel(String phaseLabel) {
        this.phaseLabel = phaseLabel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGuidance() {
        return guidance;
    }

    public void setGuidance(String guidance) {
        this.guidance = guidance;
    }

    public String getDueAnchor() {
        return dueAnchor;
    }

    public void setDueAnchor(String dueAnchor) {
        this.dueAnchor = dueAnchor;
    }

    public Integer getDueOffsetDays() {
        return dueOffsetDays;
    }

    public void setDueOffsetDays(Integer dueOffsetDays) {
        this.dueOffsetDays = dueOffsetDays;
    }

    public String getCheckCode() {
        return checkCode;
    }

    public void setCheckCode(String checkCode) {
        this.checkCode = checkCode;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
