package org.airahub.interophub.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsInterestDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsCampaignTopic;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Admin import service for ES topics and optional campaign topic assignments.
 * Parses newline-separated JSON objects and upserts into es_topic /
 * es_topic_neighborhood and, when a campaign is chosen, es_campaign_topic.
 */
public class EsTopicImportService {

    private final EsTopicDao topicDao;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsInterestDao interestDao;
    private final EsCommentDao commentDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final UserDao userDao;
    private final TopicEditService topicEditService;

    public EsTopicImportService() {
        this.topicDao = new EsTopicDao();
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.interestDao = new EsInterestDao();
        this.commentDao = new EsCommentDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.userDao = new UserDao();
        this.topicEditService = new TopicEditService();
    }

    /**
     * Imports topics from newline-separated JSON objects and, optionally, assigns
     * them to a campaign.
     *
     * <p>
     * Campaign resolution order:
     * <ol>
     * <li>If {@code newCampaignCode} and {@code newCampaignName} are both
     * non-blank, find or
     * create the campaign by code (new campaign wins over selectedCampaignId).</li>
     * <li>Otherwise, use {@code selectedCampaignId}.</li>
     * <li>If neither is supplied, topics are imported without any campaign
     * assignment, and {@code set} / {@code displayOrder} are ignored.</li>
     * </ol>
     *
     * <p>
     * Optional fields follow partial-update rules: a missing key leaves the
     * existing value unchanged, while an explicit {@code null} or {@code ""}
     * clears it (priorities clear to 0). New topics get the defaults below.
     *
     * <p>
     * Expected JSON fields per line:
     *
     * <pre>
     * {
     *   "topicCode": "code",       // required
     *   "topicName": "name",       // required
     *   "topicSummary": "...",     // nullable; one sentence, max 300 characters
     *   "description": "...",      // nullable
     *   "searchKeywords": "...",   // nullable
     *   "topicEmoji": "...",       // nullable; max 64 characters
     *   "neighborhood": "...",     // nullable
     *   "priorityIis": 2,          // integer, defaults 0
     *   "priorityEhr": 1,          // integer, defaults 0
     *   "priorityCdc": 3,          // integer, defaults 0
     *   "stage": "...",            // nullable; must match a stage name defined for the target Topic Space
     *   "path": "...",             // nullable; must match a path name defined for the target Topic Space
     *   "status": "ACTIVE",        // ACTIVE, ARCHIVED, or RETIRED; new topics default to ACTIVE
     *   "policyStatus": "...",     // nullable
     *   "topicType": "...",        // nullable
     *   "confluenceUrl": "...",    // nullable
     *   "displayOrder": 10,        // integer, defaults 0; campaign only
     *   "set": 1                   // nullable integer for topic_set_no; campaign only
     * }
     * </pre>
     */
    public ImportResult importLines(String rawLines, Long selectedCampaignId,
            String newCampaignCode, String newCampaignName, Long adminUserId, int tablesPerSet,
            String topicSpaceCode) {

        EsTopicSpace targetTopicSpace = resolveTopicSpace(topicSpaceCode);
        User importingUser = userDao.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Importing user was not found."));
        EsCampaign campaign = resolveCampaign(
                selectedCampaignId, newCampaignCode, newCampaignName, adminUserId);

        boolean allowCampaignReset = campaign != null
                && (campaign.getStatus() == null || campaign.getStatus() == EsCampaign.CampaignStatus.DRAFT);

        if (allowCampaignReset) {
            Long campaignId = campaign.getEsCampaignId();
            // Draft campaigns are reset to a clean slate before rebuilding assignments.
            interestDao.deleteByCampaignId(campaignId);
            commentDao.deleteByCampaignId(campaignId);
            subscriptionDao.deleteBySourceCampaignId(campaignId);
            campaignTopicDao.deleteByCampaignId(campaignId);
        }

        String[] lines = rawLines.split("\r?\n");
        Tally tally = new Tally(campaign, allowCampaignReset);
        Set<String> seenTopicCodes = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            JSONObject json;
            try {
                json = new JSONObject(line);
            } catch (JSONException ex) {
                return ImportResult.failure(tally, i + 1,
                        "Malformed JSON on line " + (i + 1) + ": " + ex.getMessage());
            }

            try {
                validateLineTopicSpace(json, targetTopicSpace.getSpaceCode());

                // ── Upsert es_topic ──────────────────────────────────────────────────
                String topicCode = json.getString("topicCode");

                if (seenTopicCodes.contains(topicCode)) {
                    tally.duplicateTopicCodes++;
                }
                seenTopicCodes.add(topicCode);

                Optional<EsTopic> existingTopic = topicDao.findByTopicCodeAndSpaceId(
                        topicCode, targetTopicSpace.getEsTopicSpaceId());
                EsTopic topic = existingTopic.orElse(null);
                boolean isNewTopic = topic == null;
                if (isNewTopic) {
                    Optional<EsTopic> topicInAnotherSpace = topicDao.findByTopicCode(topicCode);
                    if (topicInAnotherSpace.isPresent()) {
                        throw new IllegalArgumentException(
                                "Topic code '" + topicCode
                                        + "' already exists in a different Topic Space. Import only updates topics in the selected Topic Space and will not move topics between spaces.");
                    }
                }

                Map<String, Object> fields = topicFields(json, isNewTopic);
                TopicEditService.TopicEditResult editResult = isNewTopic
                        ? topicEditService.create(importingUser, targetTopicSpace, fields, "IMPORT")
                        : topicEditService.update(importingUser, topic, topic.getUpdatedAt(), fields, "IMPORT");
                topic = editResult.topic();

                if (isNewTopic) {
                    tally.topicsInserted++;
                } else {
                    tally.topicsUpdated++;
                }

                if (!allowCampaignReset) {
                    // No campaign, or a non-DRAFT campaign: assignments are left untouched.
                    if (hasNonNull(json, "set") || hasNonNull(json, "displayOrder")) {
                        tally.campaignFieldsIgnored++;
                    }
                } else {
                    // ── Rebuild es_campaign_topic rows only while campaign is DRAFT ───────────
                    // Topics without a valid set are intentionally not assigned to any campaign
                    // table.
                    Integer topicSetNo = hasNonNull(json, "set") ? json.getInt("set") : null;
                    if (topicSetNo == null || topicSetNo < 1) {
                        tally.linesProcessed++;
                        continue;
                    }

                    Long campaignId = campaign.getEsCampaignId();
                    Long topicId = topic.getEsTopicId();
                    int displayOrder = json.optInt("displayOrder", 0);
                    // With tablesPerSet=N and set S: tables (S-1)*N+1 .. S*N.
                    int startTable = (topicSetNo - 1) * tablesPerSet + 1;
                    int endTable = topicSetNo * tablesPerSet;

                    List<Integer> expectedTableNos = IntStream.rangeClosed(startTable, endTable)
                            .boxed()
                            .collect(Collectors.toList());

                    // Defensive cleanup if duplicates/stale rows already exist during this import
                    // run.
                    campaignTopicDao.deleteByCampaignIdAndTopicIdAndTableNoNotIn(
                            campaignId, topicId, expectedTableNos);

                    for (int tableNo = startTable; tableNo <= endTable; tableNo++) {
                        Optional<EsCampaignTopic> existingCt = campaignTopicDao
                                .findByCampaignIdAndTopicIdAndTableNo(campaignId, topicId, tableNo);
                        EsCampaignTopic ct;
                        boolean isNewCt;
                        if (existingCt.isPresent()) {
                            ct = existingCt.get();
                            isNewCt = false;
                        } else {
                            ct = new EsCampaignTopic();
                            ct.setEsCampaignId(campaignId);
                            ct.setEsTopicId(topicId);
                            ct.setTableNo(tableNo);
                            isNewCt = true;
                        }

                        ct.setDisplayOrder(displayOrder);
                        ct.setTopicSetNo(topicSetNo);

                        campaignTopicDao.saveOrUpdate(ct);

                        if (isNewCt) {
                            tally.campaignTopicsInserted++;
                        } else {
                            tally.campaignTopicsUpdated++;
                        }
                    }
                }

            } catch (JSONException ex) {
                return ImportResult.failure(tally, i + 1,
                        "Invalid field on line " + (i + 1) + ": " + ex.getMessage());
            } catch (IllegalArgumentException ex) {
                return ImportResult.failure(tally, i + 1, ex.getMessage());
            }

            tally.linesProcessed++;
        }

        return ImportResult.success(tally);
    }

    /** Returns the chosen campaign, or {@code null} when none was requested. */
    private EsCampaign resolveCampaign(Long selectedCampaignId, String newCampaignCode,
            String newCampaignName, Long adminUserId) {
        if (newCampaignCode != null && newCampaignName != null) {
            Optional<EsCampaign> existing = campaignDao.findByCampaignCode(newCampaignCode);
            if (existing.isPresent()) {
                return existing.get();
            }
            EsCampaign campaign = new EsCampaign();
            campaign.setCampaignCode(newCampaignCode);
            campaign.setCampaignName(newCampaignName);
            campaign.setCreatedByUserId(adminUserId);
            return campaignDao.saveOrUpdate(campaign);
        }
        if (newCampaignCode != null || newCampaignName != null) {
            throw new IllegalArgumentException(
                    "To create a new campaign, enter both a campaign code and a campaign name.");
        }
        if (selectedCampaignId != null) {
            return campaignDao.findById(selectedCampaignId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected campaign not found."));
        }
        return null;
    }

    private Map<String, Object> topicFields(JSONObject json, boolean creating) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (creating) {
            fields.put("topicCode", json.getString("topicCode"));
        }
        fields.put("topicName", json.getString("topicName"));
        for (String field : new String[] {
                "topicSummary", "description", "searchKeywords", "topicEmoji", "neighborhood",
                "stage", "path", "status", "policyStatus", "topicType", "confluenceUrl"
        }) {
            if (!creating && !json.has(field)) {
                continue;
            }
            fields.put(field, readNullableTrimmedString(json, field));
        }
        for (String field : new String[] { "priorityIis", "priorityEhr", "priorityCdc" }) {
            if (creating || json.has(field)) {
                fields.put(field, readPriority(json, field));
            }
        }
        return fields;
    }

    private int readPriority(JSONObject json, String fieldName) {
        return hasNonNull(json, fieldName) ? json.getInt(fieldName) : 0;
    }

    private boolean hasNonNull(JSONObject json, String fieldName) {
        return json.has(fieldName) && !json.isNull(fieldName);
    }

    private String readNullableTrimmedString(JSONObject json, String fieldName) {
        if (json.isNull(fieldName)) {
            return null;
        }
        String raw = json.optString(fieldName, null);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private EsTopicSpace resolveTopicSpace(String topicSpaceCode) {
        String normalizedCode = normalizeNeighborhoodToken(topicSpaceCode);
        if (normalizedCode == null) {
            throw new IllegalArgumentException("Topic Space code is required for imports.");
        }
        EsTopicSpace topicSpace = topicSpaceDao.findBySpaceCode(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Topic Space code: " + normalizedCode));
        if (!Boolean.TRUE.equals(topicSpace.getIsActive())) {
            throw new IllegalArgumentException(
                    "Only active Topic Spaces may receive imported topics. Topic Space is inactive: "
                            + normalizedCode);
        }
        return topicSpace;
    }

    private void validateLineTopicSpace(JSONObject json, String expectedTopicSpaceCode) {
        for (String field : new String[] { "topicSpaceCode", "spaceCode", "topicSpace" }) {
            if (!json.has(field) || json.isNull(field)) {
                continue;
            }
            String value = normalizeNeighborhoodToken(json.optString(field, null));
            if (value == null) {
                continue;
            }
            if (value.contains(",")) {
                throw new IllegalArgumentException(
                        "Import line contains multiple Topic Spaces in field '" + field
                                + "'. Exactly one Topic Space is allowed.");
            }
            if (!expectedTopicSpaceCode.equalsIgnoreCase(value)) {
                throw new IllegalArgumentException(
                        "Import line Topic Space '" + value + "' does not match selected Topic Space '"
                                + expectedTopicSpaceCode + "'.");
            }
        }
    }

    private String normalizeNeighborhoodToken(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ── Result DTO
    // ────────────────────────────────────────────────────────────────────────────────

    /** Running counts for one import, captured into an {@link ImportResult}. */
    private static class Tally {
        private final String campaignCode;
        private final String campaignName;
        private final boolean campaignAssignmentsRebuilt;
        private int linesProcessed;
        private int topicsInserted;
        private int topicsUpdated;
        private int campaignTopicsInserted;
        private int campaignTopicsUpdated;
        private int duplicateTopicCodes;
        private int campaignFieldsIgnored;

        private Tally(EsCampaign campaign, boolean campaignAssignmentsRebuilt) {
            this.campaignCode = campaign == null ? null : campaign.getCampaignCode();
            this.campaignName = campaign == null ? null : campaign.getCampaignName();
            this.campaignAssignmentsRebuilt = campaignAssignmentsRebuilt;
        }
    }

    public static class ImportResult {

        private final int linesProcessed;
        private final int topicsInserted;
        private final int topicsUpdated;
        private final int campaignTopicsInserted;
        private final int campaignTopicsUpdated;
        private final int duplicateTopicCodes;
        private final int campaignFieldsIgnored;
        private final boolean campaignAssignmentsRebuilt;
        private final String campaignCode;
        private final String campaignName;
        private final String errorMessage;
        private final int errorLine;

        private ImportResult(Tally tally, String errorMessage, int errorLine) {
            this.linesProcessed = tally.linesProcessed;
            this.topicsInserted = tally.topicsInserted;
            this.topicsUpdated = tally.topicsUpdated;
            this.campaignTopicsInserted = tally.campaignTopicsInserted;
            this.campaignTopicsUpdated = tally.campaignTopicsUpdated;
            this.duplicateTopicCodes = tally.duplicateTopicCodes;
            this.campaignFieldsIgnored = tally.campaignFieldsIgnored;
            this.campaignAssignmentsRebuilt = tally.campaignAssignmentsRebuilt;
            this.campaignCode = tally.campaignCode;
            this.campaignName = tally.campaignName;
            this.errorMessage = errorMessage;
            this.errorLine = errorLine;
        }

        private static ImportResult success(Tally tally) {
            return new ImportResult(tally, null, 0);
        }

        private static ImportResult failure(Tally tally, int errorLine, String errorMessage) {
            return new ImportResult(tally, errorMessage, errorLine);
        }

        public int getLinesProcessed() {
            return linesProcessed;
        }

        public int getTopicsInserted() {
            return topicsInserted;
        }

        public int getTopicsUpdated() {
            return topicsUpdated;
        }

        public int getCampaignTopicsInserted() {
            return campaignTopicsInserted;
        }

        public int getCampaignTopicsUpdated() {
            return campaignTopicsUpdated;
        }

        public int getDuplicateTopicCodes() {
            return duplicateTopicCodes;
        }

        /** Lines that supplied {@code set} or {@code displayOrder} that were not applied. */
        public int getCampaignFieldsIgnored() {
            return campaignFieldsIgnored;
        }

        /** True when a DRAFT campaign was reset and its topic assignments rebuilt. */
        public boolean isCampaignAssignmentsRebuilt() {
            return campaignAssignmentsRebuilt;
        }

        public boolean hasCampaign() {
            return campaignCode != null;
        }

        public String getCampaignCode() {
            return campaignCode;
        }

        public String getCampaignName() {
            return campaignName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getErrorLine() {
            return errorLine;
        }
    }
}
