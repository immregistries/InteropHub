# Topic Spaces Impact Assessment (Discovery and Planning Only)

## Status

This document remains useful as discovery research, but its architecture recommendations are superseded by:

- `docs/interop-topic-spaces-upgrade-context.md` for approved Topic Spaces architecture
- `docs/interop-topic-spaces-implementation-stages.md` for implementation sequencing

Where this assessment recommends a many-to-many Topic Space model, a separate Topic-to-Space mapping table, or leaves core Topic Space behavior as an open architecture decision, treat those parts as historical analysis rather than the approved design.

## 1) Executive Summary

This assessment evaluates the impact of introducing **Topic Spaces** into the current InteropHub Emerging Standards (ES) implementation. It is based on the repository’s current schema, migrations, Hibernate models, DAOs, services, servlet routes, and UI rendering paths.

Key conclusions:

- The current system already has one partial categorization construct (`es_neighborhood` + `es_topic_neighborhood`) and one legacy denormalized field (`es_topic.neighborhood`).
- Topic exposure logic is spread across multiple DAOs/servlets and is not centrally authorization-filtered; introducing Topic Spaces requires explicit filter insertion at each topic query boundary.
- Historical note: this assessment originally recommended a **new catalog + mapping model** for Topic Spaces, but the approved architecture now stores one Topic Space directly on each topic.
- Existing external sync behavior (Dandelion `projectTags`) is currently derived from neighborhood names; product decisions are needed on whether Topic Spaces should replace, augment, or remain independent from those tags.

This document does **not** implement Topic Spaces. It provides impact and rollout planning only.

## 2) Current ES Architecture Relevant to Topic Categorization

### 2.1 Runtime stack

- Java servlet web app (`jakarta.servlet`) with route registration in `src/main/webapp/WEB-INF/web.xml`.
- Hibernate entity mapping via `src/main/resources/hibernate.cfg.xml`.
- MySQL persistence with migration SQL in `db/v0.*.sql` and a reference schema in `db/schema.sql`.

### 2.2 Topic domain data model today

Primary topic entity:

- `src/main/java/org/airahub/interophub/model/EsTopic.java`
- `db/schema.sql` (`es_topic`)

Classification-like topic metadata currently includes:

- `es_topic.neighborhood` (string, denormalized)
- `es_topic.stage`
- `es_topic.policy_status` (added in `db/v0.8_es_topic_metadata.sql`)
- `es_topic.topic_type` (added in `db/v0.8_es_topic_metadata.sql`)
- `es_topic.confluence_url` (added in `db/v0.10_es_topic_confluence_url.sql`)

Normalized neighborhood structures:

- Catalog: `es_neighborhood` (`db/v0.11_es_neighborhood.sql`, model `EsNeighborhood`)
- Mapping: `es_topic_neighborhood` (`db/v0.24_es_topic_neighborhood_reset.sql`, model `EsTopicNeighborhood`)
- DAO support: `EsTopicNeighborhoodDao`

Relationship/curation overlays (important because they create additional topic-visibility paths):

- `es_topic_relationship` (`db/v0.19_es_topic_relationship.sql`, model `EsTopicRelationship`)
- `es_topic_curation` (`db/v0.19_es_topic_relationship.sql`, model `EsTopicCuration`)

### 2.3 Query and rendering flow

Core topic reads:

- `EsTopicDao.findAllActiveBrowseRowsOrdered()`
- `EsTopicDao.findActiveById()`
- `EsTopicDao.findAllActiveForPublicPage()`

Primary public routes:

- `/es/topics` via `EsTopicsServlet`
- `/es/topic/*` via `EsTopicDetailServlet`

Mutating topic-adjacent routes:

- `/es/topics/follow-toggle` (`EsTopicFollowToggleServlet`)
- `/es/topics/meeting-toggle` (`EsTopicMeetingToggleServlet`)
- `/es/topics/relationship` (`EsTopicRelationshipServlet`)
- `/es/topics/curation` (`EsTopicCurationServlet`)

Admin maintenance:

- `/admin/es/topics` (`AdminEsTopicServlet`)
- Topic import via `EsTopicImportService` and `/admin/es-topic-import`

## 3) What “Topic Spaces” Must Coexist With

Topic Spaces will enter a model that already includes:

- Campaign assignment (`es_campaign_topic`)
- Durable subscriptions/champion/support roles (`es_subscription`, including `SUPPORT` added by `db/v0.27_es_subscription_support.sql`)
- Optional topic meeting series and membership (`es_topic_meeting`, `es_topic_meeting_member`)
- Relationship graphs and curated lists
- Dandelion external sync, where tags are currently neighborhood-driven (`DandelionSyncService.resolveProjectTags(...)` path)

This means Topic Spaces are not just a label feature. They will affect:

- Topic list filtering/navigation
- Access boundaries (if spaces become security-scoped)
- Admin topic lifecycle and import flows
- External sync payload semantics

## 4) Historical MVP Topic Spaces Model (Superseded)

### 4.1 MVP entity structure

This section records the earlier discovery-phase recommendation that has now been superseded by the approved architecture in `docs/interop-topic-spaces-upgrade-context.md`.

Earlier normalized pattern (parallel to neighborhood design):

- `es_topic_space`
  - `es_topic_space_id` (PK)
  - `space_code` (unique, stable API/key identifier)
  - `space_name` (display label)
  - `description` (optional)
  - `display_order`
  - `is_active`
  - `created_by_user_id`
  - `created_at`, `updated_at`

- `es_topic_space_topic`
  - `es_topic_space_topic_id` (PK)
  - `es_topic_space_id` (FK)
  - `es_topic_id` (FK)
  - `created_at`
  - unique key on (`es_topic_space_id`, `es_topic_id`)

This mirrors existing codebase conventions used for neighborhoods and allows many-to-many assignment without denormalized drift.

Approved architecture instead requires:

- one Topic Space per topic
- Topic Space stored directly on `es_topic`
- no `es_topic_space_topic` many-to-many table

### 4.2 Why this model is safest in this codebase

- Existing topic categorization has already moved from string-only toward normalized mapping (`es_topic_neighborhood`).
- `EsTopicNeighborhoodDao` gives a direct reference implementation for DAO patterns, bulk lookups, replacement operations, and ordering.
- Admin and import flows already understand replacing mapping rows per topic; same approach can be reused.

## 5) Database Impact Assessment

### 5.1 Schema-level changes likely required

1. Create Topic Space catalog table (`es_topic_space`).
2. Historical recommendation only: create Topic↔Space mapping table (`es_topic_space_topic`). This is superseded and should not be implemented.
3. Add indexes for high-frequency filters:
  - historical mapping by `es_topic_id`
  - historical mapping by `es_topic_space_id`
4. Decide whether a temporary denormalized compatibility column is needed on `es_topic` (not recommended unless migration constraints require it).

### 5.2 Migration sequencing

Given current repo convention (`db/v0.*.sql` incremental migrations):

- Introduce tables first.
- Backfill from a deterministic source mapping.
- Add application read paths behind optional filters.
- Only then retire/ignore legacy classification dependence where applicable.

### 5.3 Drift caution already present in repo

`db/schema.sql` includes `es_topic` and `es_subscription` but does not fully represent all newer ES structures added in later migrations (e.g., neighborhood mapping/relationship/curation lineage). Topic Spaces should therefore rely on **new migration scripts + model mappings**, not schema.sql assumptions alone.

## 6) ORM and Model Layer Impact

### 6.1 New models required

- `EsTopicSpace`
- Historical-only model: `EsTopicSpaceTopic`

Both should follow existing entity style:

- JPA annotations used in models like `EsNeighborhood`, `EsTopicNeighborhood`
- `@PrePersist`/`@PreUpdate` patterns for timestamps where applicable

### 6.2 Existing model touchpoints

- Superseded recommendation: `EsTopic` may need no direct field addition for MVP if space membership is fully normalized.
- Approved architecture instead requires a direct Topic Space field on `EsTopic`.

## 7) DAO and Query Impact

### 7.1 High-impact query touchpoints

- `EsTopicDao.findAllActiveBrowseRowsOrdered()`
- `EsTopicDao.findActiveById()`
- `EsTopicDao.findAllActiveForPublicPage()`
- `EsCampaignTopicDao` browse queries that join `EsTopic`
- `EsTopicNeighborhoodDao`-style bulk lookup patterns should be duplicated for spaces

### 7.2 Required query behavior decisions

- Single-space filter only vs multi-space filter (OR semantics) vs intersection semantics.
- Default behavior when a topic has no space assignment.
- Ordering precedence if both neighborhood and space are shown in navigation.

### 7.3 Recommended query strategy

- Keep existing topic row DTO shape stable for MVP.
- Add side-channel lookup map (`topicId -> space names`) first, mirroring neighborhood name lookup.
- Add optional query-level filtering once product defaults are finalized.

## 8) Service and Background Processing Impact

### 8.1 Import service impact

`EsTopicImportService` currently resolves neighborhoods and replaces `es_topic_neighborhood` rows. Topic Spaces can be introduced with the same pattern:

- parse `space` or `spaces` token(s)
- resolve active space IDs
- replace mapping rows for that topic

Important: import currently also writes `topic.setNeighborhood(...)` string for backward compatibility. A product decision is needed on whether Topic Spaces should maintain a similar denormalized compatibility string (generally discouraged).

### 8.2 Dandelion sync impact

`DandelionSyncService` builds project tags from neighborhood names today. Topic Spaces introduces a semantic overlap:

- Option A: keep tags as neighborhoods only (no external behavior change)
- Option B: tags become spaces only
- Option C: tags include both neighborhoods + spaces

This is a key integration decision because it changes external project taxonomy visibility.

## 9) Servlet, Route, and UI Impact

### 9.1 Public listing/detail

- `EsTopicsServlet` sidebar/navigation currently supports overview, all, neighborhood, stage, review, my-topics, meetings.
- Topic Spaces likely adds another navigation group and filter param (e.g., `space` code).
- `EsTopicDetailServlet` likely needs display of assigned spaces, and potentially curator/relationship displays filtered by same visibility rules.

### 9.2 Admin management

- `AdminEsTopicServlet` currently edits neighborhoods via checkbox set and replacement DAO call.
- Topic Spaces needs analogous admin controls and save behavior.

### 9.3 Route registry

`web.xml` is explicit/manual. Any new Topic Space endpoints must be declared there; no global auto-discovery exists.

## 10) Authorization and Data-Visibility Impact

### 10.1 Current auth pattern risk profile

Authorization is servlet-local and role checks are localized, for example:

- `EsTopicRelationshipServlet`: admin or champion/support of `from_topic_id`
- `EsTopicCurationServlet`: admin or champion/support of `curator_topic_id`
- Toggle endpoints require authentication but no additional central policy enforcement

There is no global filter enforcing topic-level visibility constraints across all topic queries.

### 10.2 Topic Space authorization matrix (recommended future behavior)

If Topic Spaces are purely organizational labels:

- Public routes may continue showing all active topics unless filtered by user choice.

If Topic Spaces are access scopes (stronger model):

- Every query returning topics must apply allowed-space constraints.

Minimum route/query matrix to audit if spaces are access scopes:

- `/es/topics` (`EsTopicsServlet`) and all internal query branches
- `/es/topic/*` (`EsTopicDetailServlet`)
- `/es/meetings`, `/es/agenda` pathways that surface topic-linked rows
- `EsCampaignTopicDao.findAllActiveMeetingRowsOrdered()` consumers
- `EsTopicDao.findAllActiveBrowseRowsOrdered()` consumers
- `EsTopicDao.findActiveById()` consumers
- Admin views that should bypass scope restrictions for admins only

### 10.3 Highest-risk authorization issue

If Topic Spaces are intended to restrict visibility, the highest risk is **partial enforcement**: filtering in UI view logic but not in all DAO/query entry points. This would allow exposure via alternate routes and derived lists.

## 11) Data Migration and Backfill Strategy

### 11.1 Recommended migration phases

1. Add Topic Space catalog table and direct Topic Space foreign keys as defined in `docs/interop-topic-spaces-upgrade-context.md`.
2. Seed baseline space definitions.
3. Backfill mappings using deterministic rules.
4. Ship read-only display support.
5. Add filter controls.
6. Add optional auth scoping (if required).
7. Remove temporary compatibility behavior.

### 11.2 Backfill source rules

- Preferred: explicit curated mapping file in `db/` (same approach as neighborhood snapshot migration style).
- Acceptable fallback: deterministic mapping from existing `neighborhood`/`stage` only if product confirms this mapping is semantically valid.

### 11.3 “Country Interview” note

A direct repository artifact for “Country Interview” was not found during code/docs/sql text search. Any mapping rule referencing that concept must be treated as a product-level requirement to be confirmed before migration SQL is finalized.

### 11.4 Safe default recommendation

For topics with no deterministic mapping at migration time:

- assign to a default space (example: “Emerging Standards”) and log/report unresolved mappings for manual curation.

## 12) External Integrations and Reporting Impact

### 12.1 Dandelion sync

Potential payload impact if spaces are integrated into project tags.

### 12.2 Admin reporting surfaces

Any reports or exports grouping by neighborhood may need parallel Topic Space grouping, especially in:

- topic list exports
- campaign/topic browse admin pages
- future analytics around subscriptions and meeting participation

### 12.3 Documentation impact

Likely docs needing updates after implementation:

- `docs/es-topic-import-format.md`
- `docs/interophub-project-tags-upsert.md`

## 13) Regression Risk and Test Plan

### 13.1 Current test baseline

No automated tests were found under `src/test`. Regression confidence will depend on manual test plans unless a test harness is added.

### 13.2 Highest-regression surfaces

- Topic browse ordering/filtering behavior
- Follow/unfollow and meeting request workflows after applying any space filters
- Relationship/curation authorization checks with scoped topic sets
- Dandelion sync payload correctness

### 13.3 Minimum manual validation matrix (pre-implementation planning)

1. Public topic browse without filter still returns expected active topics.
2. Topic detail deep links (`/es/topic/{id}`) behave consistently with browse visibility rules.
3. Follow/unfollow and meeting toggle continue to work for allowed topics.
4. Champion/support role checks remain enforced on relationship/curation mutations.
5. Admin topic edit/import flows can assign and persist spaces.
6. Dandelion sync queue processing remains successful with expected tags.

## 14) Open Product/Architecture Decisions

1. Topic Spaces are security/visibility scopes as defined in `docs/interop-topic-spaces-upgrade-context.md`.
2. Topics belong to exactly one Topic Space; multi-space assignment is not supported.
3. Should spaces replace neighborhood in UI navigation, or coexist?
4. Should Dandelion `projectTags` be neighborhood-based, space-based, or both?
5. What is authoritative migration mapping source for existing topics?

Items 3 through 5 remain implementation and product considerations. Items 1 and 2 are settled by the authoritative context document.

## 15) Appendix: Repository Files and Components Reviewed

### 15.1 Database and migrations

- `db/schema.sql`
- `db/v0.3_es_layer.sql`
- `db/v0.8_es_topic_metadata.sql`
- `db/v0.10_es_topic_confluence_url.sql`
- `db/v0.11_es_neighborhood.sql`
- `db/v0.19_es_topic_relationship.sql`
- `db/v0.24_es_topic_neighborhood_reset.sql`
- `db/v0.27_es_subscription_support.sql`

### 15.2 Hibernate and models

- `src/main/resources/hibernate.cfg.xml`
- `src/main/java/org/airahub/interophub/model/EsTopic.java`
- `src/main/java/org/airahub/interophub/model/EsNeighborhood.java`
- `src/main/java/org/airahub/interophub/model/EsTopicNeighborhood.java`
- `src/main/java/org/airahub/interophub/model/EsSubscription.java`
- `src/main/java/org/airahub/interophub/model/EsTopicRelationship.java`
- `src/main/java/org/airahub/interophub/model/EsTopicCuration.java`

### 15.3 DAOs

- `src/main/java/org/airahub/interophub/dao/EsTopicDao.java`
- `src/main/java/org/airahub/interophub/dao/EsTopicNeighborhoodDao.java`
- `src/main/java/org/airahub/interophub/dao/EsCampaignTopicDao.java`
- `src/main/java/org/airahub/interophub/dao/EsSubscriptionDao.java`
- `src/main/java/org/airahub/interophub/dao/EsTopicMeetingDao.java`
- `src/main/java/org/airahub/interophub/dao/EsTopicRelationshipDao.java`
- `src/main/java/org/airahub/interophub/dao/EsTopicCurationDao.java`

### 15.4 Services

- `src/main/java/org/airahub/interophub/service/EsTopicImportService.java`
- `src/main/java/org/airahub/interophub/service/DandelionSyncService.java`
- `src/main/java/org/airahub/interophub/service/EsInterestService.java`
- `src/main/java/org/airahub/interophub/service/AuthFlowService.java`

### 15.5 Servlets and route registry

- `src/main/java/org/airahub/interophub/servlet/EsTopicsServlet.java`
- `src/main/java/org/airahub/interophub/servlet/EsTopicDetailServlet.java`
- `src/main/java/org/airahub/interophub/servlet/EsTopicDetailRenderer.java`
- `src/main/java/org/airahub/interophub/servlet/EsTopicFollowToggleServlet.java`
- `src/main/java/org/airahub/interophub/servlet/EsTopicMeetingToggleServlet.java`
- `src/main/java/org/airahub/interophub/servlet/EsTopicRelationshipServlet.java`
- `src/main/java/org/airahub/interophub/servlet/EsTopicCurationServlet.java`
- `src/main/java/org/airahub/interophub/servlet/AdminEsTopicServlet.java`
- `src/main/java/org/airahub/interophub/servlet/EsUnsubscribeServlet.java`
- `src/main/webapp/WEB-INF/web.xml`

---

## Concise Summary (Requested)

- **Most important schema change:** introduce the Topic Space catalog and direct Topic Space ownership on topics, neighborhoods, and meetings as defined in `docs/interop-topic-spaces-upgrade-context.md`.
- **Highest-risk authorization issue:** inconsistent enforcement if space visibility rules are applied in UI but omitted from all topic query entry points.
- **Largest likely code touchpoint:** `EsTopicsServlet` + `EsTopicDao` query/filter paths (plus their downstream consumers).
- **Main settled architecture point:** Topic Spaces are visibility scopes, and topics belong to exactly one Topic Space.
- **Generated markdown file path:** `docs/topic-spaces-impact-assessment.md`
