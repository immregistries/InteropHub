# Topic Relationship Proposals — Topic Space #1 ("Emerging Standards")

**Status:** Draft for review — temporary working document, not a permanent spec.
**Generated:** 2026-08-24, from a full read of all 110 active topics in Topic Space #1 (`es_topic_space_id = 1`, code `emerging-standards`) in the local dev database.
**Purpose:** Propose `es_topic_relationship` links a curator/admin could add via the topic management UI, to make the web of connections between topics explorable instead of implicit.

---

## 1. How relationships work today

Relationships live in `es_topic_relationship` (`EsTopicRelationship` / `EsTopicRelationshipDao` / `EsTopicRelationshipServlet`) as **directed, typed** rows: `from_topic_id → to_topic_id` with a `relationship_type`. Each type has a forward label (shown on the "from" topic's page under *Related Topics*) and an inverse label (shown on the "to" topic's page):

| Type | Forward label (A → B) | Inverse label (B's page) |
|---|---|---|
| `RELATED_TO` | related to | related to |
| `OVERLAPS` | overlaps | overlaps |
| `DEPENDS_ON` | depends on | depended on by |
| `FEEDS_INTO` | feeds into | fed by |
| `DERIVED_FROM` | derived from | source of |
| `SUPERSEDES` | supersedes | superseded by |
| `BLOCKER_FOR` | blocker for | blocked by |
| `OPERATIONALIZES` | operationalizes | operationalized by |
| `DUPLICATE_OF` | duplicate of | duplicate of |

Only two types are symmetric in meaning (`RELATED_TO`, `OVERLAPS`, `DUPLICATE_OF` — same label both directions); the rest are meaningfully directional, so I've chosen a direction for every proposal below rather than defaulting everything to `RELATED_TO`.

Authorization: a link can be added by an admin, or by a **champion/supporter of the `from` topic**. Self-links are blocked; duplicate links silently no-op (unique constraint).

**Current state of the data:** Topic Space #1 has 110 active topics and exactly **one** existing relationship: *Query by Parameter (QBP)* `RELATED_TO` *QBP on FHIR*. Everything below is new.

## 2. Method

I read every topic's `topic_name`, `description`, and `topic_summary`, and used the informal `neighborhood` tag (a free-text categorization field, not itself a relationship) as a starting signal for clustering — but the actual proposals are based on what the description text says a topic does, depends on, extends, or competes with, not just shared tags (the neighborhood field has inconsistent casing/typos, e.g. "Auxiliary" vs "Auxillary", and several topics have `NULL`).

Proposals are grouped into thematic clusters below for readability. Within each cluster, the table gives `From → Type → To` plus a one-line rationale grounded in the topics' own descriptions.

I did **not** try to link every one of the 110 topics — a handful (e.g. *FHIR Dev Days*, *Machine Learning*, *Synthetic Data*) are genuinely cross-cutting or standalone and I only proposed links where the text gave a real basis. Treat this as a first pass to accept, reject, or retype per-row in the UI, not a batch import.

---

## 3. Resolved: the two near-duplicate-looking pairs

Two pairs initially looked like they might be duplicates. Reviewed and resolved:

| Topic A | Topic B | Resolution |
|---|---|---|
| **Immunization CDS (ImmDS + HALO)** *(#1, Standard)* | **Immunization Decision Support (ImmDS)** *(#13, Standard)* | Not a duplicate — ImmDS + HALO is the successor to ImmDS. ImmDS is kept as its own topic because it's a published standard implementable today. Modeled as `SUPERSEDES` in §4.1. |
| **Data Export Format (DAR-Based)** *(#56, Exploratory)* | **Flat File Data Export** *(#59, Exploratory)* | Keep both — DAR-Based is a competing/specific proposal to solve the same export-format problem, worth tracking separately for now. Modeled as `OVERLAPS` in §4.10. |

---

## 4. Proposed relationships by cluster

### 4.1 CDS / forecasting core

| From | Type | To | Rationale |
|---|---|---|---|
| CDS Contextual Conditions | `FEEDS_INTO` | Immunization CDS (ImmDS + HALO) | Contextual Conditions explicitly "expands immunization decision support" consumed by the CDS evaluation interface. |
| CDS Contextual Conditions | `FEEDS_INTO` | Immunization Decision Support (ImmDS) | Same rationale — contextual data is an input to CDS forecasting. |
| Clinical Decision Support (CDS Hooks) | `RELATED_TO` | Immunization CDS (ImmDS + HALO) | CDS Hooks is an EHR-facing delivery channel for the same recommendations ImmDS defines. |
| IIS to EHR Query | `OVERLAPS` | Clinical Decision Support (CDS Hooks) | Description literally says "Overlaps with CDS." |
| CDS WSDL Authentication | `RELATED_TO` | CDS Response Improvements (RSP) | Both are refinements to the same CDC WSDL/HL7 v2 CDS response channel. |
| CDS Response Improvements (RSP) | `RELATED_TO` | CDS Schedule Source | Both are conveyed within the same HL7 v2 RSP message as CDS output. |
| CDS Response Improvements (RSP) | `RELATED_TO` | CDS Shared Decision Support (SCDM) | Same — both are RSP-conveyed CDS refinements. |
| CDS Schedule Source | `RELATED_TO` | CDS Shared Decision Support (SCDM) | Sibling refinements to how RSP represents recommendation nuance. |
| National Immunization Technical Advisory Groups | `RELATED_TO` | CDS Schedule Source | Schedule Source's own example of a "schedule authority" (ACIP) is a NITAG. |
| Clinical Quality Language | `RELATED_TO` | Immunization Decision Support (ImmDS) | CQL is the kind of computable logic language CDS/measure engines like ImmDS would express recommendation logic in. |
| Vaccination Dates (Estimated) | `RELATED_TO` | Immunization CDS (ImmDS + HALO) | Estimated/partial dates directly affect the accuracy of CDS due/overdue evaluations. |
| Immunization CDS (ImmDS + HALO) | `SUPERSEDES` | Immunization Decision Support (ImmDS) | ImmDS + HALO is the successor effort to the published ImmDS standard; ImmDS remains its own topic since it's implementable today, but the newer effort supersedes it going forward. |

### 4.2 HL7 v2 ↔ FHIR modernization pairs

| From | Type | To | Rationale |
|---|---|---|---|
| QBP on FHIR | `DERIVED_FROM` | CDC HL7 v2 Guide (Release 1.5) | QBP on FHIR is explicitly framed as "the alternative FHIR solution(s) to HL7 v2 QBP/RSP," which the Release 1.5 guide defines. |
| VXU on FHIR | `DERIVED_FROM` | CDC HL7 v2 Guide (Release 1.5) | Same pattern for the VXU/ACK exchange the guide defines. |
| VXU on FHIR | `RELATED_TO` | QBP on FHIR | Companion FHIR-alternative efforts, both Exploratory, developed as a pair (update vs. query). |
| HL7 v2 to FHIR Translation | `RELATED_TO` | QBP on FHIR | Translation is an alternate strategy (convert v2↔FHIR) to the same goal QBP‑on‑FHIR pursues (a FHIR-native query). |
| HL7 v2 to FHIR Translation | `RELATED_TO` | VXU on FHIR | Same reasoning for the update/ack side. |
| ADT on FHIR | `RELATED_TO` | Patient Updates (ADT) | FHIR automation of the same v2 ADT-driven demographic update workflow. |
| ADT on FHIR | `RELATED_TO` | HL7 v2 to FHIR Translation | Both are TEFCA/FHIR modernization efforts touching the same v2 message family. |
| Bulk FHIR Query | `OVERLAPS` | IIS to EHR Query | Both explore public-health-initiated FHIR queries out to healthcare organizations/EHRs. |
| Bulk FHIR Query | `RELATED_TO` | Bulk Data Exchange (FHIR Bulk Data) | Both apply the FHIR bulk-data paradigm, in opposite directions (IIS querying out vs. being queried). |

### 4.3 HL7 v2 Guide governance

| From | Type | To | Rationale |
|---|---|---|---|
| CDC HL7 v2 Guide (Release 2) | `SUPERSEDES` | CDC HL7 v2 Guide (Release 1.5) | Release 2 is the "next version," explicitly replacing 1.5 as the national baseline. |
| ACK Error Reporting (ERR-5) | `FEEDS_INTO` | CDC HL7 v2 Guide (Release 2) | Description states it "will be included in the CDC HL7 v2 Guide (Release 2)." |
| CDS Response Improvements (RSP) | `FEEDS_INTO` | CDC HL7 v2 Guide (Release 2) | Description states it's "aligned with CDC HL7 v2 Guide (Release 2)." |
| Preferred Name (Patient) | `FEEDS_INTO` | CDC HL7 v2 Guide (Release 2) | Already addressed via TAB guidance; a defined enhancement destined for the next guide release. |
| Acknowledgements (ACK) | `RELATED_TO` | ACK Error Reporting (ERR-5) | ERR-5 is a refinement of how ACK messages report errors. |
| LOINC Usage in OBX | `RELATED_TO` | CDC HL7 v2 Guide (Release 1.5) | Builds directly on the OBX guidance already defined in the baseline guide. |
| Patient Updates (ADT) | `RELATED_TO` | CDC HL7 v2 Guide (Release 1.5) | ADT usage sits alongside VXU/QBP as part of the same v2 exchange ecosystem the guide governs. |
| IIS-to-IIS Exchange | `RELATED_TO` | CDC HL7 v2 Guide (Release 1.5) | IIS-to-IIS exchange is implemented by layering the IZ Gateway over standard VXU/QBP-RSP from the guide. |
| IIS-to-IIS Exchange | `RELATED_TO` | Multi-Jurisdictional Query | Both address cross-jurisdiction querying; multi-jurisdictional query is the "ask several IIS at once" variant of IIS-to-IIS exchange. |

### 4.4 Patient identity & matching

| From | Type | To | Rationale |
|---|---|---|---|
| Patient Record Matching Integration | `DEPENDS_ON` | Patient Record Matching | Integration standardization presupposes the general matching capability already exists. |
| Immunization Record Matching | `RELATED_TO` | Patient Record Matching | Description explicitly contrasts the two ("distinct from patient matching") — related but not the same problem. |
| Newborn Identity Handling | `RELATED_TO` | Patient Record Matching | A specific hard case (temporary/changing newborn names) of the general matching problem. |
| Direct Trust | `RELATED_TO` | Patient Record Matching | DirectTrust's PEHRLS ecosystem is explicitly a "nationwide patient credential and matching ecosystem." |
| Fact of Death Exchange | `RELATED_TO` | Patient Record Matching | Description calls out "identity matching, patient reconciliation" as core to death-exchange workflows. |
| Data Merge Visibility | `RELATED_TO` | Patient Record Matching | Merge visibility is about surfacing how matching decisions were made to submitters. |
| Machine Learning | `RELATED_TO` | Patient Record Matching | Matching/deduplication is a natural ML application area referenced generally under this topic. |
| Consumer Identity & Access | `DEPENDS_ON` | Patient Record Matching | Resolving "which patient is this consumer" for self-service access relies on the same matching foundation. |

### 4.5 Consumer access

| From | Type | To | Rationale |
|---|---|---|---|
| Consumer Access to Records | `DEPENDS_ON` | Consumer Identity & Access | Records access requires the consumer to first be authenticated/identified. |
| Consumer Record Corrections | `DEPENDS_ON` | Consumer Identity & Access | Description: "allowing **authenticated** individuals to request corrections." |
| Digital Vaccine Cards (SMART Health Cards) | `OVERLAPS` | Digital Vaccine Cards (SMART Health Links) | Two mechanisms for the same verifiable-credential use case; Links is generally viewed as the newer/broader mechanism. |
| Consumer Access to Records | `RELATED_TO` | Digital Vaccine Cards (SMART Health Cards) | SMART Health Cards is one concrete way consumers access/share their records. |
| Consumer Access to Records | `RELATED_TO` | Official Record Documents (PDF) | PDF generation is another output channel for the same consumer-facing record. |
| Consumer Access to Records | `RELATED_TO` | International Patient Summary (IPS) | IPS is another standardized summary format IIS data could feed into for the individual. |
| SMART Applications (SMART on FHIR) | `RELATED_TO` | Consumer Access to Records | SMART apps are a plausible delivery vehicle for consumer-facing record access. |
| SMART Applications (SMART on FHIR) | `RELATED_TO` | Clinical Decision Support (CDS Hooks) | Sibling SMART/FHIR app-integration patterns (patient-facing vs. clinician-facing). |
| Appointment Scheduling Integration | `RELATED_TO` | Vaccine Availability Search | Both are COVID-era, consumer-facing tools that emerged outside the IIS and share the same "connect IIS-adjacent data to a public tool" shape. |
| Digital Record Provenance | `RELATED_TO` | International Patient Summary (IPS) | IPS is named as an example verifiable digital artifact this topic's provenance concept would apply to. |

### 4.6 Data quality

| From | Type | To | Rationale |
|---|---|---|---|
| Data Quality Notifications | `FEEDS_INTO` | Data Quality Reporting | Real-time/point-in-time notifications are the raw signal that periodic quality reports aggregate and trend. |
| Data Merge Visibility | `RELATED_TO` | Data Provenance Tracking | Both are about giving submitters/users insight into how IIS processed their data. |
| Data Provenance Tracking | `RELATED_TO` | Digital Record Provenance | Shared theme (tracking source/history of data) at two different scopes — internal processing history vs. verifiable original documents. |
| Subpotent Vaccinations | `RELATED_TO` | Vaccine Lot Validation | Storage/cold-chain and lot problems are a common root cause of a dose being recorded as subpotent. |
| Subpotent Vaccinations | `RELATED_TO` | Data Quality Notifications | A subpotent dose is exactly the kind of condition a quality notification would flag to a submitter/clinician. |
| Received Code Validation | `RELATED_TO` | Data Validation Services | Code validation (CVX/MVX) is one concrete instance of the general reusable validation service concept. |
| MQE (Message Quality Evaluation Tool) | `RELATED_TO` | Data Quality Reporting | MQE is a concrete tool implementing the same quality-metrics/reporting goal. |
| MQE (Message Quality Evaluation Tool) | `RELATED_TO` | ACK Error Reporting (ERR-5) | Both aim to give submitters clearer, more structured feedback about message problems. |
| Synthetic Data | `RELATED_TO` | MQE (Message Quality Evaluation Tool) | Synthetic messages are natural test input for a quality-evaluation tool like MQE. |

### 4.7 Vaccine inventory & supply chain

| From | Type | To | Rationale |
|---|---|---|---|
| Vaccine Inventory Management | `RELATED_TO` | Inventory Reconciliation | Reconciliation is one concrete workflow within the broader inventory management topic. |
| Vaccine Inventory Management | `RELATED_TO` | Inventory Synchronization | Same — synchronization is another concrete workflow within the broader topic. |
| Inventory Reconciliation | `OVERLAPS` | Inventory Synchronization | Both solve "keep provider and IIS inventory counts aligned," reconciliation after-the-fact vs. synchronization continuously. |
| Vaccine Barcode Scanning | `FEEDS_INTO` | Vaccine Lot Validation | Barcode scanning is described as capturing "product, lot, and expiration" data, which lot validation then checks. |
| Vaccine Ordering Integration | `RELATED_TO` | VTrckS-IIS API | VTrckS is the CDC system that actually handles publicly funded vaccine ordering/distribution this topic explores integrating with. |
| VTrckS-IIS API | `RELATED_TO` | Vaccine Inventory Management | VTrckS integration is one channel of the broader inventory tracking goal. |
| Vaccine Storage & Temperature | `RELATED_TO` | Subpotent Vaccinations | Storage/temperature excursions are a primary cause of subpotency. |
| NDC Transition to 12-Digit Format | `BLOCKER_FOR` | Received Code Validation | The format transition can break existing lookup/validation logic keyed on 10-digit NDCs until updated. |
| NDC Transition to 12-Digit Format | `BLOCKER_FOR` | Vaccine Barcode Scanning | Barcode parsing logic tied to the current NDC digit format needs rework ahead of the transition. |
| NDC Transition to 12-Digit Format | `RELATED_TO` | Product Identifiers/Serialization | Both concern how vaccine products are uniquely identified in exchanged data. |

### 4.8 Terminology & product identification

| From | Type | To | Rationale |
|---|---|---|---|
| Immunization Vocabularies Collaboration (IVC) | `RELATED_TO` | Unified Nomenclature of Vaccines (NUVA) | Both are international vaccine-vocabulary alignment efforts (CVX/CDC-led vs. NUVA), covering overlapping ground. |
| Unified Nomenclature of Vaccines (NUVA) | `RELATED_TO` | Identification of Medicinal Products (IDMP) | Both are international standards for uniquely identifying vaccine/medicinal products. |
| Product Identifiers/Serialization | `RELATED_TO` | Identification of Medicinal Products (IDMP) | Serialized package identifiers and IDMP both address unique product identification, at different points in the supply chain. |
| Product Identifiers/Serialization | `RELATED_TO` | Unified Nomenclature of Vaccines (NUVA) | Overlapping goal of unambiguous product identification across systems/countries. |
| Building Bridges | `RELATED_TO` | Immunization Vocabularies Collaboration (IVC) | Building Bridges' country/partner outreach directly supports IVC's international vocabulary-alignment mission. |
| Building Bridges | `RELATED_TO` | Unified Nomenclature of Vaccines (NUVA) | Same rationale — international vocabulary alignment work. |
| Received Code Validation | `RELATED_TO` | Immunization Vocabularies Collaboration (IVC) | Received code validation checks incoming codes (CVX/MVX) against the vocabulary IVC maintains. |
| IIS Terminology Services | `RELATED_TO` | Received Code Validation | Publishing supported codes (terminology services) and validating incoming codes are two sides of the same consistency problem. |

### 4.9 Security & interfaces

| From | Type | To | Rationale |
|---|---|---|---|
| Certificate Management | `DEPENDS_ON` | Interface Security | Interface Security is explicitly described as the "overarching space" for identifying where guidance like certificate automation is needed. |
| CDC WSDL Authentication | `DEPENDS_ON` | Interface Security | Modernizing WSDL auth (toward OAuth2) is an instance of the broader authN/authZ modernization Interface Security scopes out. |

### 4.10 Reporting, exchange volume & synchronization

| From | Type | To | Rationale |
|---|---|---|---|
| Data Export Format (DAR-Based) | `OVERLAPS` | Flat File Data Export | Both propose a common flat-file export standard for IIS; see the duplicate-candidates note in §3 — treat one as possibly redundant with the other. |
| Flat File Data Import | `RELATED_TO` | Flat File Data Export | Natural import/export counterpart pair around the same flat-file concept. |
| Bulk Data Submission | `RELATED_TO` | Flat File Data Import | File-based bulk import is one plausible transport for high-volume submission. |
| Bulk Data Submission | `RELATED_TO` | Bulk Data Exchange (FHIR Bulk Data) | Mirror-image "bulk" paradigms: submitting large volumes in vs. retrieving large volumes out. |
| Reminder/Recall Services | `RELATED_TO` | Bulk Data Exchange (FHIR Bulk Data) | Description states reminder/recall "may align with bulk data exchange approaches." |
| Record Synchronization | `RELATED_TO` | Event-Based Exchange (FHIR Subscriptions) | Subscriptions are a plausible mechanism for keeping records synchronized over time rather than one-time exchange. |
| Aggregate Data Exchange (IHE) | `RELATED_TO` | Population Analytics | Both concern population/aggregate-level (not per-patient) immunization data use. |
| Population Analytics | `RELATED_TO` | Bulk Data Exchange (FHIR Bulk Data) | Description states analytics "may leverage FHIR Bulk Data." |
| IIS Metrics Access (IISAR) | `RELATED_TO` | Data Quality Reporting | Both are about giving on-demand/periodic visibility into IIS-level metrics rather than per-record data. |
| IIS Metrics Access (IISAR) | `RELATED_TO` | Population Analytics | Both surface aggregate statistics about IIS/population data. |

### 4.11 National emergency / external-driver reporting

| From | Type | To | Rationale |
|---|---|---|---|
| Emergency Vaccination Reporting | `DERIVED_FROM` | COVID-19 / Mpox Reporting | Description states it's meant to "replace the ad hoc flat-file approaches used during COVID-19 and Mpox," i.e. a generalized successor drawn from that experience. |
| Adverse Event Reporting (VAERS) | `RELATED_TO` | COVID-19 / Mpox Reporting | Adverse-event tracking was a significant part of the COVID reporting response. |
| Priority Group Classification | `RELATED_TO` | COVID-19 / Mpox Reporting | Priority classification was implemented by some IIS specifically for COVID-era allocation/reporting. |
| Vaccine Exemptions and Deferrals | `RELATED_TO` | COVID-19 / Mpox Reporting | Description notes exemptions/deferrals "became more visible during COVID-19." |
| TEFCA (National Exchange Framework) | `RELATED_TO` | ADT on FHIR | TEFCA is described as "a driver of topics" including FHIR-based exchange automation like this one. |
| TEFCA (National Exchange Framework) | `RELATED_TO` | Bulk FHIR Query | Same — TEFCA participation is a likely driver for bulk FHIR query patterns. |
| TEFCA (National Exchange Framework) | `RELATED_TO` | Provider Enrollment Integration | Same driver relationship, for TEFCA-aligned provider onboarding automation. |
| TEFCA (National Exchange Framework) | `RELATED_TO` | IIS to EHR Query | Same driver relationship, for TEFCA-enabled IIS-initiated EHR queries. |
| Provider Identifier | `RELATED_TO` | Provider Enrollment Integration | A national provider-identification strategy is foundational to automating provider enrollment. |

### 4.12 Auxiliary public-health data in IIS

| From | Type | To | Rationale |
|---|---|---|---|
| Lead Test Query | `DEPENDS_ON` | Lead Test Reporting | You can only query lead data that jurisdictions have first captured via reporting. |
| TB Screening Data | `RELATED_TO` | Lead Test Reporting | Same pattern of "auxiliary, non-core public-health data occasionally stored in IIS," raising the same standardization questions. |
| TB Screening Data | `RELATED_TO` | Lead Test Query | Same rationale. |
| Social Determinants of Health (SDOH) | `RELATED_TO` | Homeless Data Integration (HMIS) | HMIS integration is a concrete instance of the general "what's SDOH's role in IIS" question. |
| Social Determinants of Health (SDOH) | `RELATED_TO` | Disability Data Collection | Disability status is commonly modeled as an SDOH-adjacent data element. |
| Race/Ethnicity | `RELATED_TO` | Sexual Orientation & Gender Identity (SOGI) | Both are demographic data elements undergoing definitional/collection transitions in HL7 v2 (note: SOGI is `ARCHIVED`). |
| Newborn Admission Notification Information (NANI) | `RELATED_TO` | Newborn Identity Handling | Both concern newborn-specific data workflows shortly after birth. |

### 4.13 School data exchange

| From | Type | To | Rationale |
|---|---|---|---|
| HL7 v2.5.1 School Roster Reporting | `OPERATIONALIZES` | School Data Exchange | Roster Reporting is the concrete HL7 v2.5.1 messaging implementation of the broader School Data Exchange concept. Adding this one link is enough — the UI shows it on both topics' pages automatically (forward label on Roster Reporting, "operationalized by" on School Data Exchange). |

### 4.14 Community, coordination & governance

| From | Type | To | Rationale |
|---|---|---|---|
| FHIR Dev Days | `RELATED_TO` | Immunization Focus Group (IFG) | Both are recurring forums where FHIR-based immunization interoperability work gets discussed/shaped. |
| Immunization Focus Group (IFG) | `RELATED_TO` | FHIR Open Tickets | IFG discussion of emerging FHIR standards and JIRA ticket coordination are closely linked activities in the same standards-development process. |
| Immunization Focus Group (IFG) | `RELATED_TO` | IIP Collaborative | Both are cross-organizational coordination bodies touching immunization interoperability (HL7 PHWG forum vs. AIRA/HIMSS/Drummond/CDC partnership). |

---

## 5. Summary counts

- Proposed relationships: **107** across 14 clusters.
- Types used: `RELATED_TO` (80), `DEPENDS_ON` (7), `FEEDS_INTO` (7), `OVERLAPS` (5), `DERIVED_FROM` (3), `SUPERSEDES` (2), `BLOCKER_FOR` (2), `OPERATIONALIZES` (1). No `DUPLICATE_OF` used — see §3.
- Topics not touched by any proposal: a handful of genuinely standalone/cross-cutting ones (e.g. *Machine Learning* beyond the one link above, *NITAG* beyond its one link, *Space Health*) where the description didn't give a concrete enough hook to another specific topic without guessing.

## 6. Status: accepted for v1, entered into `db/unapplied_updates.sql`

Reviewed 2026-08-24 — all 107 relationships above (including the resolved §3 items) were accepted as-is for a first pass and added as a single `INSERT ... SELECT` statement at the end of `db/unapplied_updates.sql`, keyed by `topic_code` (stable across environments) rather than raw IDs, attributed to `nbunker@immregistries.org` (`auth_user.user_id = 2`, the same account that created all seeded topics). A `WHERE NOT EXISTS` guard makes the block safe to apply more than once. Tested locally: applying it against the current local database inserted exactly 107 new rows (108 total, including the pre-existing QBP ↔ QBP-on-FHIR link), and re-applying it added zero duplicates. It will land permanently on the next local refresh, and ship to production with the next release per `docs/database-release-practice.md`.

This file can be deleted once that block in `unapplied_updates.sql` has been released and frozen into a `vX.Y_*.sql` file — it was a working proposal, not documentation to keep long-term.
