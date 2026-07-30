-- Populate default topic summaries and emojis for the 124 existing topics.
-- Prerequisite: es_topic.topic_summary and es_topic.topic_emoji have already been added.
-- Generated from es_topics.csv; review and revise individual values through normal topic administration as needed.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

START TRANSACTION;

-- 1: Immunization CDS (ImmDS + HALO)
UPDATE es_topic
SET topic_summary = 'Defines FHIR-based interfaces for requesting and returning immunization clinical decision support (CDS) evaluations and recommendations, including contextual conditions.',
    topic_emoji = '🧠'
WHERE es_topic_id = 1;

-- 2: Consumer Access to Records
UPDATE es_topic
SET topic_summary = 'Enables individuals to securely retrieve their immunization records from an immunization information system (IIS) through portals, APIs, or digital credentials.',
    topic_emoji = '📱'
WHERE es_topic_id = 2;

-- 3: Data Quality Notifications
UPDATE es_topic
SET topic_summary = 'Provides structured notifications to submitters when immunization data quality problems are detected during processing or later review.',
    topic_emoji = '⚠️'
WHERE es_topic_id = 3;

-- 4: Vaccine Lot Validation
UPDATE es_topic
SET topic_summary = 'Validates vaccine lot numbers against reference data to improve the accuracy and safety of reported immunizations.',
    topic_emoji = '🔍'
WHERE es_topic_id = 4;

-- 5: Consumer Identity & Access
UPDATE es_topic
SET topic_summary = 'Addresses identity verification, authentication, and secure access for consumers retrieving their immunization records.',
    topic_emoji = '🔐'
WHERE es_topic_id = 5;

-- 6: Flat File Data Import
UPDATE es_topic
SET topic_summary = 'Supports importing bulk or legacy immunization data into an immunization information system (IIS) through structured flat files.',
    topic_emoji = '📥'
WHERE es_topic_id = 6;

-- 7: CDS Contextual Conditions
UPDATE es_topic
SET topic_summary = 'Represents medical, occupational, and other patient conditions that affect immunization clinical decision support (CDS) recommendations.',
    topic_emoji = '🩺'
WHERE es_topic_id = 7;

-- 8: TEFCA (National Exchange Framework)
UPDATE es_topic
SET topic_summary = 'Examines how the Trusted Exchange Framework and Common Agreement (TEFCA) may affect immunization information system participation in nationwide health information exchange.',
    topic_emoji = '🌐'
WHERE es_topic_id = 8;

-- 9: Immunization Record Matching
UPDATE es_topic
SET topic_summary = 'Addresses matching and deduplicating vaccination events within an immunization information system, separately from matching patient identities.',
    topic_emoji = '🧩'
WHERE es_topic_id = 9;

-- 10: Population Analytics
UPDATE es_topic
SET topic_summary = 'Uses immunization data for population-level public health analysis, reporting, surveillance, and decision-making.',
    topic_emoji = '📊'
WHERE es_topic_id = 10;

-- 11: Data Provenance Tracking
UPDATE es_topic
SET topic_summary = 'Captures the source and processing history of immunization data to support trust, auditing, and data quality.',
    topic_emoji = '🧾'
WHERE es_topic_id = 11;

-- 12: Vaccine Ordering Integration
UPDATE es_topic
SET topic_summary = 'Explores integrating vaccine ordering workflows among clinical systems, immunization information systems, and vaccine supply systems.',
    topic_emoji = '📦'
WHERE es_topic_id = 12;

-- 13: Immunization Decision Support (ImmDS)
UPDATE es_topic
SET topic_summary = 'Defines standardized interfaces for requesting immunization evaluations and recommendations from clinical decision support (CDS) engines.',
    topic_emoji = '💡'
WHERE es_topic_id = 13;

-- 14: Event-Based Exchange (FHIR Subscriptions)
UPDATE es_topic
SET topic_summary = 'Uses FHIR Subscriptions to notify systems when relevant immunization data changes instead of requiring repeated polling.',
    topic_emoji = '🔔'
WHERE es_topic_id = 14;

-- 15: Received Code Validation
UPDATE es_topic
SET topic_summary = 'Validates and interprets incoming immunization codes, including vaccine and manufacturer codes, before they are accepted by an immunization information system.',
    topic_emoji = '✅'
WHERE es_topic_id = 15;

-- 16: Vaccine Barcode Scanning
UPDATE es_topic
SET topic_summary = 'Uses barcode scanning at the point of care to capture accurate vaccine product, lot, and expiration information.',
    topic_emoji = '📷'
WHERE es_topic_id = 16;

-- 17: Consumer Record Corrections
UPDATE es_topic
SET topic_summary = 'Enables authenticated consumers to request corrections to inaccurate or incomplete immunization records and track their resolution.',
    topic_emoji = '✏️'
WHERE es_topic_id = 17;

-- 18: Reminder/Recall Services
UPDATE es_topic
SET topic_summary = 'Uses immunization information system data to identify and contact patients who are due or overdue for vaccination.',
    topic_emoji = '⏰'
WHERE es_topic_id = 18;

-- 19: Digital Vaccine Cards (SMART Health Cards)
UPDATE es_topic
SET topic_summary = 'Issues verifiable digital vaccination credentials through SMART Health Cards using FHIR data and QR codes.',
    topic_emoji = '🪪'
WHERE es_topic_id = 19;

-- 20: CDS Shared Decision Support (SCDM)
UPDATE es_topic
SET topic_summary = 'Represents recommendations that require shared clinical decision-making rather than routine application of the immunization schedule.',
    topic_emoji = '🤝'
WHERE es_topic_id = 20;

-- 21: Data Validation Services
UPDATE es_topic
SET topic_summary = 'Provides reusable services for validating data elements such as names, contact information, codes, and vaccine lot numbers.',
    topic_emoji = '🧪'
WHERE es_topic_id = 21;

-- 22: Patient Updates (ADT)
UPDATE es_topic
SET topic_summary = 'Uses HL7 version 2 Admission, Discharge, and Transfer (ADT) messages to keep patient demographics in immunization information systems current.',
    topic_emoji = '🔄'
WHERE es_topic_id = 22;

-- 23: Data Merge Visibility
UPDATE es_topic
SET topic_summary = 'Shows submitters how an immunization information system matched, merged, accepted, updated, or ignored submitted patient and vaccination data.',
    topic_emoji = '🔎'
WHERE es_topic_id = 23;

-- 24: Provider Enrollment Integration
UPDATE es_topic
SET topic_summary = 'Automates provider enrollment and onboarding into immunization information systems through standardized electronic interfaces.',
    topic_emoji = '📝'
WHERE es_topic_id = 24;

-- 25: CDS Schedule Source
UPDATE es_topic
SET topic_summary = 'Identifies the schedule authority responsible for an immunization evaluation or recommendation, such as the Advisory Committee on Immunization Practices.',
    topic_emoji = '📚'
WHERE es_topic_id = 25;

-- 26: CDC WSDL Authentication
UPDATE es_topic
SET topic_summary = 'Modernizes authentication for the Centers for Disease Control and Prevention web-service interface used for HL7 version 2 exchange.',
    topic_emoji = '🔑'
WHERE es_topic_id = 26;

-- 27: Digital Record Provenance
UPDATE es_topic
SET topic_summary = 'Associates historical immunizations with verifiable original digital records that document their source and authenticity.',
    topic_emoji = '📜'
WHERE es_topic_id = 27;

-- 28: IIS Terminology Services
UPDATE es_topic
SET topic_summary = 'Publishes standard and local terminology supported by an immunization information system for validation and interoperability.',
    topic_emoji = '🏷️'
WHERE es_topic_id = 28;

-- 29: Inventory Synchronization
UPDATE es_topic
SET topic_summary = 'Keeps vaccine inventory in clinical and immunization information systems synchronized through bidirectional exchange.',
    topic_emoji = '🔄'
WHERE es_topic_id = 29;

-- 30: Record Synchronization
UPDATE es_topic
SET topic_summary = 'Maintains alignment of patient and immunization records between an immunization information system and external systems over time.',
    topic_emoji = '🔁'
WHERE es_topic_id = 30;

-- 31: Immunization Vocabularies Collaboration (IVC)
UPDATE es_topic
SET topic_summary = 'Coordinates international vaccine terminology, mappings, and code-system maintenance through the Immunization Vocabularies Collaboration (IVC).',
    topic_emoji = '🌍'
WHERE es_topic_id = 31;

-- 32: International Patient Summary (IPS)
UPDATE es_topic
SET topic_summary = 'Uses the FHIR International Patient Summary (IPS) to share a concise patient health record, including immunizations, across systems and countries.',
    topic_emoji = '🌐'
WHERE es_topic_id = 32;

-- 33: Bulk Data Exchange (FHIR Bulk Data)
UPDATE es_topic
SET topic_summary = 'Uses the FHIR Bulk Data specification to retrieve large populations of immunization records for analytics and data sharing.',
    topic_emoji = '📤'
WHERE es_topic_id = 33;

-- 34: Vaccine Inventory Management
UPDATE es_topic
SET topic_summary = 'Tracks vaccine quantities, lots, ordering, distribution, and usage within immunization programs and connected systems.',
    topic_emoji = '📦'
WHERE es_topic_id = 34;

-- 35: Data Quality Reporting
UPDATE es_topic
SET topic_summary = 'Provides submitters with periodic reports and trends describing the quality, completeness, and validity of their immunization data.',
    topic_emoji = '📈'
WHERE es_topic_id = 35;

-- 36: Vaccine Exemptions and Deferrals
UPDATE es_topic
SET topic_summary = 'Represents vaccine exemptions and temporary or permanent deferrals that affect immunization requirements and clinical decisions.',
    topic_emoji = '⏸️'
WHERE es_topic_id = 36;

-- 37: School Data Exchange
UPDATE es_topic
SET topic_summary = 'Exchanges student immunization information between immunization information systems and schools for compliance and public health workflows.',
    topic_emoji = '🏫'
WHERE es_topic_id = 37;

-- 38: Newborn Identity Handling
UPDATE es_topic
SET topic_summary = 'Handles temporary names, changing identifiers, and other identity challenges when matching newborn records.',
    topic_emoji = '👶'
WHERE es_topic_id = 38;

-- 39: Inventory Reconciliation
UPDATE es_topic
SET topic_summary = 'Compares and corrects vaccine inventory balances between provider systems and immunization information systems.',
    topic_emoji = '⚖️'
WHERE es_topic_id = 39;

-- 40: Official Record Documents (PDF)
UPDATE es_topic
SET topic_summary = 'Generates official, portable immunization record documents in Portable Document Format (PDF) for patients and providers.',
    topic_emoji = '📄'
WHERE es_topic_id = 40;

-- 41: Patient Record Matching Integration
UPDATE es_topic
SET topic_summary = 'Standardizes how immunization information systems integrate patient-matching and identity-resolution services.',
    topic_emoji = '🧩'
WHERE es_topic_id = 41;

-- 42: Pharmacy Integration
UPDATE es_topic
SET topic_summary = 'Supports vaccine reporting and bidirectional immunization exchange between pharmacies and immunization information systems.',
    topic_emoji = '💊'
WHERE es_topic_id = 42;

-- 43: Digital Vaccine Cards (SMART Health Links)
UPDATE es_topic
SET topic_summary = 'Uses SMART Health Links to provide individuals with shareable, updateable access to verifiable digital vaccination records.',
    topic_emoji = '🔗'
WHERE es_topic_id = 43;

-- 44: ACK Error Reporting (ERR-5)
UPDATE es_topic
SET topic_summary = 'Standardizes detailed error codes in field ERR-5 of HL7 version 2 acknowledgement messages to give submitters clearer feedback.',
    topic_emoji = '🚨'
WHERE es_topic_id = 44;

-- 45: Acknowledgements (ACK)
UPDATE es_topic
SET topic_summary = 'Defines how HL7 version 2 acknowledgement (ACK) messages communicate whether immunization submissions succeeded, produced warnings, or failed.',
    topic_emoji = '📬'
WHERE es_topic_id = 45;

-- 46: Adverse Event Reporting (VAERS)
UPDATE es_topic
SET topic_summary = 'Examines whether and how immunization information systems should support reporting vaccine adverse events to the Vaccine Adverse Event Reporting System (VAERS).',
    topic_emoji = '🛡️'
WHERE es_topic_id = 46;

-- 47: Aggregate Data Exchange (IHE)
UPDATE es_topic
SET topic_summary = 'Uses Integrating the Healthcare Enterprise (IHE) profiles to exchange aggregate or population-level immunization data.',
    topic_emoji = '📊'
WHERE es_topic_id = 47;

-- 48: Antiviral Tracking
UPDATE es_topic
SET topic_summary = 'Explores whether immunization information systems should capture and exchange information about antiviral medications.',
    topic_emoji = '💊'
WHERE es_topic_id = 48;

-- 49: Appointment Scheduling Integration
UPDATE es_topic
SET topic_summary = 'Explores integration between immunization information systems and vaccination appointment scheduling services.',
    topic_emoji = '📅'
WHERE es_topic_id = 49;

-- 50: Bulk Data Submission
UPDATE es_topic
SET topic_summary = 'Supports submitting large volumes of immunization updates through files or APIs instead of individual real-time HL7 version 2 messages.',
    topic_emoji = '📦'
WHERE es_topic_id = 50;

-- 51: CDC HL7 v2 Guide (Release 1.5)
UPDATE es_topic
SET topic_summary = 'Covers the current national HL7 version 2.5.1 implementation guide for immunization update, acknowledgement, query, and response exchange.',
    topic_emoji = '📘'
WHERE es_topic_id = 51;

-- 52: CDC HL7 v2 Guide (Release 2)
UPDATE es_topic
SET topic_summary = 'Develops the next national HL7 version 2.5.1 implementation guide for immunization update, acknowledgement, query, and response exchange.',
    topic_emoji = '📗'
WHERE es_topic_id = 52;

-- 53: CDS Response Improvements (RSP)
UPDATE es_topic
SET topic_summary = 'Improves how HL7 version 2 response messages represent immunization evaluations, recommendations, schedules, and explanatory details.',
    topic_emoji = '💬'
WHERE es_topic_id = 53;

-- 54: Clinical Decision Support (CDS Hooks)
UPDATE es_topic
SET topic_summary = 'Uses CDS Hooks to deliver immunization clinical decision support within electronic health record workflows.',
    topic_emoji = '🪝'
WHERE es_topic_id = 54;

-- 55: COVID-19 / Mpox Reporting
UPDATE es_topic
SET topic_summary = 'Covers specialized immunization and public health reporting developed for the COVID-19 and mpox responses.',
    topic_emoji = '🦠'
WHERE es_topic_id = 55;

-- 56: Data Export Format (DAR-Based)
UPDATE es_topic
SET topic_summary = 'Extends the Data at Rest (DAR) extract format into a reusable flat-file standard for immunization information system data exports.',
    topic_emoji = '🗂️'
WHERE es_topic_id = 56;

-- 57: Disability Data Collection
UPDATE es_topic
SET topic_summary = 'Explores whether immunization information systems should collect disability-related information required by programs or jurisdictions.',
    topic_emoji = '♿'
WHERE es_topic_id = 57;

-- 58: Emergency Vaccination Reporting
UPDATE es_topic
SET topic_summary = 'Defines consistent, rapid vaccination reporting to public health authorities during emergencies and outbreak responses.',
    topic_emoji = '🚑'
WHERE es_topic_id = 58;

-- 59: Flat File Data Export
UPDATE es_topic
SET topic_summary = 'Defines a common flat-file format for exporting immunization information system data when messaging or APIs are not practical.',
    topic_emoji = '📤'
WHERE es_topic_id = 59;

-- 60: Homeless Data Integration (HMIS)
UPDATE es_topic
SET topic_summary = 'Explores data exchange between immunization information systems and Homeless Management Information Systems (HMIS).',
    topic_emoji = '🏠'
WHERE es_topic_id = 60;

-- 61: IIS Metrics Access (IISAR)
UPDATE es_topic
SET topic_summary = 'Provides direct, on-demand access to aggregate immunization information system metrics currently reported through the IIS Annual Report (IISAR).',
    topic_emoji = '📊'
WHERE es_topic_id = 61;

-- 62: IIS-to-IIS Exchange
UPDATE es_topic
SET topic_summary = 'Exchanges patient and immunization records between jurisdictional immunization information systems through national HL7 version 2 and IZ Gateway workflows.',
    topic_emoji = '↔️'
WHERE es_topic_id = 62;

-- 63: Lead Test Query
UPDATE es_topic
SET topic_summary = 'Enables authorized systems to query lead blood test information from jurisdictions that store it in an immunization information system.',
    topic_emoji = '🩸'
WHERE es_topic_id = 63;

-- 64: Lead Test Reporting
UPDATE es_topic
SET topic_summary = 'Enables submission of lead blood test results to jurisdictions that store them in an immunization information system.',
    topic_emoji = '🧪'
WHERE es_topic_id = 64;

-- 65: LOINC Usage in OBX
UPDATE es_topic
SET topic_summary = 'Defines consistent use of Logical Observation Identifiers Names and Codes (LOINC) in HL7 version 2 observation segments for immunization data.',
    topic_emoji = '🔬'
WHERE es_topic_id = 65;

-- 66: Preferred Name (Patient)
UPDATE es_topic
SET topic_summary = 'Transmits a patient''s preferred name alongside legal identifiers in HL7 version 2 immunization messages.',
    topic_emoji = '🏷️'
WHERE es_topic_id = 66;

-- 67: Priority Group Classification
UPDATE es_topic
SET topic_summary = 'Classifies patients into priority groups used for vaccine allocation, outreach, or emergency response.',
    topic_emoji = '🎯'
WHERE es_topic_id = 67;

-- 68: Serology Reporting
UPDATE es_topic
SET topic_summary = 'Explores capturing and exchanging antibody test results that may inform interpretation of immunization status.',
    topic_emoji = '🧫'
WHERE es_topic_id = 68;

-- 69: Sexual Orientation & Gender Identity (SOGI)
UPDATE es_topic
SET topic_summary = 'Explores representation of sexual orientation and gender identity (SOGI) information in immunization information systems.',
    topic_emoji = '👥'
WHERE es_topic_id = 69;

-- 70: SMART Applications (SMART on FHIR)
UPDATE es_topic
SET topic_summary = 'Uses SMART on FHIR applications to provide secure, user-facing tools and workflows connected to immunization information systems.',
    topic_emoji = '📲'
WHERE es_topic_id = 70;

-- 71: TB Screening Data
UPDATE es_topic
SET topic_summary = 'Explores capturing and exchanging tuberculosis screening information through immunization information systems.',
    topic_emoji = '🫁'
WHERE es_topic_id = 71;

-- 72: Vaccine Availability Search
UPDATE es_topic
SET topic_summary = 'Helps individuals locate providers with available vaccines by combining location, provider, and inventory information.',
    topic_emoji = '📍'
WHERE es_topic_id = 72;

-- 73: Vaccine Storage & Temperature
UPDATE es_topic
SET topic_summary = 'Explores monitoring vaccine storage conditions and temperatures through immunization information systems or connected equipment.',
    topic_emoji = '🌡️'
WHERE es_topic_id = 73;

-- 74: Immunization Focus Group (IFG)
UPDATE es_topic
SET topic_summary = 'Provides an HL7 Public Health Work Group forum for discussing new and emerging immunization interoperability standards.',
    topic_emoji = '🗣️'
WHERE es_topic_id = 74;

-- 75: VXU on FHIR
UPDATE es_topic
SET topic_summary = 'Defines a FHIR alternative to HL7 version 2 vaccine update and acknowledgement workflows for submitting immunization histories and receiving processing results.',
    topic_emoji = '📨'
WHERE es_topic_id = 75;

-- 76: QBP on FHIR
UPDATE es_topic
SET topic_summary = 'Defines FHIR alternatives to HL7 version 2 immunization query and response workflows, including patient matching, history, evaluations, and recommendations.',
    topic_emoji = '🔎'
WHERE es_topic_id = 76;

-- 77: HL7 v2.5.1 School Roster Reporting
UPDATE es_topic
SET topic_summary = 'Uses HL7 version 2.5.1 messages to exchange student roster information between school information systems and immunization information systems.',
    topic_emoji = '🏫'
WHERE es_topic_id = 77;

-- 78: ADT on FHIR
UPDATE es_topic
SET topic_summary = 'Explores FHIR-based automation for establishing and maintaining data exchange relationships with immunization information systems.',
    topic_emoji = '⚙️'
WHERE es_topic_id = 78;

-- 79: Bulk FHIR Query
UPDATE es_topic
SET topic_summary = 'Uses bulk FHIR queries to obtain demographic or other patient updates from healthcare organizations for public health purposes.',
    topic_emoji = '📚'
WHERE es_topic_id = 79;

-- 80: Clinical Quality Language
UPDATE es_topic
SET topic_summary = 'Uses Clinical Quality Language (CQL) to express computable clinical quality measures and decision-support logic.',
    topic_emoji = '🧮'
WHERE es_topic_id = 80;

-- 81: Direct Trust
UPDATE es_topic
SET topic_summary = 'Explores the DirectTrust privacy-enhancing health record locator and credential ecosystem for nationwide patient identity and matching.',
    topic_emoji = '🕵️'
WHERE es_topic_id = 81;

-- 82: HL7 v2 to FHIR Translation
UPDATE es_topic
SET topic_summary = 'Translates immunization data between HL7 version 2.5.1 messages and FHIR resources in either direction.',
    topic_emoji = '🔄'
WHERE es_topic_id = 82;

-- 83: Identification of Medicinal Products (IDMP)
UPDATE es_topic
SET topic_summary = 'Applies the Identification of Medicinal Products (IDMP) standards to uniquely identify regulated vaccine and medicinal products internationally.',
    topic_emoji = '🧬'
WHERE es_topic_id = 83;

-- 84: IIS to EHR Query
UPDATE es_topic
SET topic_summary = 'Explores whether an immunization information system should query electronic health records for non-immunization patient information through FHIR.',
    topic_emoji = '🏥'
WHERE es_topic_id = 84;

-- 85: Insurance
UPDATE es_topic
SET topic_summary = 'Examines limited use cases for collecting or exchanging patient insurance information through immunization information systems.',
    topic_emoji = '🧾'
WHERE es_topic_id = 85;

-- 86: Machine Learning
UPDATE es_topic
SET topic_summary = 'Explores machine learning, artificial intelligence, and large language models for immunization interoperability and program operations.',
    topic_emoji = '🤖'
WHERE es_topic_id = 86;

-- 87: Multi-Jurisdictional Query
UPDATE es_topic
SET topic_summary = 'Allows organizations operating across jurisdictions to query multiple immunization information systems through a coordinated request.',
    topic_emoji = '🗺️'
WHERE es_topic_id = 87;

-- 88: Newborn Admission Notification Information  (NANI)
UPDATE es_topic
SET topic_summary = 'Uses the Newborn Admission Notification Information (NANI) profile to notify public health programs promptly when a newborn is admitted.',
    topic_emoji = '👶'
WHERE es_topic_id = 88;

-- 89: National Immunization Technical Advisory Groups
UPDATE es_topic
SET topic_summary = 'Examines national expert advisory groups that develop evidence-based vaccine policy and immunization schedule recommendations.',
    topic_emoji = '👩‍⚕️'
WHERE es_topic_id = 89;

-- 90: Product Identifiers/Serialization
UPDATE es_topic
SET topic_summary = 'Uses serialized identifiers to distinguish individual vaccine or medicinal-product packages throughout the supply chain.',
    topic_emoji = '🔢'
WHERE es_topic_id = 90;

-- 91: Race/Ethnicity
UPDATE es_topic
SET topic_summary = 'Addresses changes to the collection and exchange of race and ethnicity information in United States immunization workflows.',
    topic_emoji = '👥'
WHERE es_topic_id = 91;

-- 92: Social Determinants of Health (SDOH)
UPDATE es_topic
SET topic_summary = 'Explores the appropriate role of social determinants of health (SDOH) information within immunization information systems.',
    topic_emoji = '🏘️'
WHERE es_topic_id = 92;

-- 93: Space Health
UPDATE es_topic
SET topic_summary = 'Explores FHIR-based health records and immunization interoperability for astronauts and spaceflight missions.',
    topic_emoji = '🚀'
WHERE es_topic_id = 93;

-- 94: Synthetic Data
UPDATE es_topic
SET topic_summary = 'Creates realistic but non-identifying immunization data for software testing, demonstrations, training, and research.',
    topic_emoji = '🧪'
WHERE es_topic_id = 94;

-- 95: VTrcks-IIS API
UPDATE es_topic
SET topic_summary = 'Integrates immunization information systems with the Centers for Disease Control and Prevention Vaccine Tracking System (VTrckS) for publicly funded vaccine ordering and distribution.',
    topic_emoji = '🚚'
WHERE es_topic_id = 95;

-- 96: FHIR Open Tickets
UPDATE es_topic
SET topic_summary = 'Reviews and coordinates open FHIR specification issues related to immunization workflows and implementation guidance.',
    topic_emoji = '🎫'
WHERE es_topic_id = 96;

-- 97: IIP Collaborative
UPDATE es_topic
SET topic_summary = 'Coordinates the Immunization Integration Program (IIP) partnership to improve immunization data exchange, management, testing, and use.',
    topic_emoji = '🤝'
WHERE es_topic_id = 97;

-- 98: Provider Identifier
UPDATE es_topic
SET topic_summary = 'Develops a national strategy for consistently identifying immunizing provider organizations and sites across jurisdictions.',
    topic_emoji = '🏥'
WHERE es_topic_id = 98;

-- 99: Patient Record Matching
UPDATE es_topic
SET topic_summary = 'Improves how patient identities are represented, matched, and resolved between systems during immunization interoperability.',
    topic_emoji = '🧩'
WHERE es_topic_id = 99;

-- 100: Query by Parameter (QBP)
UPDATE es_topic
SET topic_summary = 'Uses HL7 version 2 Query by Parameter (QBP) messages to request immunization histories, evaluations, and recommendations.',
    topic_emoji = '❓'
WHERE es_topic_id = 100;

-- 101: Estimated Vaccination Dates
UPDATE es_topic
SET topic_summary = 'Represents partially known vaccination dates without inventing an exact day that could mislead clinical decisions or analytics.',
    topic_emoji = '📅'
WHERE es_topic_id = 101;

-- 102: Subpotent Vaccinations
UPDATE es_topic
SET topic_summary = 'Records administered vaccinations that should not count toward immunity because the product, storage, dose, or administration was inadequate.',
    topic_emoji = '⚠️'
WHERE es_topic_id = 102;

-- 103: NDC Transition to 12-Digit Format
UPDATE es_topic
SET topic_summary = 'Prepares immunization systems and vocabularies for the United States transition from 10-digit to 12-digit National Drug Codes (NDCs).',
    topic_emoji = '🔢'
WHERE es_topic_id = 103;

-- 104: England
UPDATE es_topic
SET topic_summary = 'Examines England''s National Health Service immunization systems, vaccine terminology, standards, governance, and data exchange practices.',
    topic_emoji = '🏴'
WHERE es_topic_id = 104;

-- 105: Canada
UPDATE es_topic
SET topic_summary = 'Examines Canada''s immunization registries, vaccine coding, standards, and exchange practices across provinces and territories.',
    topic_emoji = '🇨🇦'
WHERE es_topic_id = 105;

-- 106: Mexico
UPDATE es_topic
SET topic_summary = 'Examines Mexico''s immunization information systems, vaccine coding, standards, and public health data exchange practices.',
    topic_emoji = '🇲🇽'
WHERE es_topic_id = 106;

-- 107: France
UPDATE es_topic
SET topic_summary = 'Examines France''s immunization recommendations, vaccine terminology, digital health infrastructure, and interoperability practices.',
    topic_emoji = '🇫🇷'
WHERE es_topic_id = 107;

-- 108: Norway
UPDATE es_topic
SET topic_summary = 'Examines Norway''s national digital health infrastructure, immunization registry practices, standards, and data governance.',
    topic_emoji = '🇳🇴'
WHERE es_topic_id = 108;

-- 109: Australia
UPDATE es_topic
SET topic_summary = 'Examines Australia''s national immunization systems, vaccine terminology, standards, and interoperability community.',
    topic_emoji = '🇦🇺'
WHERE es_topic_id = 109;

-- 110: New Zealand
UPDATE es_topic
SET topic_summary = 'Examines New Zealand''s immunization systems, vaccine coding, standards, and collaboration with regional partners.',
    topic_emoji = '🇳🇿'
WHERE es_topic_id = 110;

-- 111: Philippines
UPDATE es_topic
SET topic_summary = 'Examines immunization data collection, coding, exchange, and collaboration opportunities in the Philippines.',
    topic_emoji = '🇵🇭'
WHERE es_topic_id = 111;

-- 112: Netherlands
UPDATE es_topic
SET topic_summary = 'Examines the Netherlands'' digital health infrastructure, immunization programs, terminology, and interoperability practices.',
    topic_emoji = '🇳🇱'
WHERE es_topic_id = 112;

-- 113: Finland
UPDATE es_topic
SET topic_summary = 'Examines Finland''s national health information infrastructure, vaccine information systems, standards, and public health practices.',
    topic_emoji = '🇫🇮'
WHERE es_topic_id = 113;

-- 114: Spain
UPDATE es_topic
SET topic_summary = 'Examines Spain''s regional and national immunization systems, governance, terminology, and standards coordination.',
    topic_emoji = '🇪🇸'
WHERE es_topic_id = 114;

-- 115: Scotland
UPDATE es_topic
SET topic_summary = 'Examines NHS Scotland''s immunization systems, vaccine terminology, standards, and national digital health infrastructure.',
    topic_emoji = '🏴'
WHERE es_topic_id = 115;

-- 116: Pan American Health Organization (PAHO)
UPDATE es_topic
SET topic_summary = 'Examines how the Pan American Health Organization (PAHO) supports regional immunization programs, digital health, terminology, and cross-country collaboration.',
    topic_emoji = '🌎'
WHERE es_topic_id = 116;

-- 117: Unified Nomenclature of Vaccines (NUVA)
UPDATE es_topic
SET topic_summary = 'Uses the Unified Nomenclature of Vaccines (NUVA) to identify vaccine products and interpret vaccination histories across countries and source systems.',
    topic_emoji = '💉'
WHERE es_topic_id = 117;

-- 118: FHIR Dev Days
UPDATE es_topic
SET topic_summary = 'Tracks the international FHIR DevDays conference for implementation experience, education, collaboration, and emerging Fast Healthcare Interoperability Resources practices.',
    topic_emoji = '👩‍💻'
WHERE es_topic_id = 118;

-- 119: Fact of Death Exchange
UPDATE es_topic
SET topic_summary = 'Standardizes exchange of verified patient death information among vital records, healthcare, public health, and immunization information systems.',
    topic_emoji = '🕯️'
WHERE es_topic_id = 119;

-- 120: Building Bridges
UPDATE es_topic
SET topic_summary = 'Builds relationships with countries, regional organizations, and terminology authorities to understand vaccine vocabularies and align interoperability efforts.',
    topic_emoji = '🌉'
WHERE es_topic_id = 120;

-- 121: Message Quality Evaluation Tool (MQE)
UPDATE es_topic
SET topic_summary = 'Uses the open-source Message Quality Evaluation Tool (MQE) to assess and improve the quality of incoming HL7 version 2 immunization data.',
    topic_emoji = '🧰'
WHERE es_topic_id = 121;

-- 122: Dubai
UPDATE es_topic
SET topic_summary = 'Examines Dubai''s immunization systems, vaccine coding, and health information exchange through organizations such as Dubai Health Authority and NABIDH.',
    topic_emoji = '🇦🇪'
WHERE es_topic_id = 122;

-- 123: Interface Security
UPDATE es_topic
SET topic_summary = 'Coordinates security architecture for immunization interfaces, including authentication, authorization, transport protection, credentials, and operational responsibilities.',
    topic_emoji = '🛡️'
WHERE es_topic_id = 123;

-- 124: Certificate Management
UPDATE es_topic
SET topic_summary = 'Addresses issuance, installation, renewal, rotation, monitoring, revocation, and ownership of digital certificates used by immunization interfaces.',
    topic_emoji = '📜'
WHERE es_topic_id = 124;

COMMIT;

-- Verification: all current topics should now have both values populated.
SELECT
  COUNT(*) AS total_topics,
  SUM(topic_summary IS NOT NULL AND topic_summary <> '') AS topics_with_summary,
  SUM(topic_emoji IS NOT NULL AND topic_emoji <> '') AS topics_with_emoji
FROM es_topic;

-- Review the seeded values.
SELECT es_topic_id, topic_name, topic_emoji, topic_summary
FROM es_topic
ORDER BY es_topic_id;
