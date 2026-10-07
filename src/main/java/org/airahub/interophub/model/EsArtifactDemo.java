package org.airahub.interophub.model;

import jakarta.persistence.*;

/** Temporary feature associations; shared stored files survive retirement of this demo. */
@Entity
@Table(name = "es_artifact_demo")
public class EsArtifactDemo {
    public static final String SLOT_WELCOME_DEMO = "WELCOME_DEMO";
    public static final String SLOT_LEGACY_BLOB = "WELCOME_BLOB_DEMO";
    public static final String SLOT_DOCUMENT_DEMO = "DOCUMENT_DEMO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_artifact_demo_id")
    private Long esArtifactDemoId;
    @Column(name = "slot_key", nullable = false, unique = true, length = 40)
    private String slotKey;
    @ManyToOne(optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false)
    private StoredFile storedFile;

    public Long getEsArtifactDemoId() { return esArtifactDemoId; }
    public void setEsArtifactDemoId(Long value) { esArtifactDemoId = value; }
    public String getSlotKey() { return slotKey; }
    public void setSlotKey(String value) { slotKey = value; }
    public StoredFile getStoredFile() { return storedFile; }
    public void setStoredFile(StoredFile value) { storedFile = value; }
}
