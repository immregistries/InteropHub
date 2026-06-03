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
@Table(name = "es_survey_answer")
public class EsSurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_survey_answer_id")
    private Long esSurveyAnswerId;

    @Column(name = "es_survey_response_id", nullable = false)
    private Long esSurveyResponseId;

    @Column(name = "es_survey_question_id", nullable = false)
    private Long esSurveyQuestionId;

    @Column(name = "numeric_value")
    private Integer numericValue;

    @Column(name = "text_value", columnDefinition = "TEXT")
    private String textValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getEsSurveyAnswerId() {
        return esSurveyAnswerId;
    }

    public void setEsSurveyAnswerId(Long esSurveyAnswerId) {
        this.esSurveyAnswerId = esSurveyAnswerId;
    }

    public Long getEsSurveyResponseId() {
        return esSurveyResponseId;
    }

    public void setEsSurveyResponseId(Long esSurveyResponseId) {
        this.esSurveyResponseId = esSurveyResponseId;
    }

    public Long getEsSurveyQuestionId() {
        return esSurveyQuestionId;
    }

    public void setEsSurveyQuestionId(Long esSurveyQuestionId) {
        this.esSurveyQuestionId = esSurveyQuestionId;
    }

    public Integer getNumericValue() {
        return numericValue;
    }

    public void setNumericValue(Integer numericValue) {
        this.numericValue = numericValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
