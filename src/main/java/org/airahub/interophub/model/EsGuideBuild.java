package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One build record of a guide: a hosted upload (UPLOAD) or a manifest-only report
 * from an externally hosted guide (MANIFEST). The columns copy manifest fields
 * used for listing; {@code manifestJson} keeps the manifest whole.
 */
@Entity
@Table(name = "es_guide_build")
public class EsGuideBuild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_build_id")
    private Long guideBuildId;

    @Column(name = "guide_document_id", nullable = false)
    private Long guideDocumentId;

    @Column(name = "guide_hosted_version_id")
    private Long guideHostedVersionId;

    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "label", length = 64)
    private String label;

    @Column(name = "manifest_schema")
    private Integer manifestSchema;

    @Column(name = "process_version", length = 32)
    private String processVersion;

    @Column(name = "document_version", length = 64)
    private String documentVersion;

    @Column(name = "document_status", length = 40)
    private String documentStatus;

    @Column(name = "built_at")
    private LocalDateTime builtAt;

    @Column(name = "source_commit", length = 64)
    private String sourceCommit;

    @Column(name = "warning_count")
    private Integer warningCount;

    @Column(name = "manifest_json", columnDefinition = "JSON")
    private String manifestJson;

    @Column(name = "manifest_error", length = 300)
    private String manifestError;

    @Column(name = "received_by_user_id", nullable = false)
    private Long receivedByUserId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    public Long getGuideBuildId() {
        return guideBuildId;
    }

    public void setGuideBuildId(Long guideBuildId) {
        this.guideBuildId = guideBuildId;
    }

    public Long getGuideDocumentId() {
        return guideDocumentId;
    }

    public void setGuideDocumentId(Long guideDocumentId) {
        this.guideDocumentId = guideDocumentId;
    }

    public Long getGuideHostedVersionId() {
        return guideHostedVersionId;
    }

    public void setGuideHostedVersionId(Long guideHostedVersionId) {
        this.guideHostedVersionId = guideHostedVersionId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getManifestSchema() {
        return manifestSchema;
    }

    public void setManifestSchema(Integer manifestSchema) {
        this.manifestSchema = manifestSchema;
    }

    public String getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(String processVersion) {
        this.processVersion = processVersion;
    }

    public String getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(String documentVersion) {
        this.documentVersion = documentVersion;
    }

    public String getDocumentStatus() {
        return documentStatus;
    }

    public void setDocumentStatus(String documentStatus) {
        this.documentStatus = documentStatus;
    }

    public LocalDateTime getBuiltAt() {
        return builtAt;
    }

    public void setBuiltAt(LocalDateTime builtAt) {
        this.builtAt = builtAt;
    }

    public String getSourceCommit() {
        return sourceCommit;
    }

    public void setSourceCommit(String sourceCommit) {
        this.sourceCommit = sourceCommit;
    }

    public Integer getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(Integer warningCount) {
        this.warningCount = warningCount;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public String getManifestError() {
        return manifestError;
    }

    public void setManifestError(String manifestError) {
        this.manifestError = manifestError;
    }

    public Long getReceivedByUserId() {
        return receivedByUserId;
    }

    public void setReceivedByUserId(Long receivedByUserId) {
        this.receivedByUserId = receivedByUserId;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
