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
@Table(name = "es_communication_bundle_purpose")
public class EsCommunicationBundlePurpose {

    public enum Mode {
        LIVING,
        SNAPSHOT
    }

    public enum InstancePolicy {
        SINGLE,
        MULTIPLE
    }

    public enum Audience {
        PUBLIC,
        PARTICIPANTS,
        STEWARDS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purpose_id")
    private Long purposeId;

    @Column(name = "purpose_key", nullable = false, unique = true, length = 80)
    private String purposeKey;

    @Column(name = "display_name", nullable = false, length = 140)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "instance_policy", nullable = false, length = 16)
    private InstancePolicy instancePolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_audience", nullable = false, length = 16)
    private Audience defaultAudience;

    @Column(name = "active_template_id")
    private Long activeTemplateId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public Long getPurposeId() { return purposeId; }
    public void setPurposeId(Long value) { purposeId = value; }
    public String getPurposeKey() { return purposeKey; }
    public void setPurposeKey(String value) { purposeKey = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public Mode getMode() { return mode; }
    public void setMode(Mode value) { mode = value; }
    public InstancePolicy getInstancePolicy() { return instancePolicy; }
    public void setInstancePolicy(InstancePolicy value) { instancePolicy = value; }
    public Audience getDefaultAudience() { return defaultAudience; }
    public void setDefaultAudience(Audience value) { defaultAudience = value; }
    public Long getActiveTemplateId() { return activeTemplateId; }
    public void setActiveTemplateId(Long value) { activeTemplateId = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
