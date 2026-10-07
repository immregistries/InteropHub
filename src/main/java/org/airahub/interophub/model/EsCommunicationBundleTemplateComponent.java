package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "es_communication_bundle_template_component")
public class EsCommunicationBundleTemplateComponent {

    public enum Kind {
        TEXT,
        STRUCTURED_LIST,
        RESOURCE,
        RESOURCE_COLLECTION
    }

    public enum Cardinality {
        SINGLE,
        REPEATING
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_id")
    private Long componentId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "semantic_key", nullable = false, length = 80)
    private String semanticKey;

    @Column(name = "display_name", nullable = false, length = 140)
    private String displayName;

    @Column(name = "authoring_prompt", columnDefinition = "TEXT")
    private String authoringPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_kind", nullable = false, length = 24)
    private Kind kind;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(name = "cardinality", nullable = false, length = 16)
    private Cardinality cardinality;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long value) { componentId = value; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long value) { templateId = value; }
    public String getSemanticKey() { return semanticKey; }
    public void setSemanticKey(String value) { semanticKey = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getAuthoringPrompt() { return authoringPrompt; }
    public void setAuthoringPrompt(String value) { authoringPrompt = value; }
    public Kind getKind() { return kind; }
    public void setKind(Kind value) { kind = value; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean value) { required = value; }
    public Cardinality getCardinality() { return cardinality; }
    public void setCardinality(Cardinality value) { cardinality = value; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int value) { displayOrder = value; }
}
