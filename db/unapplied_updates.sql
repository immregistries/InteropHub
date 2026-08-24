-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Remove legacy free-text stage/path columns on es_topic now that topic
-- creation/edit and all consumers exclusively use the
-- es_topic_stage_definition_id / es_topic_path_definition_id FKs into
-- es_topic_stage_definition / es_topic_path_definition. Defensive backfill
-- first, in case any production row's stage/path text drifted from its FK
-- (none do as of the last local snapshot, but this is free insurance).
UPDATE es_topic t
JOIN es_topic_stage_definition d
  ON d.es_topic_space_id = t.es_topic_space_id
  AND LOWER(d.stage_name) = LOWER(t.stage)
SET t.es_topic_stage_definition_id = d.es_topic_stage_definition_id
WHERE t.es_topic_stage_definition_id IS NULL AND t.stage IS NOT NULL;

UPDATE es_topic t
JOIN es_topic_path_definition d
  ON d.es_topic_space_id = t.es_topic_space_id
  AND LOWER(d.path_name) = LOWER(t.path)
SET t.es_topic_path_definition_id = d.es_topic_path_definition_id
WHERE t.es_topic_path_definition_id IS NULL AND t.path IS NOT NULL;

ALTER TABLE es_topic
  DROP COLUMN stage,
  DROP COLUMN path;

-- Seed es_topic_relationship links for Topic Space #1 (Emerging Standards),
-- proposed and reviewed in docs/topic-relationships-proposal-space1.md.
-- Keyed by topic_code (stable across environments) rather than raw IDs.
-- created_by_user_id = 2 (nbunker@immregistries.org), the same account that
-- created all seeded es_topic rows. WHERE NOT EXISTS guards make this block
-- safe to re-run against a database that already has some of these rows.
INSERT INTO es_topic_relationship
  (from_topic_id, to_topic_id, relationship_type, display_order, created_by_user_id, created_at)
SELECT ft.es_topic_id, tt.es_topic_id, x.rel_type, 0, 2, UTC_TIMESTAMP(6)
FROM (
  VALUES
    -- 4.1 CDS / forecasting core
    ROW('CDS-Contextual-Conditions', 'Immunization-CDS', 'FEEDS_INTO'),
    ROW('CDS-Contextual-Conditions', 'Immunization-Decision-Support', 'FEEDS_INTO'),
    ROW('Clinical-Decision-Supprt', 'Immunization-CDS', 'RELATED_TO'),
    ROW('IIS-to-EHR-Query', 'Clinical-Decision-Supprt', 'OVERLAPS'),
    ROW('CDS-WSDL-Authentication', 'CDS-Response-Improvements', 'RELATED_TO'),
    ROW('CDS-Response-Improvements', 'CDS-Schedule-Source', 'RELATED_TO'),
    ROW('CDS-Response-Improvements', 'CDS-Shared-Decision-Support', 'RELATED_TO'),
    ROW('CDS-Schedule-Source', 'CDS-Shared-Decision-Support', 'RELATED_TO'),
    ROW('NITAG', 'CDS-Schedule-Source', 'RELATED_TO'),
    ROW('Clinical-Quality-Language', 'Immunization-Decision-Support', 'RELATED_TO'),
    ROW('Vaccination-Dates-Estimated', 'Immunization-CDS', 'RELATED_TO'),
    ROW('Immunization-CDS', 'Immunization-Decision-Support', 'SUPERSEDES'),

    -- 4.2 HL7 v2 <-> FHIR modernization pairs
    ROW('QBP-on-FHIR', 'CDC-HL-v2-Guide-Release-1', 'DERIVED_FROM'),
    ROW('VXU-on-FHIR', 'CDC-HL-v2-Guide-Release-1', 'DERIVED_FROM'),
    ROW('VXU-on-FHIR', 'QBP-on-FHIR', 'RELATED_TO'),
    ROW('HL7v2-to-FHIR', 'QBP-on-FHIR', 'RELATED_TO'),
    ROW('HL7v2-to-FHIR', 'VXU-on-FHIR', 'RELATED_TO'),
    ROW('ADT-on-FHIR', 'Patient-Updates', 'RELATED_TO'),
    ROW('ADT-on-FHIR', 'HL7v2-to-FHIR', 'RELATED_TO'),
    ROW('Bulk-FHIR-Query', 'IIS-to-EHR-Query', 'OVERLAPS'),
    ROW('Bulk-FHIR-Query', 'Bulk-Data-Exchange', 'RELATED_TO'),

    -- 4.3 HL7 v2 Guide governance
    ROW('CDC-HL7-v2-Guide-Release-2', 'CDC-HL-v2-Guide-Release-1', 'SUPERSEDES'),
    ROW('ACK-Error-Reporting', 'CDC-HL7-v2-Guide-Release-2', 'FEEDS_INTO'),
    ROW('CDS-Response-Improvements', 'CDC-HL7-v2-Guide-Release-2', 'FEEDS_INTO'),
    ROW('Preferred-Name', 'CDC-HL7-v2-Guide-Release-2', 'FEEDS_INTO'),
    ROW('Acknowledgements', 'ACK-Error-Reporting', 'RELATED_TO'),
    ROW('LOINC-Usage-in-OBX', 'CDC-HL-v2-Guide-Release-1', 'RELATED_TO'),
    ROW('Patient-Updates', 'CDC-HL-v2-Guide-Release-1', 'RELATED_TO'),
    ROW('IIS-to-IIS-Exchnage', 'CDC-HL-v2-Guide-Release-1', 'RELATED_TO'),
    ROW('IIS-to-IIS-Exchnage', 'Multi-Jurisdictional-Query', 'RELATED_TO'),

    -- 4.4 Patient identity & matching
    ROW('Patient-Matching-Integration', 'Patient-Matching', 'DEPENDS_ON'),
    ROW('Immunization-Record-Matching', 'Patient-Matching', 'RELATED_TO'),
    ROW('Newborn-Identity-Handling', 'Patient-Matching', 'RELATED_TO'),
    ROW('Direct-Trust', 'Patient-Matching', 'RELATED_TO'),
    ROW('Fact-of-Dead-Exchange', 'Patient-Matching', 'RELATED_TO'),
    ROW('Data-Merge-Visiblity', 'Patient-Matching', 'RELATED_TO'),
    ROW('Machine Learning', 'Patient-Matching', 'RELATED_TO'),
    ROW('Consumer-Identity-and-Access', 'Patient-Matching', 'DEPENDS_ON'),

    -- 4.5 Consumer access
    ROW('Consumer-Access-to-Records', 'Consumer-Identity-and-Access', 'DEPENDS_ON'),
    ROW('Consumer-Record-Corrections', 'Consumer-Identity-and-Access', 'DEPENDS_ON'),
    ROW('Digital-Vaccine-Cards-SMART-Health-Cards', 'Digital-Vaccine-Cards-SMART-Health-Links', 'OVERLAPS'),
    ROW('Consumer-Access-to-Records', 'Digital-Vaccine-Cards-SMART-Health-Cards', 'RELATED_TO'),
    ROW('Consumer-Access-to-Records', 'Official-Record-Documents', 'RELATED_TO'),
    ROW('Consumer-Access-to-Records', 'International-Patient-Summary', 'RELATED_TO'),
    ROW('SMART-Applications', 'Consumer-Access-to-Records', 'RELATED_TO'),
    ROW('SMART-Applications', 'Clinical-Decision-Supprt', 'RELATED_TO'),
    ROW('Appointment-Scheduling-Integration', 'Vaccine-Availability-Search', 'RELATED_TO'),
    ROW('Digital-Record-Provenance', 'International-Patient-Summary', 'RELATED_TO'),

    -- 4.6 Data quality
    ROW('Data-Quality-Notifications', 'Data-Quality-Reporting', 'FEEDS_INTO'),
    ROW('Data-Merge-Visiblity', 'Data-Provenance-Tracking', 'RELATED_TO'),
    ROW('Data-Provenance-Tracking', 'Digital-Record-Provenance', 'RELATED_TO'),
    ROW('Subpotent-Vaccinations', 'Vaccine-Lot-Validation', 'RELATED_TO'),
    ROW('Subpotent-Vaccinations', 'Data-Quality-Notifications', 'RELATED_TO'),
    ROW('Received-Code-Validation', 'Data-Validation-Services', 'RELATED_TO'),
    ROW('MQE', 'Data-Quality-Reporting', 'RELATED_TO'),
    ROW('MQE', 'ACK-Error-Reporting', 'RELATED_TO'),
    ROW('Synthetic-Data', 'MQE', 'RELATED_TO'),

    -- 4.7 Vaccine inventory & supply chain
    ROW('Vaccine-Inventory-Management', 'Inventory-Reconcilliation', 'RELATED_TO'),
    ROW('Vaccine-Inventory-Management', 'Inventory-Syncrhonization', 'RELATED_TO'),
    ROW('Inventory-Reconcilliation', 'Inventory-Syncrhonization', 'OVERLAPS'),
    ROW('Vaccine-Barcode-Scanning', 'Vaccine-Lot-Validation', 'FEEDS_INTO'),
    ROW('Vaccine-Ordering-Integration', 'VTrcks-IIS-API', 'RELATED_TO'),
    ROW('VTrcks-IIS-API', 'Vaccine-Inventory-Management', 'RELATED_TO'),
    ROW('Vaccine-Storage-and-Temperature', 'Subpotent-Vaccinations', 'RELATED_TO'),
    ROW('NDC-12digit', 'Received-Code-Validation', 'BLOCKER_FOR'),
    ROW('NDC-12digit', 'Vaccine-Barcode-Scanning', 'BLOCKER_FOR'),
    ROW('NDC-12digit', 'Product-Identifiers', 'RELATED_TO'),

    -- 4.8 Terminology & product identification
    ROW('Vaccine-Coding', 'NUVA', 'RELATED_TO'),
    ROW('NUVA', 'IDMP', 'RELATED_TO'),
    ROW('Product-Identifiers', 'IDMP', 'RELATED_TO'),
    ROW('Product-Identifiers', 'NUVA', 'RELATED_TO'),
    ROW('Building-Bridges', 'Vaccine-Coding', 'RELATED_TO'),
    ROW('Building-Bridges', 'NUVA', 'RELATED_TO'),
    ROW('Received-Code-Validation', 'Vaccine-Coding', 'RELATED_TO'),
    ROW('IIS-Terminology-Services', 'Received-Code-Validation', 'RELATED_TO'),

    -- 4.9 Security & interfaces
    ROW('Certificate-Management', 'Interface-Security', 'DEPENDS_ON'),
    ROW('CDS-WSDL-Authentication', 'Interface-Security', 'DEPENDS_ON'),

    -- 4.10 Reporting, exchange volume & synchronization
    ROW('Data-Export-Format', 'Flat-File-Data-Export', 'OVERLAPS'),
    ROW('Flat-File-Data-Import', 'Flat-File-Data-Export', 'RELATED_TO'),
    ROW('Bulk-Data-Submission', 'Flat-File-Data-Import', 'RELATED_TO'),
    ROW('Bulk-Data-Submission', 'Bulk-Data-Exchange', 'RELATED_TO'),
    ROW('Reminder-Recall-Services', 'Bulk-Data-Exchange', 'RELATED_TO'),
    ROW('Record-Synchronization', 'Event-Based-Exchange', 'RELATED_TO'),
    ROW('Aggregate-Data-Exchange', 'Population-Analytics', 'RELATED_TO'),
    ROW('Population-Analytics', 'Bulk-Data-Exchange', 'RELATED_TO'),
    ROW('IIS-Metrics-Access', 'Data-Quality-Reporting', 'RELATED_TO'),
    ROW('IIS-Metrics-Access', 'Population-Analytics', 'RELATED_TO'),

    -- 4.11 National emergency / external-driver reporting
    ROW('Emergency-Vaccination-Reporting', 'COVID-Mpox-Reporting', 'DERIVED_FROM'),
    ROW('Adverse-Event-Reporting', 'COVID-Mpox-Reporting', 'RELATED_TO'),
    ROW('Priority-Classification', 'COVID-Mpox-Reporting', 'RELATED_TO'),
    ROW('Vaccine-Exemptions-and-Deferrals', 'COVID-Mpox-Reporting', 'RELATED_TO'),
    ROW('TEFCA-National-Exchange', 'ADT-on-FHIR', 'RELATED_TO'),
    ROW('TEFCA-National-Exchange', 'Bulk-FHIR-Query', 'RELATED_TO'),
    ROW('TEFCA-National-Exchange', 'Provider-Enrollment-Integration', 'RELATED_TO'),
    ROW('TEFCA-National-Exchange', 'IIS-to-EHR-Query', 'RELATED_TO'),
    ROW('Provider-Identifiers', 'Provider-Enrollment-Integration', 'RELATED_TO'),

    -- 4.12 Auxiliary public-health data in IIS
    ROW('Lead-Test-Query', 'Lead-Reporting', 'DEPENDS_ON'),
    ROW('TB-Screening-Data', 'Lead-Reporting', 'RELATED_TO'),
    ROW('TB-Screening-Data', 'Lead-Test-Query', 'RELATED_TO'),
    ROW('Social-Determinants-of-Health', 'Homeless-Data-Integration', 'RELATED_TO'),
    ROW('Social-Determinants-of-Health', 'Disability-Data-Collection', 'RELATED_TO'),
    ROW('Race-Ethnicity', 'Sexual-Orientation-and-Gender-Identity', 'RELATED_TO'),
    ROW('Newborn-Admission-Notification-Information', 'Newborn-Identity-Handling', 'RELATED_TO'),

    -- 4.13 School data exchange
    ROW('School-Roster-Reporting', 'School-Data-Exchange', 'OPERATIONALIZES'),

    -- 4.14 Community, coordination & governance
    ROW('FHIR-Dev-Days', 'Immunization-Focus-Group', 'RELATED_TO'),
    ROW('Immunization-Focus-Group', 'FHIR-Open-Tickets', 'RELATED_TO'),
    ROW('Immunization-Focus-Group', 'IIP-Collaborative', 'RELATED_TO')
) AS x (from_code, to_code, rel_type)
JOIN es_topic ft ON ft.topic_code = x.from_code
JOIN es_topic tt ON tt.topic_code = x.to_code
WHERE NOT EXISTS (
  SELECT 1 FROM es_topic_relationship r
  WHERE r.from_topic_id = ft.es_topic_id
    AND r.to_topic_id = tt.es_topic_id
    AND r.relationship_type = x.rel_type
);
