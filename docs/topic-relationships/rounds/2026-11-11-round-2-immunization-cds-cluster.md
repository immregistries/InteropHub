# Topic Relationship Review — Round 2 (Immunization CDS cluster)

**Round date:** 2026-11-11
**Scope:** Focused review of the immunization CDS cluster in Topic Space #1: *Immunization CDS (ImmDS + HALO)* (topic 1), *CDS Contextual Conditions* (7), *Immunization Decision Support (ImmDS)* (13), *HL7 v2 Contextual Conditions Exchange* (137), and its children (140, 141), with *CDS Hooks* (54) and *Estimated Vaccination Dates* (101) as context. Not a full watermark diff of the topic space.
**Status:** Accepted. See `docs/topic-relationships/decisions-log.md` and `db/unapplied_updates.sql`.
**Trigger:** Topic References for topics 1 and 7 were rebaselined in November 2026 (topic 1 v0.2.0, topic 7 v0.4.0), and the topic 1 overview was rewritten. Both now state their boundaries explicitly, which exposed gaps and one stale link in this cluster.

---

## 1. Relationship types

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

## 2. Method

Read the `description` of each topic in scope, the rewritten topic 1 overview (entered in production on 2026-11-11; the local copy still had the earlier text), and the boundary sections of the topic 1 and topic 7 Topic References. A link is proposed only where that text supports it.

## 3. Existing relationships reviewed

| From | Type | To | Decision | Why |
|---|---|---|---|---|
| Immunization CDS (ImmDS + HALO) | `SUPERSEDES` | Immunization Decision Support (ImmDS) | Keep | Topic 1 builds on and replaces the published ImmDS 1.0.0 guide. |
| CDS Contextual Conditions | `FEEDS_INTO` | Immunization CDS (ImmDS + HALO) | Keep | HALO is implemented as Contextual Conditions; topic 1 carries them in the FHIR request. |
| CDS Contextual Conditions | `FEEDS_INTO` | Immunization Decision Support (ImmDS) | **Remove** | ImmDS 1.0.0 is a published R4 guide that is not being revised; Contextual Conditions reach the FHIR interface through its successor, topic 1. The link sends readers to a frozen standard. |
| Clinical Decision Support (CDS Hooks) | `RELATED_TO` | Immunization CDS (ImmDS + HALO) | Keep | Topic 1 names CDS Hooks as a complementary layer. |
| Estimated Vaccination Dates | `RELATED_TO` | Immunization CDS (ImmDS + HALO) | Keep | Imprecise dates affect what an engine can evaluate. |
| HL7 v2 Contextual Conditions in Immunization Updates | `DERIVED_FROM` | HL7 v2 Contextual Conditions Exchange | Keep | Child topic. |
| HL7 v2 Contextual Conditions in Immunization Queries | `DERIVED_FROM` | HL7 v2 Contextual Conditions Exchange | Keep | Child topic. |

## 4. Proposed additions

| From | Type | To | Rationale |
|---|---|---|---|
| CDS Contextual Conditions | `FEEDS_INTO` | HL7 v2 Contextual Conditions Exchange | Topic 7 defines the concept and says it may be conveyed using FHIR or HL7 v2; topic 137 is the HL7 v2 representation. Same type as topic 7's existing link to the FHIR side (topic 1), so both transports show as "fed by" Contextual Conditions. |
| Immunization CDS (ImmDS + HALO) | `RELATED_TO` | HL7 v2 Contextual Conditions Exchange | Both return immunization evaluations and recommendations that account for supplied conditions: topic 1 through FHIR, topic 137 through QBP/RSP. Topic 1's overview points to topic 137 for conditions in HL7 v2 messages. Linked at the parent rather than topic 141, which is reached through it. |

Considered and not proposed:

- *Immunization CDS* ↔ *HL7 v2 Contextual Conditions in Immunization Queries* (141): the parent link above covers it.
- `OVERLAPS` for topic 1 ↔ 137: they are parallel transports for complementary audiences, not competing proposals.

## 5. Summary

- 2 additions, 1 removal, 6 existing links kept.

## 6. Outcome

Nathan asked for this round on 2026-11-11 and approved writing the changes to `db/unapplied_updates.sql`. Applied as proposed. Tested against the local database: the additions inserted 2 rows and the removal deleted 1 row; a re-run changed nothing.
